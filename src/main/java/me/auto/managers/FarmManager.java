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
    private final Map<String, FarmData> farms   = new HashMap<>();
    private final Map<UUID, String> playerFarm   = new HashMap<>();
    private final File dataFile;

    public FarmManager(AutoPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "farms.yml");
        loadAll();
        startGrowthTimer();
        startResetTimer();
        startLeaveChecker();
    }

    public void createFarm(Player p, String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        FarmData farm = new FarmData(id, p.getLocation(), p.getUniqueId(), name);
        farms.put(id, farm);
        buildFarm(farm);
        saveAll();
        ColorUtils.msg(p, "&aФерму &e" + name + " &aстворено!");
    }

    public boolean deleteFarm(String id) {
        FarmData f = farms.remove(id);
        if (f == null) return false;
        clearFarm(f);
        saveAll();
        return true;
    }

    // ── Build / Reset ─────────────────────────────────────────────────

    public void buildFarm(FarmData farm) {
        World w = Bukkit.getWorld(farm.getWorldName());
        if (w == null) return;

        int fw = plugin.getConfig().getInt("farm.width",  9);
        int fl = plugin.getConfig().getInt("farm.length", 9);
        List<String> crops = plugin.getConfig().getStringList("farm.crops");
        Location c = farm.getCenter();
        int ox = c.getBlockX() - fw / 2;
        int oy = c.getBlockY();
        int oz = c.getBlockZ() - fl / 2;

        Random rng = new Random();

        // Water channel in middle
        w.getBlockAt(ox + fw / 2, oy - 1, oz - 1).setType(Material.WATER);
        for (int z = oz; z < oz + fl; z++)
            w.getBlockAt(ox + fw / 2, oy - 1, z).setType(Material.WATER);

        // Farmland and crops
        for (int x = ox; x < ox + fw; x++) {
            for (int z = oz; z < oz + fl; z++) {
                if (x == ox + fw / 2) continue; // water channel
                w.getBlockAt(x, oy - 1, z).setType(Material.FARMLAND);
                // Clear above
                w.getBlockAt(x, oy, z).setType(Material.AIR);
                w.getBlockAt(x, oy + 1, z).setType(Material.AIR);

                // Plant random crop
                String cropName = crops.get(rng.nextInt(crops.size()));
                Material crop = Material.matchMaterial(cropName);
                if (crop != null) {
                    Block b = w.getBlockAt(x, oy, z);
                    b.setType(crop);
                    // Set to max growth
                    if (b.getBlockData() instanceof Ageable age) {
                        age.setAge(age.getMaximumAge());
                        b.setBlockData(age);
                    }
                }
            }
        }

        // Fence border
        for (int x = ox - 1; x <= ox + fw; x++) {
            w.getBlockAt(x, oy - 1, oz - 2).setType(Material.OAK_FENCE);
            w.getBlockAt(x, oy - 1, oz + fl).setType(Material.OAK_FENCE);
        }
        for (int z = oz - 2; z <= oz + fl; z++) {
            w.getBlockAt(ox - 1, oy - 1, z).setType(Material.OAK_FENCE);
            w.getBlockAt(ox + fw, oy - 1, z).setType(Material.OAK_FENCE);
        }

        // Lanterns at corners
        w.getBlockAt(ox - 1, oy, oz - 2).setType(Material.LANTERN);
        w.getBlockAt(ox + fw, oy, oz - 2).setType(Material.LANTERN);
        w.getBlockAt(ox - 1, oy, oz + fl).setType(Material.LANTERN);
        w.getBlockAt(ox + fw, oy, oz + fl).setType(Material.LANTERN);

        farm.setLastReset(System.currentTimeMillis());
    }

    private void clearFarm(FarmData farm) {
        World w = Bukkit.getWorld(farm.getWorldName());
        if (w == null) return;
        int fw = plugin.getConfig().getInt("farm.width",  9);
        int fl = plugin.getConfig().getInt("farm.length", 9);
        Location c = farm.getCenter();
        for (int x = c.getBlockX() - fw / 2 - 2; x <= c.getBlockX() + fw / 2 + 2; x++)
            for (int z = c.getBlockZ() - fl / 2 - 2; z <= c.getBlockZ() + fl / 2 + 2; z++)
                for (int y = c.getBlockY() - 1; y <= c.getBlockY() + 2; y++)
                    w.getBlockAt(x, y, z).setType(Material.AIR);
    }

    // ── Harvest mechanic ──────────────────────────────────────────────

    /**
     * Called when player breaks a crop.
     * Gives money, replants immediately — player gets NO items.
     */
    public void onHarvest(Player p, Block block, FarmData farm) {
        Material cropType = block.getType();
        String matName = cropType.name();

        // Check if fully grown
        if (block.getBlockData() instanceof Ageable age) {
            if (age.getAge() < age.getMaximumAge()) {
                plugin.getHudManager().sendActionBar(p, "&cУрожай ще не дозрів!");
                return;
            }
        }

        // Reward
        double reward = plugin.getConfig().getDouble("farm.rewards." + matName, 2.0);
        plugin.getEconomy().depositPlayer(p, reward);

        // Replant immediately
        block.setType(cropType);
        if (block.getBlockData() instanceof Ageable age) {
            age.setAge(0);
            block.setBlockData(age);
        }

        plugin.getHudManager().sendActionBar(p, "&a+$" + reward + " &7за &e" + matName.toLowerCase().replace("_", " ") + "!");
        p.playSound(p.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
    }

    // ── Player tracking ───────────────────────────────────────────────

    public void enterFarm(Player p, FarmData farm) {
        playerFarm.put(p.getUniqueId(), farm.getId());
        ColorUtils.msg(p, "&aВи на фермі &e" + farm.getName() + " &7| Ломайте урожай для заробітку!");
    }

    public void leaveFarm(Player p) {
        playerFarm.remove(p.getUniqueId());
        ColorUtils.msg(p, "&cВи покинули ферму.");
    }

    public FarmData getFarmAt(Location loc) {
        for (FarmData f : farms.values()) {
            if (isInside(loc, f)) return f;
        }
        return null;
    }

    public FarmData getPlayerFarm(Player p) {
        String id = playerFarm.get(p.getUniqueId());
        return id != null ? farms.get(id) : null;
    }

    private boolean isInside(Location loc, FarmData f) {
        if (!loc.getWorld().getName().equals(f.getWorldName())) return false;
        Location c = f.getCenter();
        int fw = plugin.getConfig().getInt("farm.width",  9);
        int fl = plugin.getConfig().getInt("farm.length", 9);
        return Math.abs(loc.getBlockX() - c.getBlockX()) <= fw / 2 + 2 &&
               Math.abs(loc.getBlockZ() - c.getBlockZ()) <= fl / 2 + 2;
    }

    // ── Timers ────────────────────────────────────────────────────────

    /** Grow all crops every 30 seconds */
    private void startGrowthTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                for (FarmData farm : farms.values()) {
                    World w = Bukkit.getWorld(farm.getWorldName());
                    if (w == null) continue;
                    int fw = plugin.getConfig().getInt("farm.width", 9);
                    int fl = plugin.getConfig().getInt("farm.length", 9);
                    Location c = farm.getCenter();
                    for (int x = c.getBlockX() - fw/2; x < c.getBlockX() + fw/2; x++) {
                        for (int z = c.getBlockZ() - fl/2; z < c.getBlockZ() + fl/2; z++) {
                            Block b = w.getBlockAt(x, c.getBlockY(), z);
                            if (b.getBlockData() instanceof Ageable age) {
                                if (age.getAge() < age.getMaximumAge()) {
                                    age.setAge(age.getAge() + 1);
                                    b.setBlockData(age);
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // every 30 sec
    }

    /** Full reset every reset-time seconds */
    private void startResetTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                long resetMs = plugin.getConfig().getLong("farm.reset-time", 180) * 1000L;
                for (FarmData f : farms.values()) {
                    if (System.currentTimeMillis() - f.getLastReset() >= resetMs) {
                        buildFarm(f);
                        if (f.hasActivePlayer()) {
                            Player p = Bukkit.getPlayer(f.getActivePlayer());
                            if (p != null) ColorUtils.msg(p, "&aФерма &e" + f.getName() + " &aоновилась!");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startLeaveChecker() {
        new BukkitRunnable() {
            @Override public void run() {
                int dist = plugin.getConfig().getInt("settings.leave-distance", 10);
                for (Map.Entry<UUID, String> e : new HashMap<>(playerFarm).entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p == null) { playerFarm.remove(e.getKey()); continue; }
                    FarmData f = farms.get(e.getValue());
                    if (f == null) continue;
                    if (p.getLocation().distance(f.getCenter()) > dist && !isInside(p.getLocation(), f))
                        leaveFarm(p);
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
            farms.put(id, new FarmData(id, loc, owner, cfg.getString(path + ".name", id)));
        }
    }

    public Collection<FarmData> getAll() { return farms.values(); }
    public FarmData getById(String id)   { return farms.get(id); }
}
