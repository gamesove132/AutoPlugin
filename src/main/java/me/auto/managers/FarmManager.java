package me.auto.managers;

import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class FarmManager {

    private final AutoPlugin plugin;
    private final Map<String, FarmData> farms = new HashMap<>();
    private final Map<UUID, String> playerFarm = new HashMap<>();
    private final File dataFile;

    public FarmManager(AutoPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "farms.yml");
        loadAll();
        startGrowthTimer();
        startLeaveChecker();
    }

    public void createFarmFromSelection(Player p, String name) {
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
        FarmData farm = new FarmData(id, center, p.getUniqueId(), name);
        farm.setSizeX(sx);
        farm.setSizeZ(sz);
        farms.put(id, farm);
        buildFarm(farm);
        plugin.getSelectionManager().clear(p);
        saveAll();
        ColorUtils.msg(p, "&aФерму &e" + name + " &aстворено! ID: &f" + id + " &7(" + sx + "x" + sz + ")");
    }

    public boolean deleteFarm(String id) {
        FarmData f = farms.remove(id);
        if (f == null) return false;
        clearFarm(f);
        saveAll();
        return true;
    }

    // ── Build ─────────────────────────────────────────────────────────

    public void buildFarm(FarmData farm) {
        World w = Bukkit.getWorld(farm.getWorldName());
        if (w == null) return;

        int sx = farm.getSizeX();
        int sz = farm.getSizeZ();
        Location c = farm.getCenter();
        int ox = c.getBlockX() - sx / 2;
        int oy = c.getBlockY();
        int oz = c.getBlockZ() - sz / 2;

        Random rng = new Random();
        List<String> crops = plugin.getConfig().getStringList("farm.crops");
        if (crops.isEmpty()) crops = List.of("WHEAT","CARROTS","POTATOES","BEETROOTS","MELON_STEM","PUMPKIN_STEM");

        // Рядок посередині — іригаційний канал (нижче рівня ходіння)
        int waterX = ox + sx / 2;
        for (int z = oz - 1; z < oz + sz + 1; z++)
            w.getBlockAt(waterX, oy - 2, z).setType(Material.WATER);

        for (int x = ox; x < ox + sx; x++) {
            for (int z = oz; z < oz + sz; z++) {
                // Підлога — farmland
                w.getBlockAt(x, oy - 1, z).setType(Material.FARMLAND);
                // Повітря зверху
                w.getBlockAt(x, oy,     z).setType(Material.AIR);
                w.getBlockAt(x, oy + 1, z).setType(Material.AIR);

                // Культура
                String cropName = crops.get(rng.nextInt(crops.size()));
                Material crop = Material.matchMaterial(cropName);
                if (crop != null) {
                    Block b = w.getBlockAt(x, oy, z);
                    b.setType(crop);
                    if (b.getBlockData() instanceof Ageable age) {
                        age.setAge(age.getMaximumAge());
                        b.setBlockData(age);
                    }
                }
            }
        }

        // Ліхтарики по кутах (без паркану!)
        w.getBlockAt(ox - 1,    oy, oz - 1).setType(Material.TORCH);
        w.getBlockAt(ox + sx,   oy, oz - 1).setType(Material.TORCH);
        w.getBlockAt(ox - 1,    oy, oz + sz).setType(Material.TORCH);
        w.getBlockAt(ox + sx,   oy, oz + sz).setType(Material.TORCH);

        farm.setLastReset(System.currentTimeMillis());
    }

    private void clearFarm(FarmData f) {
        World w = Bukkit.getWorld(f.getWorldName());
        if (w == null) return;
        Location c = f.getCenter();
        for (int x = c.getBlockX() - f.getSizeX()/2 - 2; x <= c.getBlockX() + f.getSizeX()/2 + 2; x++)
            for (int z = c.getBlockZ() - f.getSizeZ()/2 - 2; z <= c.getBlockZ() + f.getSizeZ()/2 + 2; z++)
                for (int y = c.getBlockY() - 2; y <= c.getBlockY() + 3; y++)
                    w.getBlockAt(x, y, z).setType(Material.AIR);
    }

    // ── Harvest ───────────────────────────────────────────────────────

    /**
     * Збирання врожаю — БЕЗ дропу предметів.
     * Після збирання культура відновлюється.
     */
    public void onHarvest(Player p, Block block, FarmData farm) {
        Material type = block.getType();

        // Кавун і тиква — повне прибирання, стебло залишається
        if (type == Material.MELON || type == Material.PUMPKIN) {
            double reward = plugin.getConfig().getDouble(
                    "farm.rewards." + type.name(), 4.0);
            plugin.getEconomy().depositPlayer(p, reward);
            block.setType(Material.AIR); // Просто видаляємо — не дропаємо
            plugin.getHudManager().sendActionBar(p,
                    "&a+$" + reward + " &7за &e" + (type == Material.MELON ? "кавун" : "тиква") + "!");
            p.playSound(p.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
            return;
        }

        // Решта — перевіряємо дозрілість
        if (!(block.getBlockData() instanceof Ageable age)) return;
        if (age.getAge() < age.getMaximumAge()) {
            plugin.getHudManager().sendActionBar(p, "&cНе дозрів!");
            return;
        }

        double reward = plugin.getConfig().getDouble("farm.rewards." + type.name(), 2.0);
        plugin.getEconomy().depositPlayer(p, reward);
        plugin.getHudManager().sendActionBar(p, "&a+$" + reward + " &7за &e" +
                type.name().toLowerCase().replace("_", " ") + "!");
        p.playSound(p.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);

        // Відновлюємо культуру одразу (вік = 0)
        block.setType(type);
        if (block.getBlockData() instanceof Ageable fresh) {
            fresh.setAge(0);
            block.setBlockData(fresh);
        }
    }

    // ── Player tracking ───────────────────────────────────────────────

    public void enterFarm(Player p, FarmData farm) {
        playerFarm.put(p.getUniqueId(), farm.getId());
        farm.setActivePlayer(p.getUniqueId());
        ColorUtils.msg(p, "&aВи на фермі &e" + farm.getName() + " &7— збирайте врожай!");
        plugin.getScoreboardManager().showFarmBoard(p);
    }

    public void leaveFarm(Player p) {
        String id = playerFarm.remove(p.getUniqueId());
        if (id == null) return;
        FarmData f = farms.get(id);
        if (f != null && p.getUniqueId().equals(f.getActivePlayer())) f.setActivePlayer(null);
        plugin.getNpcManager().losePending(p.getUniqueId(), "farm");
        plugin.getScoreboardManager().showLobbyBoard(p);
    }

    public FarmData getFarmAt(Location loc) {
        for (FarmData f : farms.values()) if (isInside(loc, f)) return f;
        return null;
    }

    public FarmData getPlayerFarm(Player p) {
        String id = playerFarm.get(p.getUniqueId());
        return id != null ? farms.get(id) : null;
    }

    private boolean isInside(Location loc, FarmData f) {
        if (!loc.getWorld().getName().equals(f.getWorldName())) return false;
        Location c = f.getCenter();
        return Math.abs(loc.getBlockX() - c.getBlockX()) <= f.getSizeX()/2 + 2 &&
               Math.abs(loc.getBlockZ() - c.getBlockZ()) <= f.getSizeZ()/2 + 2;
    }

    // ── Timers ────────────────────────────────────────────────────────

    private void startGrowthTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                for (FarmData farm : farms.values()) {
                    World w = Bukkit.getWorld(farm.getWorldName());
                    if (w == null) continue;
                    Location c = farm.getCenter();
                    for (int x = c.getBlockX()-farm.getSizeX()/2; x < c.getBlockX()+farm.getSizeX()/2; x++)
                        for (int z = c.getBlockZ()-farm.getSizeZ()/2; z < c.getBlockZ()+farm.getSizeZ()/2; z++) {
                            Block b = w.getBlockAt(x, c.getBlockY(), z);
                            if (b.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
                                age.setAge(age.getAge() + 1);
                                b.setBlockData(age);
                            }
                        }
                }
            }
        }.runTaskTimer(plugin, 400L, 400L); // ~20 секунд
    }

    private void startLeaveChecker() {
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID uid : new HashSet<>(playerFarm.keySet())) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p == null) { playerFarm.remove(uid); continue; }
                    FarmData f = farms.get(playerFarm.get(uid));
                    if (f == null || !isInside(p.getLocation(), f)) leaveFarm(p);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    // ── Save / Load ───────────────────────────────────────────────────

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (FarmData f : farms.values()) {
            String path = "farms." + f.getId();
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
        if (!cfg.isConfigurationSection("farms")) return;
        for (String id : cfg.getConfigurationSection("farms").getKeys(false)) {
            String path = "farms." + id;
            World w = Bukkit.getWorld(cfg.getString(path + ".world", "world"));
            if (w == null) continue;
            Location loc = new Location(w, cfg.getDouble(path+".x"), cfg.getDouble(path+".y"), cfg.getDouble(path+".z"));
            UUID owner = UUID.fromString(cfg.getString(path + ".owner", UUID.randomUUID().toString()));
            FarmData farm = new FarmData(id, loc, owner, cfg.getString(path + ".name", id));
            farm.setSizeX(cfg.getInt(path + ".sizeX", 9));
            farm.setSizeZ(cfg.getInt(path + ".sizeZ", 9));
            farms.put(id, farm);
        }
    }

    public Collection<FarmData> getAll() { return farms.values(); }
    public FarmData getById(String id)   { return farms.get(id); }
}
