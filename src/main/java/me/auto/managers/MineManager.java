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

public class MineManager {

    private final AutoPlugin plugin;
    private final Map<String, MineData> mines = new HashMap<>();
    // player UUID -> mine id they're currently in
    private final Map<UUID, String> playerMine = new HashMap<>();
    private final File dataFile;

    public MineManager(AutoPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "mines.yml");
        loadAll();
        startResetTimer();
        startLeaveChecker();
    }

    // ── Create / Delete ───────────────────────────────────────────────

    public void createMine(Player p, String name) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Location center = p.getLocation();
        MineData mine = new MineData(id, center, p.getUniqueId(), name);
        mines.put(id, mine);
        buildMine(mine);
        saveAll();
        ColorUtils.msg(p, "&aШахту &e" + name + " &aстворено!");
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

        int size  = plugin.getConfig().getInt("mine.size", 15);
        int depth = plugin.getConfig().getInt("mine.depth", 10);
        Location c = mine.getCenter();
        int ox = c.getBlockX() - size / 2;
        int oy = c.getBlockY();
        int oz = c.getBlockZ() - size / 2;

        Random rng = new Random();

        for (int x = ox; x < ox + size; x++) {
            for (int z = oz; z < oz + size; z++) {
                // Floor / border
                w.getBlockAt(x, oy - 1,         z).setType(Material.STONE_BRICKS);
                w.getBlockAt(x, oy - depth - 1, z).setType(Material.BEDROCK);

                for (int y = oy - depth; y < oy; y++) {
                    boolean isDeep = (y < oy - depth / 2);
                    Material mat = rollOre(rng, isDeep);
                    w.getBlockAt(x, y, z).setType(mat);
                }
            }
        }

        // Walls + entrance ladder
        buildMineWalls(w, ox, oy, oz, size, depth);
        mine.setLastReset(System.currentTimeMillis());
    }

    private void buildMineWalls(World w, int ox, int oy, int oz, int size, int depth) {
        Material wall = Material.STONE_BRICKS;
        for (int y = oy - depth - 1; y <= oy; y++) {
            for (int x = ox - 1; x <= ox + size; x++) {
                w.getBlockAt(x, y, oz - 1).setType(wall);
                w.getBlockAt(x, y, oz + size).setType(wall);
            }
            for (int z = oz - 1; z <= oz + size; z++) {
                w.getBlockAt(ox - 1, y, z).setType(wall);
                w.getBlockAt(ox + size, y, z).setType(wall);
            }
        }
        // Top platform
        for (int x = ox - 1; x <= ox + size; x++)
            for (int z = oz - 1; z <= oz + size; z++)
                w.getBlockAt(x, oy, z).setType(Material.STONE_SLAB);

        // Ladder down the side
        for (int y = oy - depth; y < oy; y++)
            w.getBlockAt(ox + size - 1, y, oz).setType(Material.LADDER);

        // Lanterns
        for (int x = ox + 2; x < ox + size - 1; x += 4)
            for (int z = oz + 2; z < oz + size - 1; z += 4)
                w.getBlockAt(x, oy - 1, z).setType(Material.LANTERN);
    }

    private void clearMine(MineData mine) {
        World w = Bukkit.getWorld(mine.getWorldName());
        if (w == null) return;
        int size  = plugin.getConfig().getInt("mine.size", 15);
        int depth = plugin.getConfig().getInt("mine.depth", 10);
        Location c = mine.getCenter();
        int ox = c.getBlockX() - size / 2 - 1;
        int oy = c.getBlockY();
        int oz = c.getBlockZ() - size / 2 - 1;
        for (int x = ox; x <= ox + size + 2; x++)
            for (int z = oz; z <= oz + size + 2; z++)
                for (int y = oy - depth - 2; y <= oy + 1; y++)
                    w.getBlockAt(x, y, z).setType(Material.AIR);
    }

    private Material rollOre(Random rng, boolean deep) {
        String section = deep ? "mine.deep-composition" : "mine.composition";
        Map<String, Object> comp = plugin.getConfig().getConfigurationSection(section).getValues(false);
        double roll = rng.nextDouble();
        double cumulative = 0;
        for (Map.Entry<String, Object> entry : comp.entrySet()) {
            cumulative += (double) entry.getValue();
            if (roll <= cumulative) {
                Material mat = Material.matchMaterial(entry.getKey());
                return mat != null ? mat : (deep ? Material.DEEPSLATE : Material.STONE);
            }
        }
        return deep ? Material.DEEPSLATE : Material.STONE;
    }

    // ── Player tracking ───────────────────────────────────────────────

    public void enterMine(Player p, MineData mine) {
        playerMine.put(p.getUniqueId(), mine.getId());
        mine.setActivePlayer(p.getUniqueId());
        ColorUtils.msg(p, "&aВи увійшли в шахту &e" + mine.getName());
        ColorUtils.actionBar(p, "&eШахта: &a" + mine.getName() +
                " &7| Оновлення через &e" + getResetSeconds(mine) + "s");
    }

    public void leaveMine(Player p) {
        String id = playerMine.remove(p.getUniqueId());
        if (id == null) return;
        MineData mine = mines.get(id);
        if (mine != null && p.getUniqueId().equals(mine.getActivePlayer())) {
            mine.setActivePlayer(null);
        }
    }

    public MineData getMineAt(Location loc) {
        for (MineData mine : mines.values()) {
            if (isInsideMine(loc, mine)) return mine;
        }
        return null;
    }

    public MineData getPlayerMine(Player p) {
        String id = playerMine.get(p.getUniqueId());
        return id != null ? mines.get(id) : null;
    }

    private boolean isInsideMine(Location loc, MineData mine) {
        if (!loc.getWorld().getName().equals(mine.getWorldName())) return false;
        int size  = plugin.getConfig().getInt("mine.size", 15);
        int depth = plugin.getConfig().getInt("mine.depth", 10);
        Location c = mine.getCenter();
        int dx = Math.abs(loc.getBlockX() - c.getBlockX());
        int dz = Math.abs(loc.getBlockZ() - c.getBlockZ());
        int dy = c.getBlockY() - loc.getBlockY();
        return dx <= size / 2 + 1 && dz <= size / 2 + 1 && dy >= 0 && dy <= depth + 2;
    }

    private int getResetSeconds(MineData mine) {
        long resetMs = plugin.getConfig().getLong("mine.reset-time", 300) * 1000L;
        long elapsed = System.currentTimeMillis() - mine.getLastReset();
        return (int) Math.max(0, (resetMs - elapsed) / 1000);
    }

    // ── Timers ────────────────────────────────────────────────────────

    private void startResetTimer() {
        new BukkitRunnable() {
            @Override public void run() {
                long resetMs = plugin.getConfig().getLong("mine.reset-time", 300) * 1000L;
                for (MineData mine : mines.values()) {
                    if (System.currentTimeMillis() - mine.getLastReset() >= resetMs) {
                        buildMine(mine);
                        // Notify active player
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
        int leaveDist = plugin.getConfig().getInt("settings.leave-distance", 10);
        new BukkitRunnable() {
            @Override public void run() {
                for (Map.Entry<UUID, String> entry : new HashMap<>(playerMine).entrySet()) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null) { playerMine.remove(entry.getKey()); continue; }
                    MineData mine = mines.get(entry.getValue());
                    if (mine == null) continue;
                    if (!isInsideMine(p.getLocation(), mine) &&
                        p.getLocation().distance(mine.getCenter()) > leaveDist) {
                        leaveMine(p);
                        ColorUtils.msg(p, "&cВи покинули шахту — прогрес збережено.");
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    // ── Save / Load ───────────────────────────────────────────────────

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (MineData mine : mines.values()) {
            String path = "mines." + mine.getId();
            cfg.set(path + ".name",    mine.getName());
            cfg.set(path + ".owner",   mine.getOwner().toString());
            cfg.set(path + ".world",   mine.getWorldName());
            cfg.set(path + ".x",       mine.getCenter().getX());
            cfg.set(path + ".y",       mine.getCenter().getY());
            cfg.set(path + ".z",       mine.getCenter().getZ());
        }
        try { cfg.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.isConfigurationSection("mines")) return;
        for (String id : cfg.getConfigurationSection("mines").getKeys(false)) {
            String path = "mines." + id;
            String worldName = cfg.getString(path + ".world");
            World w = Bukkit.getWorld(worldName != null ? worldName : "world");
            if (w == null) continue;
            Location loc = new Location(w,
                    cfg.getDouble(path + ".x"),
                    cfg.getDouble(path + ".y"),
                    cfg.getDouble(path + ".z"));
            UUID owner = UUID.fromString(cfg.getString(path + ".owner", UUID.randomUUID().toString()));
            String name = cfg.getString(path + ".name", id);
            mines.put(id, new MineData(id, loc, owner, name));
        }
    }

    public Collection<MineData> getAll() { return mines.values(); }
    public MineData getById(String id)   { return mines.get(id); }
}
