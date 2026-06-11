package me.auto.managers;

import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class MineManager {

    private final AutoPlugin plugin;
    private final Map<String, MineData> mines = new HashMap<>();
    private final Map<UUID, String> playerMine = new HashMap<>();
    private final File dataFile;

    public MineManager(AutoPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "mines.yml");
        loadAll();
        startResetTimer();
        startLeaveChecker();
    }

    // ── Видати кирку при вході ────────────────────────────────────────

    public static ItemStack createPickaxe() {
        ItemStack pick = new ItemStack(Material.STONE_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "⛏ Шахтарська кирка");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Тільки для авто-шахти");
        lore.add(ChatColor.DARK_GRAY + "Звичайні блоки не ламає");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.EFFICIENCY, 2, true);
        pick.setItemMeta(meta);
        return pick;
    }

    public static boolean isMinePickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.STONE_PICKAXE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        return item.getItemMeta().getDisplayName().contains("Шахтарська кирка");
    }

    private void givePickaxe(Player p) {
        // Даємо тільки якщо немає в руці
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isMinePickaxe(hand)) return;
        ItemStack pick = createPickaxe();
        if (hand.getType() == Material.AIR) {
            p.getInventory().setItemInMainHand(pick);
        } else {
            // Знаходимо вільний слот або кладемо в інвентар
            p.getInventory().addItem(pick);
        }
        p.updateInventory();
        ColorUtils.msg(p, "&7Отримано &e⛏ Шахтарська кирка!");
    }

    // ── Create / Delete ───────────────────────────────────────────────

    /** Створює шахту за виділенням гравця (ПКМ/ЛКМ топором) */
    public void createMineFromSelection(Player p, String name) {
        if (!plugin.getSelectionManager().hasSelection(p)) {
            ColorUtils.msg(p, "&cСпочатку виділи область топором виділення!");
            ColorUtils.msg(p, "&7ЛКМ = точка 1, ПКМ = точка 2");
            return;
        }
        Location min = plugin.getSelectionManager().getMin(p);
        Location max = plugin.getSelectionManager().getMax(p);
        int sx = plugin.getSelectionManager().getSizeX(p);
        int sz = plugin.getSelectionManager().getSizeZ(p);
        int depth = (int)(max.getY() - min.getY()) + 1;
        if (depth < 1) depth = plugin.getConfig().getInt("mine.depth", 10);

        String id = UUID.randomUUID().toString().substring(0, 8);
        Location center = plugin.getSelectionManager().getCenter(p);
        center.setY(max.getY());

        MineData mine = new MineData(id, center, p.getUniqueId(), name);
        mine.setSizeX(sx);
        mine.setSizeZ(sz);
        mine.setDepth(depth);
        mines.put(id, mine);
        buildMine(mine);
        plugin.getSelectionManager().clear(p);
        saveAll();
        ColorUtils.msg(p, "&aШахту &e" + name + " &aстворено! ID: &f" + id);
        ColorUtils.msg(p, "&7Розмір: &e" + sx + "x" + depth + "x" + sz);
    }

    public boolean deleteMine(String id) {
        MineData mine = mines.remove(id);
        if (mine == null) return false;
        clearMine(mine);
        saveAll();
        return true;
    }

    // ── Build / Reset ─────────────────────────────────────────────────

    public void buildMine(MineData mine) {
        World w = Bukkit.getWorld(mine.getWorldName());
        if (w == null) return;

        int sizeX = mine.getSizeX();
        int sizeZ = mine.getSizeZ();
        int depth = mine.getDepth();
        Location c = mine.getCenter();
        int ox = c.getBlockX() - sizeX / 2;
        int oy = c.getBlockY();
        int oz = c.getBlockZ() - sizeZ / 2;

        Random rng = new Random();

        // Заповнюємо руди та камінь
        for (int x = ox; x < ox + sizeX; x++) {
            for (int z = oz; z < oz + sizeZ; z++) {
                // Підлога — ЗВИЧАЙНИЙ КАМІНЬ
                w.getBlockAt(x, oy - depth - 1, z).setType(Material.BEDROCK);

                for (int y = oy - depth; y < oy; y++) {
                    Material mat = rollOre(rng);
                    w.getBlockAt(x, y, z).setType(mat);
                }
                // Стеля — AIR, щоб можна було зайти
                w.getBlockAt(x, oy, z).setType(Material.AIR);
            }
        }

        // Стіни — ЗВИЧАЙНИЙ КАМІНЬ (не stone_bricks!)
        buildWalls(w, ox, oy, oz, sizeX, sizeZ, depth);
        mine.setLastReset(System.currentTimeMillis());
    }

    private void buildWalls(World w, int ox, int oy, int oz, int sx, int sz, int depth) {
        // Стіни — звичайний камінь
        for (int y = oy - depth - 1; y <= oy; y++) {
            for (int x = ox - 1; x <= ox + sx; x++) {
                setIfNotAir(w, x, y, oz - 1, Material.STONE);
                setIfNotAir(w, x, y, oz + sz, Material.STONE);
            }
            for (int z = oz - 1; z <= oz + sz; z++) {
                setIfNotAir(w, ox - 1, y, z, Material.STONE);
                setIfNotAir(w, ox + sx, y, z, Material.STONE);
            }
        }
        // Ліхтарі в кутах на рівні входу
        w.getBlockAt(ox,      oy, oz).setType(Material.LANTERN);
        w.getBlockAt(ox+sx-1, oy, oz).setType(Material.LANTERN);
        w.getBlockAt(ox,      oy, oz+sz-1).setType(Material.LANTERN);
        w.getBlockAt(ox+sx-1, oy, oz+sz-1).setType(Material.LANTERN);
    }

    private void setIfNotAir(World w, int x, int y, int z, Material mat) {
        Block b = w.getBlockAt(x, y, z);
        if (b.getType() != Material.AIR) b.setType(mat);
        else b.setType(mat);
    }

    private void clearMine(MineData mine) {
        World w = Bukkit.getWorld(mine.getWorldName());
        if (w == null) return;
        int sizeX = mine.getSizeX();
        int sizeZ = mine.getSizeZ();
        int depth = mine.getDepth();
        Location c = mine.getCenter();
        for (int x = c.getBlockX() - sizeX/2 - 2; x <= c.getBlockX() + sizeX/2 + 2; x++)
            for (int z = c.getBlockZ() - sizeZ/2 - 2; z <= c.getBlockZ() + sizeZ/2 + 2; z++)
                for (int y = c.getBlockY() - depth - 2; y <= c.getBlockY() + 1; y++)
                    w.getBlockAt(x, y, z).setType(Material.AIR);
    }

    /**
     * Склад шахти: БАГАТО каменю (~50%), плюс руди.
     * Тільки дозволені матеріали: камінь, алмаз, лазурит, золото, залізо, смарагд, редстоун, вугілля
     */
    private Material rollOre(Random rng) {
        double roll = rng.nextDouble();
        // Сума = 1.0
        if (roll < 0.50) return Material.STONE;
        if (roll < 0.65) return Material.COAL_ORE;
        if (roll < 0.77) return Material.IRON_ORE;
        if (roll < 0.85) return Material.LAPIS_ORE;
        if (roll < 0.92) return Material.REDSTONE_ORE;
        if (roll < 0.96) return Material.GOLD_ORE;
        if (roll < 0.98) return Material.EMERALD_ORE;
        return Material.DIAMOND_ORE;      // 2%
    }

    // ── Player tracking ───────────────────────────────────────────────

    public void enterMine(Player p, MineData mine) {
        playerMine.put(p.getUniqueId(), mine.getId());
        mine.setActivePlayer(p.getUniqueId());
        ColorUtils.msg(p, "&aВи у шахті &e" + mine.getName() + " &7— копайте та заробляйте!");
        givePickaxe(p);
        plugin.getScoreboardManager().showMineBoard(p);
    }

    public void leaveMine(Player p) {
        String id = playerMine.remove(p.getUniqueId());
        if (id == null) return;
        MineData mine = mines.get(id);
        if (mine != null && p.getUniqueId().equals(mine.getActivePlayer()))
            mine.setActivePlayer(null);
        // Якщо не продав NPC — губить накопичене
        plugin.getNpcManager().losePending(p.getUniqueId(), "mine");
        plugin.getScoreboardManager().showLobbyBoard(p);
    }

    public MineData getMineAt(Location loc) {
        for (MineData mine : mines.values())
            if (isInsideMine(loc, mine)) return mine;
        return null;
    }

    public MineData getPlayerMine(Player p) {
        String id = playerMine.get(p.getUniqueId());
        return id != null ? mines.get(id) : null;
    }

    private boolean isInsideMine(Location loc, MineData mine) {
        if (!loc.getWorld().getName().equals(mine.getWorldName())) return false;
        Location c = mine.getCenter();
        int sx = mine.getSizeX(); int sz = mine.getSizeZ(); int d = mine.getDepth();
        int dx = Math.abs(loc.getBlockX() - c.getBlockX());
        int dz = Math.abs(loc.getBlockZ() - c.getBlockZ());
        int dy = c.getBlockY() - loc.getBlockY();
        return dx <= sx/2+1 && dz <= sz/2+1 && dy >= -1 && dy <= d+2;
    }

    // ── Timers ────────────────────────────────────────────────────────

    private void startResetTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                long resetMs = plugin.getConfig().getLong("mine.reset-time", 300) * 1000L;
                for (MineData mine : mines.values()) {
                    if (System.currentTimeMillis() - mine.getLastReset() >= resetMs) {
                        buildMine(mine);
                        if (mine.hasActivePlayer()) {
                            Player p = Bukkit.getPlayer(mine.getActivePlayer());
                            if (p != null) ColorUtils.msg(p, "&aШахта &e" + mine.getName() + " &aоновилась!");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startLeaveChecker() {
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID uid : new HashSet<>(playerMine.keySet())) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p == null) { playerMine.remove(uid); continue; }
                    MineData mine = mines.get(playerMine.get(uid));
                    if (mine == null) continue;
                    if (!isInsideMine(p.getLocation(), mine)) leaveMine(p);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    // ── Save / Load ───────────────────────────────────────────────────

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (MineData mine : mines.values()) {
            String path = "mines." + mine.getId();
            cfg.set(path + ".name",   mine.getName());
            cfg.set(path + ".owner",  mine.getOwner().toString());
            cfg.set(path + ".world",  mine.getWorldName());
            cfg.set(path + ".x",      mine.getCenter().getX());
            cfg.set(path + ".y",      mine.getCenter().getY());
            cfg.set(path + ".z",      mine.getCenter().getZ());
            cfg.set(path + ".sizeX",  mine.getSizeX());
            cfg.set(path + ".sizeZ",  mine.getSizeZ());
            cfg.set(path + ".depth",  mine.getDepth());
        }
        try { cfg.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.isConfigurationSection("mines")) return;
        for (String id : cfg.getConfigurationSection("mines").getKeys(false)) {
            String path = "mines." + id;
            World w = Bukkit.getWorld(cfg.getString(path + ".world", "world"));
            if (w == null) continue;
            Location loc = new Location(w, cfg.getDouble(path+".x"), cfg.getDouble(path+".y"), cfg.getDouble(path+".z"));
            UUID owner = UUID.fromString(cfg.getString(path + ".owner", UUID.randomUUID().toString()));
            MineData mine = new MineData(id, loc, owner, cfg.getString(path + ".name", id));
            mine.setSizeX(cfg.getInt(path + ".sizeX", 15));
            mine.setSizeZ(cfg.getInt(path + ".sizeZ", 15));
            mine.setDepth(cfg.getInt(path + ".depth", 10));
            mines.put(id, mine);
        }
    }

    public Collection<MineData> getAll() { return mines.values(); }
    public MineData getById(String id)   { return mines.get(id); }
}
