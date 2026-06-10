package me.auto.npc;

import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/**
 * NPC система.
 * Типи NPC:
 *   CUSTOM   — довільна команда (як раніше)
 *   MINE_NPC — "скупник руд": гравець підходить → отримує накопичений баланс з шахти
 *   FARM_NPC — "скупник врожаю"
 *   FOREST_NPC — "скупник деревини"
 *
 * Команда /anpc create <ім'я> [mine|farm|forest]
 * Без типу — CUSTOM (як раніше, налаштовується командою).
 */
public class NpcManager {

    private final AutoPlugin plugin;
    private final Map<UUID, NpcData> npcs = new HashMap<>();
    private final File dataFile;
    private final NamespacedKey npcKey;

    public NpcManager(AutoPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.npcKey   = new NamespacedKey(plugin, "doors_npc_id");
        loadAll();
    }

    // ── Create ────────────────────────────────────────────────────────

    public void createNpc(Player creator, String name, String type) {
        Location loc = creator.getLocation();
        World w = loc.getWorld();

        // NPC — невидимий ArmorStand з шоломом-головою
        ArmorStand stand = w.spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setSmall(false);
            s.setArms(false);
            s.setBasePlate(false);
            String prefix = getNpcPrefix(type);
            s.setCustomName(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("npc.name-color", "&e") + prefix + name));
            s.setCustomNameVisible(plugin.getConfig().getBoolean("npc.show-name", true));
        });

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        stand.getEquipment().setHelmet(head);

        String id = UUID.randomUUID().toString().substring(0, 8);
        stand.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);

        NpcData data = new NpcData(id, stand.getUniqueId(), loc, creator.getUniqueId(), name);
        data.setNpcType(type.toUpperCase());
        npcs.put(stand.getUniqueId(), data);
        saveAll();

        ColorUtils.msg(creator, "&aNPC &e" + name + " &aстворено! (тип: &f" + type + "&a)");
        ColorUtils.msg(creator, "&7ID: &f" + id);
        if (type.equalsIgnoreCase("custom")) {
            ColorUtils.msg(creator, "&7Команда: &e/anpc command " + id + " <команда>");
        }
    }

    public void createNpc(Player creator, String name) {
        createNpc(creator, name, "custom");
    }

    private String getNpcPrefix(String type) {
        return switch (type.toUpperCase()) {
            case "MINE"   -> "⛏ ";
            case "FARM"   -> "🌾 ";
            case "FOREST" -> "🪵 ";
            default       -> "";
        };
    }

    public boolean deleteNpc(String id) {
        NpcData data = getNpcById(id);
        if (data == null) return false;
        Entity e = Bukkit.getEntity(data.getStandUuid());
        if (e != null) e.remove();
        npcs.values().removeIf(n -> n.getId().equals(id));
        saveAll();
        return true;
    }

    // ── Configure ─────────────────────────────────────────────────────

    public void setCommand(String npcId, String command) {
        NpcData data = getNpcById(npcId);
        if (data == null) return;
        data.setCommand(command);
        saveAll();
    }

    public void setSkin(String npcId, String playerName) {
        NpcData data = getNpcById(npcId);
        if (data == null) return;
        Entity e = Bukkit.getEntity(data.getStandUuid());
        if (!(e instanceof ArmorStand stand)) return;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(playerName);
        meta.setOwnerProfile(profile);
        head.setItemMeta(meta);
        stand.getEquipment().setHelmet(head);
        data.setSkinName(playerName);
        saveAll();
    }

    public void setName(String npcId, String newName) {
        NpcData data = getNpcById(npcId);
        if (data == null) return;
        data.setName(newName);
        Entity e = Bukkit.getEntity(data.getStandUuid());
        if (e != null) e.setCustomName(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("npc.name-color", "&e") +
                getNpcPrefix(data.getNpcType()) + newName));
        saveAll();
    }

    // ── Interact ──────────────────────────────────────────────────────

    public void onInteract(Player p, ArmorStand stand) {
        NpcData data = npcs.get(stand.getUniqueId());
        if (data == null) return;

        String type = data.getNpcType() != null ? data.getNpcType() : "CUSTOM";

        switch (type) {
            case "MINE" -> handleMineNpc(p, data);
            case "FARM" -> handleFarmNpc(p, data);
            case "FOREST" -> handleForestNpc(p, data);
            default -> handleCustomNpc(p, data);
        }
    }

    private void handleMineNpc(Player p, NpcData data) {
        double balance = plugin.getEconomy().getBalance(p);
        if (balance <= 0) {
            ColorUtils.msg(p, "&c" + data.getName() + ": &7У вас немає балансу для обміну.");
            return;
        }
        // NPC просто показує поточний баланс та підказку
        ColorUtils.msg(p, "&6⛏ " + data.getName() + " &7каже:");
        ColorUtils.msg(p, "  &7Ваш баланс: &e$" + String.format("%.1f", balance));
        ColorUtils.msg(p, "  &7Копайте більше руди щоб заробити! Алмаз = &f$100");
    }

    private void handleFarmNpc(Player p, NpcData data) {
        double balance = plugin.getEconomy().getBalance(p);
        ColorUtils.msg(p, "&2🌾 " + data.getName() + " &7каже:");
        ColorUtils.msg(p, "  &7Ваш баланс: &e$" + String.format("%.1f", balance));
        ColorUtils.msg(p, "  &7Збирайте врожай на фермі! Кавун/гарбуз = &f$4");
    }

    private void handleForestNpc(Player p, NpcData data) {
        double balance = plugin.getEconomy().getBalance(p);
        ColorUtils.msg(p, "&a🪵 " + data.getName() + " &7каже:");
        ColorUtils.msg(p, "  &7Ваш баланс: &e$" + String.format("%.1f", balance));
        ColorUtils.msg(p, "  &7Рубайте дерева в лісі щоб заробити!");
    }

    private void handleCustomNpc(Player p, NpcData data) {
        String cmd = data.getCommand();
        if (cmd == null || cmd.isEmpty()) {
            ColorUtils.msg(p, "&7NPC &e" + data.getName() + " &7[ID: " + data.getId() + "]");
            ColorUtils.msg(p, "&7Команда не налаштована. &e/anpc command " + data.getId() + " <команда>");
            return;
        }
        String finalCmd = cmd.replace("{player}", p.getName());
        if (finalCmd.startsWith("/")) finalCmd = finalCmd.substring(1);
        p.performCommand(finalCmd);
    }

    // ── List ──────────────────────────────────────────────────────────

    public void listNpcs(Player p) {
        if (npcs.isEmpty()) { ColorUtils.msg(p, "&7NPC не створено."); return; }
        ColorUtils.msg(p, "&6=== NPC список ===");
        for (NpcData data : npcs.values()) {
            String type = data.getNpcType() != null ? data.getNpcType() : "CUSTOM";
            p.sendMessage(ColorUtils.color(
                    "  &e" + data.getId() + " &7| &f" + data.getName() +
                    " &7| Тип: &b" + type +
                    " &7| Команда: &a" + (data.getCommand() != null ? data.getCommand() : "—") +
                    " &7| Скін: &b" + (data.getSkinName() != null ? data.getSkinName() : "стандарт")));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    public NpcData getNpcById(String id) {
        return npcs.values().stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null);
    }

    // ── Save / Load ───────────────────────────────────────────────────

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (NpcData d : npcs.values()) {
            String path = "npcs." + d.getId();
            cfg.set(path + ".name",     d.getName());
            cfg.set(path + ".stand",    d.getStandUuid().toString());
            cfg.set(path + ".owner",    d.getOwner().toString());
            cfg.set(path + ".command",  d.getCommand());
            cfg.set(path + ".skin",     d.getSkinName());
            cfg.set(path + ".type",     d.getNpcType());
            cfg.set(path + ".world",    d.getLocation().getWorld().getName());
            cfg.set(path + ".x",        d.getLocation().getX());
            cfg.set(path + ".y",        d.getLocation().getY());
            cfg.set(path + ".z",        d.getLocation().getZ());
        }
        try { cfg.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.isConfigurationSection("npcs")) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String id : cfg.getConfigurationSection("npcs").getKeys(false)) {
                String path = "npcs." + id;
                World w = Bukkit.getWorld(cfg.getString(path + ".world", "world"));
                if (w == null) continue;
                UUID standUuid = UUID.fromString(cfg.getString(path + ".stand", UUID.randomUUID().toString()));
                UUID owner     = UUID.fromString(cfg.getString(path + ".owner", UUID.randomUUID().toString()));
                Location loc = new Location(w,
                        cfg.getDouble(path + ".x"),
                        cfg.getDouble(path + ".y"),
                        cfg.getDouble(path + ".z"));
                String name    = cfg.getString(path + ".name", "NPC");
                String command = cfg.getString(path + ".command");
                String skin    = cfg.getString(path + ".skin");
                String type    = cfg.getString(path + ".type", "CUSTOM");

                NpcData data = new NpcData(id, standUuid, loc, owner, name);
                data.setCommand(command);
                data.setSkinName(skin);
                data.setNpcType(type);
                npcs.put(standUuid, data);
            }
        }, 40L);
    }

    public Collection<NpcData> getAll() { return npcs.values(); }
}
