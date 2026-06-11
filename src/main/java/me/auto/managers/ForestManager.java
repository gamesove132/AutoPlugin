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

public class ForestManager {

    private final AutoPlugin plugin;
    private final Map<String, ForestData> forests = new HashMap<>();
    private final Map<UUID, String> playerForest = new HashMap<>();
    private final Map<String, Map<UUID, Integer>> chopCounts = new HashMap<>();
    private final File dataFile;

    public ForestManager(AutoPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "forests.yml");
        loadAll();
        startResetTimer();
        startLeaveChecker();
    }

    // ── Сокира ───────────────────────────────────────────────────────

    public static ItemStack createAxe() {
        ItemStack axe = new ItemStack(Material.STONE_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "🪓 Лісорубська сокира");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Тільки для авто-лісу");
        lore.add(ChatColor.DARK_GRAY + "Звичайні дерева не рубає");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.EFFICIENCY, 2, true);
        axe.setItemMeta(meta);
        return axe;
    }

    public static boolean isForestAxe(ItemStack item) {
        if (item == null || item.getType() != Material.STONE_AXE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        return item.getItemMeta().getDisplayName().contains("Лісорубська сокира");
    }

    private void giveAxe(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isForestAxe(hand)) return;
        ItemStack axe = createAxe();
        if (hand.getType() == Material.AIR) {
            p.getInventory().setItemInMainHand(axe);
        } else {
            p.getInventory().addItem(axe);
        }
        p.updateInventory();
        ColorUtils.msg(p, "&7Отримано &a🪓 Лісорубська сокира!");
    }

    // ── Create / Delete ───────────────────────────────────────────────

    public void createForestFromSelection(Player p, String name) {
        if (!plugin.getSelectionManager().hasSelection(p)) {
            ColorUtils.msg(p, "&cСпочатку виділи область топором виділення!");
            return;
        }
        int sx = plugin.getSelectionManager().getSizeX(p);
        int sz = plugin.getSelectionManager().getSizeZ(p);
        Location center = plugin.getSelectionManager().getCenter(p);
        Location max = plugin.getSelectionManager().getMax(p);
        center.setY(max.getY());

        String id = UUID.randomUUID().toString().substring(0, 8);
        ForestData forest = new ForestData(id, center, p.getUniqueId(), name);
        forest.setSizeX(sx);
        forest.setSizeZ(sz);
        forests.put(id, forest);
        buildForest(forest);
        plugin.getSelectionManager().clear(p);
        saveAll();
        ColorUtils.msg(p, "&aЛіс &e" + name + " &aстворено! ID: &f" + id + " &7(" + sx + "x" + sz + ")");
    }

    public boolean deleteForest(String id) {
        ForestData f = forests.remove(id);
        if (f == null) return false;
        clearForest(f);
        saveAll();
        return true;
    }

    // ── Build ─────────────────────────────────────────────────────────

    public void buildForest(ForestData forest) {
        World w = Bukkit.getWorld(forest.getWorldName());
        if (w == null) return;

        int sx = forest.getSizeX();
        int sz = forest.getSizeZ();
        Location c = forest.getCenter();
        int ox = c.getBlockX() - sx / 2;
        int oy = c.getBlockY();
        int oz = c.getBlockZ() - sz / 2;

        // Трава підлога
        for (int x = ox; x < ox + sx; x++)
            for (int z = oz; z < oz + sz; z++) {
                w.getBlockAt(x, oy - 1, z).setType(Material.GRASS_BLOCK);
                // Очищаємо місце для дерев
                for (int y = oy; y < oy + 16; y++)
                    w.getBlockAt(x, y, z).setType(Material.AIR);
            }

        // Відстань між деревами: більше поле = менша відстань = більше дерев
        int spacing = sx >= 15 ? 3 : (sx >= 10 ? 4 : 5);

        // Тільки класичні дерева: дуб, береза, ялина
        TreeType[] types = { TreeType.TREE, TreeType.BIRCH, TreeType.REDWOOD };
        Random rng = new Random();
        List<Location> treeLocs = new ArrayList<>();

        for (int x = ox + 1; x < ox + sx - 1; x += spacing) {
            for (int z = oz + 1; z < oz + sz - 1; z += spacing) {
                Location treeLoc = new Location(w, x, oy, z);
                TreeType tt = types[rng.nextInt(types.length)];
                boolean grown = w.generateTree(treeLoc, rng, tt);
                if (grown) treeLocs.add(treeLoc);
            }
        }

        forest.setTreeLocations(treeLocs);
        forest.setLastReset(System.currentTimeMillis());
        chopCounts.remove(forest.getId());
    }

    private void clearForest(ForestData f) {
        World w = Bukkit.getWorld(f.getWorldName());
        if (w == null) return;
        Location c = f.getCenter();
        for (int x = c.getBlockX()-f.getSizeX()/2-1; x <= c.getBlockX()+f.getSizeX()/2+1; x++)
            for (int z = c.getBlockZ()-f.getSizeZ()/2-1; z <= c.getBlockZ()+f.getSizeZ()/2+1; z++)
                for (int y = c.getBlockY()-1; y <= c.getBlockY()+16; y++)
                    w.getBlockAt(x, y, z).setType(Material.AIR);
    }

    // ── Chop ──────────────────────────────────────────────────────────

    public boolean onChop(Player p, Block block, ForestData forest) {
        String key = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        int required = plugin.getConfig().getInt("forest.chops-required", 5);

        Map<UUID, Integer> counts = chopCounts
                .computeIfAbsent(forest.getId() + ":" + key, k -> new HashMap<>());
        int current = counts.getOrDefault(p.getUniqueId(), 0) + 1;
        counts.put(p.getUniqueId(), current);

        int remaining = required - current;
        if (remaining > 0) {
            plugin.getHudManager().sendActionBar(p, "&7Ще &e" + remaining + " &7удар(и)...");
            return false;
        }

        counts.remove(p.getUniqueId());
        block.setType(Material.AIR);
        return true;
    }

    // ── Player tracking ───────────────────────────────────────────────

    public void enterForest(Player p, ForestData forest) {
        playerForest.put(p.getUniqueId(), forest.getId());
        forest.setActivePlayer(p.getUniqueId());
        ColorUtils.msg(p, "&aВи у лісі &e" + forest.getName() + " &7— рубайте дерева!");
        giveAxe(p);
        plugin.getScoreboardManager().showForestBoard(p);
    }

    public void leaveForest(Player p) {
        String id = playerForest.remove(p.getUniqueId());
        if (id == null) return;
        ForestData f = forests.get(id);
        if (f != null && p.getUniqueId().equals(f.getActivePlayer())) f.setActivePlayer(null);
        plugin.getNpcManager().losePending(p.getUniqueId(), "forest");
        plugin.getScoreboardManager().showLobbyBoard(p);
    }

    public ForestData getForestAt(Location loc) {
        for (ForestData f : forests.values()) if (isInside(loc, f)) return f;
        return null;
    }

    public ForestData getPlayerForest(Player p) {
        String id = playerForest.get(p.getUniqueId());
        return id != null ? forests.get(id) : null;
    }

    private boolean isInside(Location loc, ForestData f) {
        if (!loc.getWorld().getName().equals(f.getWorldName())) return false;
        Location c = f.getCenter();
        return Math.abs(loc.getBlockX() - c.getBlockX()) <= f.getSizeX()/2 + 1 &&
               Math.abs(loc.getBlockZ() - c.getBlockZ()) <= f.getSizeZ()/2 + 1;
    }

    // ── Timers ────────────────────────────────────────────────────────

    private void startResetTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                long resetMs = plugin.getConfig().getLong("forest.reset-time", 60) * 1000L;
                for (ForestData f : forests.values()) {
                    if (System.currentTimeMillis() - f.getLastReset() >= resetMs) {
                        buildForest(f);
                        if (f.hasActivePlayer()) {
                            Player p = Bukkit.getPlayer(f.getActivePlayer());
                            if (p != null) ColorUtils.msg(p, "&aЛіс &e" + f.getName() + " &aоновився!");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startLeaveChecker() {
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID uid : new HashSet<>(playerForest.keySet())) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p == null) { playerForest.remove(uid); continue; }
                    ForestData f = forests.get(playerForest.get(uid));
                    if (f == null || !isInside(p.getLocation(), f)) leaveForest(p);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    // ── Save / Load ───────────────────────────────────────────────────

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (ForestData f : forests.values()) {
            String path = "forests." + f.getId();
            cfg.set(path + ".name",  f.getName());
            cfg.set(path + ".owner", f.getOwner().toString());
            cfg.set(path + ".world", f.getWorldName());
            cfg.set(path + ".x",     f.getCenter().getX());
            cfg.set(path + ".y",     f.getCenter().getY());
            cfg.set(path + ".z",     f.getCenter().getZ());
            cfg.set(path + ".sizeX", f.getSizeX());
            cfg.set(path + ".sizeZ", f.getSizeZ());
        }
        try { cfg.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.isConfigurationSection("forests")) return;
        for (String id : cfg.getConfigurationSection("forests").getKeys(false)) {
            String path = "forests." + id;
            World w = Bukkit.getWorld(cfg.getString(path + ".world", "world"));
            if (w == null) continue;
            Location loc = new Location(w, cfg.getDouble(path+".x"), cfg.getDouble(path+".y"), cfg.getDouble(path+".z"));
            UUID owner = UUID.fromString(cfg.getString(path + ".owner", UUID.randomUUID().toString()));
            ForestData forest = new ForestData(id, loc, owner, cfg.getString(path + ".name", id));
            forest.setSizeX(cfg.getInt(path + ".sizeX", 15));
            forest.setSizeZ(cfg.getInt(path + ".sizeZ", 15));
            forests.put(id, forest);
        }
    }

    public Collection<ForestData> getAll() { return forests.values(); }
    public ForestData getById(String id)   { return forests.get(id); }
}
