package me.auto.managers;

import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class ForestManager {

    private final AutoPlugin plugin;
    private final Map<String, ForestData> forests = new HashMap<>();
    private final Map<UUID, String> playerForest   = new HashMap<>();
    // tree location key -> chop count for that player session
    private final Map<String, Map<UUID, Integer>> chopCount = new HashMap<>();
    private final File dataFile;

    public ForestManager(AutoPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "forests.yml");
        loadAll();
        startResetTimer();
        startLeaveChecker();
    }

    public void createForest(Player p, String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        ForestData forest = new ForestData(id, p.getLocation(), p.getUniqueId(), name);
        forests.put(id, forest);
        buildForest(forest);
        saveAll();
        ColorUtils.msg(p, "&aЛісоруб &e" + name + " &aстворено!");
    }

    public boolean deleteForest(String id) {
        ForestData f = forests.remove(id);
        if (f == null) return false;
        clearForest(f);
        saveAll();
        return true;
    }

    // ── Build / Reset ─────────────────────────────────────────────────

    public void buildForest(ForestData forest) {
        World w = Bukkit.getWorld(forest.getWorldName());
        if (w == null) return;

        List<String> treeTypes = plugin.getConfig().getStringList("forest.tree-types");
        Location c = forest.getCenter();
        int size = 15;
        int ox = c.getBlockX() - size / 2;
        int oy = c.getBlockY();
        int oz = c.getBlockZ() - size / 2;

        // Grass floor
        for (int x = ox; x < ox + size; x++)
            for (int z = oz; z < oz + size; z++)
                w.getBlockAt(x, oy - 1, z).setType(Material.GRASS_BLOCK);

        // Clear area
        for (int x = ox; x < ox + size; x++)
            for (int z = oz; z < oz + size; z++)
                for (int y = oy; y < oy + 12; y++)
                    w.getBlockAt(x, y, z).setType(Material.AIR);

        // Plant trees every 3 blocks
        Random rng = new Random();
        List<Location> treeLocs = new ArrayList<>();
        for (int x = ox + 1; x < ox + size - 1; x += 3) {
            for (int z = oz + 1; z < oz + size - 1; z += 3) {
                Location treeLoc = new Location(w, x, oy, z);
                String type = treeTypes.get(rng.nextInt(treeTypes.size()));
                TreeType tt = getTreeType(type);
                if (tt != null) {
                    w.generateTree(treeLoc, rng, tt);
                    treeLocs.add(treeLoc);
                }
            }
        }

        forest.setTreeLocations(treeLocs);
        forest.setLastReset(System.currentTimeMillis());
        chopCount.remove(forest.getId()); // reset chop counts
    }

    private void clearForest(ForestData forest) {
        World w = Bukkit.getWorld(forest.getWorldName());
        if (w == null) return;
        Location c = forest.getCenter();
        int size = 15;
        for (int x = c.getBlockX() - size / 2 - 1; x <= c.getBlockX() + size / 2 + 1; x++)
            for (int z = c.getBlockZ() - size / 2 - 1; z <= c.getBlockZ() + size / 2 + 1; z++)
                for (int y = c.getBlockY() - 1; y <= c.getBlockY() + 12; y++)
                    w.getBlockAt(x, y, z).setType(Material.AIR);
    }

    private TreeType getTreeType(String name) {
        return switch (name.toUpperCase()) {
            case "OAK"       -> TreeType.TREE;
            case "BIRCH"     -> TreeType.BIRCH;
            case "SPRUCE"    -> TreeType.REDWOOD;
            case "JUNGLE"    -> TreeType.JUNGLE;
            case "ACACIA"    -> TreeType.ACACIA;
            case "DARK_OAK"  -> TreeType.DARK_OAK;
            case "MANGROVE"  -> TreeType.MANGROVE;
            case "CHERRY"    -> TreeType.CHERRY;
            default          -> TreeType.TREE;
        };
    }

    // ── Chop mechanic ─────────────────────────────────────────────────

    /**
     * Called when player breaks a log in a forest.
     * Returns true if the tree is fully chopped (reward given).
     */
    public boolean onChop(Player p, Block block, ForestData forest) {
        String locKey = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        int required  = plugin.getConfig().getInt("forest.chops-required", 5);

        Map<UUID, Integer> counts = chopCount.computeIfAbsent(locKey, k -> new HashMap<>());
        int current = counts.getOrDefault(p.getUniqueId(), 0) + 1;
        counts.put(p.getUniqueId(), current);

        int remaining = required - current;
        if (remaining > 0) {
            plugin.getHudManager().sendActionBar(p, "&7Зрубати &e" + remaining + " &7разів ще...");
            // Cancel the break — we count manually
            return false;
        }

        // Chopped! Give reward
        String matName = block.getType().name();
        double reward = plugin.getConfig().getDouble("forest.rewards." + matName, 3.0);
        plugin.getEconomy().depositPlayer(p, reward);
        counts.remove(p.getUniqueId());

        plugin.getHudManager().sendActionBar(p, "&a+$" + reward + " &7за &e" + matName.replace("_LOG","").toLowerCase() + " &7дерево!");
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        // Remove the log block visually but don't drop item
        block.setType(Material.AIR);
        return true;
    }

    // ── Player tracking ───────────────────────────────────────────────

    public void enterForest(Player p, ForestData forest) {
        playerForest.put(p.getUniqueId(), forest.getId());
        ColorUtils.msg(p, "&aВи увійшли в ліс &e" + forest.getName() +
                " &7| Рубайте дерева " + plugin.getConfig().getInt("forest.chops-required", 5) + "x");
    }

    public void leaveForest(Player p) {
        String id = playerForest.remove(p.getUniqueId());
        if (id == null) return;
        // Clear chop progress for this player
        for (Map<UUID, Integer> counts : chopCount.values()) counts.remove(p.getUniqueId());
        ColorUtils.msg(p, "&cВи покинули ліс — прогрес скинуто.");
    }

    public ForestData getForestAt(Location loc) {
        for (ForestData f : forests.values()) {
            if (isInside(loc, f)) return f;
        }
        return null;
    }

    public ForestData getPlayerForest(Player p) {
        String id = playerForest.get(p.getUniqueId());
        return id != null ? forests.get(id) : null;
    }

    private boolean isInside(Location loc, ForestData f) {
        if (!loc.getWorld().getName().equals(f.getWorldName())) return false;
        Location c = f.getCenter();
        return Math.abs(loc.getBlockX() - c.getBlockX()) <= 9 &&
               Math.abs(loc.getBlockZ() - c.getBlockZ()) <= 9;
    }

    // ── Timers ────────────────────────────────────────────────────────

    private void startResetTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                long resetMs = plugin.getConfig().getLong("forest.reset-time", 50) * 1000L;
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
                int dist = plugin.getConfig().getInt("settings.leave-distance", 10);
                for (Map.Entry<UUID, String> e : new HashMap<>(playerForest).entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p == null) { playerForest.remove(e.getKey()); continue; }
                    ForestData f = forests.get(e.getValue());
                    if (f == null) continue;
                    if (p.getLocation().distance(f.getCenter()) > dist && !isInside(p.getLocation(), f)) {
                        leaveForest(p);
                    }
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
        }
        try { cfg.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.isConfigurationSection("forests")) return;
        for (String id : cfg.getConfigurationSection("forests").getKeys(false)) {
            String path  = "forests." + id;
            World w = Bukkit.getWorld(cfg.getString(path + ".world", "world"));
            if (w == null) continue;
            Location loc = new Location(w, cfg.getDouble(path+".x"), cfg.getDouble(path+".y"), cfg.getDouble(path+".z"));
            UUID owner = UUID.fromString(cfg.getString(path + ".owner", UUID.randomUUID().toString()));
            forests.put(id, new ForestData(id, loc, owner, cfg.getString(path + ".name", id)));
        }
    }

    public Collection<ForestData> getAll() { return forests.values(); }
    public ForestData getById(String id)   { return forests.get(id); }
}
