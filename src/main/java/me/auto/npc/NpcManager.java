package me.auto.npc;

import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;

import java.io.File;
import java.util.*;

/**
 * NPC з типами:
 *   MINE   — продає накопичений баланс шахти (якщо відійшов без продажу → губить)
 *   FARM   — продає накопичений баланс ферми
 *   FOREST — продає накопичений баланс лісу
 *   CUSTOM — звичайна команда
 *
 * Механіка продажу:
 *   Гравець заходить в зону → накопичує "pending balance" (тимчасовий)
 *   Підходить до NPC → продає → гроші перекладаються на основний баланс
 *   Виходить із зони БЕЗ NPC → pending губиться
 */
public class NpcManager {

    private final AutoPlugin plugin;
    private final Map<UUID, NpcData> npcs = new HashMap<>();
    // UUID гравця → pending balance (зароблене але не продане)
    private final Map<UUID, Double> pendingMine   = new HashMap<>();
    private final Map<UUID, Double> pendingFarm   = new HashMap<>();
    private final Map<UUID, Double> pendingForest = new HashMap<>();
    private final File dataFile;
    private final NamespacedKey npcKey;

    public NpcManager(AutoPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.npcKey   = new NamespacedKey(plugin, "auto_npc_id");
        loadAll();
    }

    // ── Pending balance API (викликається з менеджерів) ───────────────

    public void addPendingMine(UUID uid, double amount) {
        pendingMine.merge(uid, amount, Double::sum);
    }
    public void addPendingFarm(UUID uid, double amount) {
        pendingFarm.merge(uid, amount, Double::sum);
    }
    public void addPendingForest(UUID uid, double amount) {
        pendingForest.merge(uid, amount, Double::sum);
    }

    /** Гравець вийшов із зони без продажу → губить накопичене */
    public void losePending(UUID uid, String zone) {
        Player p = Bukkit.getPlayer(uid);
        Map<UUID, Double> map = getPendingMap(zone);
        double lost = map.getOrDefault(uid, 0.0);
        map.remove(uid);
        if (p != null && lost > 0) {
            ColorUtils.msg(p, "&cВи не продали товар NPC! Втрачено &e$" + String.format("%.1f", lost));
        }
    }

    /** Гравець продає NPC */
    public void sellPending(Player p, String zone) {
        Map<UUID, Double> map = getPendingMap(zone);
        double amount = map.getOrDefault(p.getUniqueId(), 0.0);
        if (amount <= 0) {
            ColorUtils.msg(p, "&7Нічого продавати — заробіток &e$0.");
            return;
        }
        map.remove(p.getUniqueId());
        plugin.getEconomy().depositPlayer(p, amount);
        double newBal = plugin.getEconomy().getBalance(p);
        ColorUtils.msg(p, "&aПродано! Отримано &e$" + String.format("%.2f", amount));
        ColorUtils.msg(p, "&7Баланс: &e$" + String.format("%.2f", newBal));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    public double getPending(UUID uid, String zone) {
        return getPendingMap(zone).getOrDefault(uid, 0.0);
    }

    private Map<UUID, Double> getPendingMap(String zone) {
        return switch (zone.toLowerCase()) {
            case "farm"   -> pendingFarm;
            case "forest" -> pendingForest;
            default       -> pendingMine;
        };
    }

    // ── Create / Delete ───────────────────────────────────────────────

    public void createNpc(Player creator, String name, String type) {
        World w = creator.getWorld();
        ArmorStand stand = w.spawn(creator.getLocation(), ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setSmall(false);
            s.setArms(false);
            s.setBasePlate(false);
            s.setCustomNameVisible(true);
            s.setCustomName(ChatColor.translateAlternateColorCodes('&',
                    "&e" + getNpcPrefix(type) + name));
        });

        String id = UUID.randomUUID().toString().substring(0, 8);
        stand.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);

        NpcData data = new NpcData(id, stand.getUniqueId(), creator.getLocation(), creator.getUniqueId(), name);
        data.setNpcType(type);
        npcs.put(stand.getUniqueId(), data);
        saveAll();

        ColorUtils.msg(creator, "&aNPC &e" + name + " &aстворено! Тип: &f" + type.toUpperCase() + " &7| ID: &f" + id);
        if (type.equalsIgnoreCase("custom"))
            ColorUtils.msg(creator, "&7Встанови команду: &e/anpc command " + id + " <команда>");
        else
            ColorUtils.msg(creator, "&7Щоб встановити скін: &e/anpc skin " + id + " <нік гравця>");
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

    /**
     * Встановлює скін через UUID гравця (офлайн-гравці теж підтримуються).
     * Шукає UUID через Bukkit або OfflinePlayer.
     */
    public void setSkin(Player admin, String npcId, String playerName) {
        NpcData data = getNpcById(npcId);
        if (data == null) { ColorUtils.msg(admin, "&cNPC не знайдено."); return; }
        Entity e = Bukkit.getEntity(data.getStandUuid());
        if (!(e instanceof ArmorStand stand)) { ColorUtils.msg(admin, "&cStand не знайдено."); return; }

        // Шукаємо офлайн гравця щоб отримати UUID і скін
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        PlayerProfile profile = Bukkit.createPlayerProfile(op.getUniqueId(), playerName);
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta skull = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        skull.setOwnerProfile(profile);
        head.setItemMeta(skull);
        stand.getEquipment().setHelmet(head);

        data.setSkinName(playerName);
        saveAll();
        ColorUtils.msg(admin, "&aСкін NPC встановлено на &e" + playerName);
    }

    public void setName(String npcId, String newName) {
        NpcData data = getNpcById(npcId);
        if (data == null) return;
        data.setName(newName);
        Entity e = Bukkit.getEntity(data.getStandUuid());
        if (e != null) e.setCustomName(ChatColor.translateAlternateColorCodes('&',
                "&e" + getNpcPrefix(data.getNpcType()) + newName));
        saveAll();
    }

    // ── Interact ──────────────────────────────────────────────────────

    public void onInteract(Player p, ArmorStand stand) {
        NpcData data = npcs.get(stand.getUniqueId());
        if (data == null) return;

        String type = data.getNpcType() != null ? data.getNpcType().toUpperCase() : "CUSTOM";

        switch (type) {
            case "MINE"   -> handleZoneNpc(p, data, "mine");
            case "FARM"   -> handleZoneNpc(p, data, "farm");
            case "FOREST" -> handleZoneNpc(p, data, "forest");
            default       -> handleCustomNpc(p, data);
        }
    }

    private void handleZoneNpc(Player p, NpcData data, String zone) {
        double pending = getPending(p.getUniqueId(), zone);
        String zoneName = switch (zone) {
            case "farm"   -> "ферми";
            case "forest" -> "лісу";
            default       -> "шахти";
        };
        if (pending <= 0) {
            ColorUtils.msg(p, "&e" + data.getName() + "&7: Нічого продавати. Спочатку заробіть в " + zoneName + "!");
            return;
        }
        ColorUtils.msg(p, "&e" + data.getName() + "&7: Продаємо товар " + zoneName + " на &e$" +
                String.format("%.2f", pending) + "&7...");
        sellPending(p, zone);
    }

    private void handleCustomNpc(Player p, NpcData data) {
        String cmd = data.getCommand();
        if (cmd == null || cmd.isEmpty()) {
            ColorUtils.msg(p, "&7NPC &e" + data.getName() + " &7[&f" + data.getId() + "&7] — команда не встановлена.");
            return;
        }
        String finalCmd = cmd.replace("{player}", p.getName());
        if (finalCmd.startsWith("/")) finalCmd = finalCmd.substring(1);
        p.performCommand(finalCmd);
    }

    private String getNpcPrefix(String type) {
        if (type == null) return "";
        return switch (type.toUpperCase()) {
            case "MINE"   -> "⛏ ";
            case "FARM"   -> "🌾 ";
            case "FOREST" -> "🪵 ";
            default       -> "";
        };
    }

    // ── List ──────────────────────────────────────────────────────────

    public void listNpcs(Player p) {
        if (npcs.isEmpty()) { ColorUtils.msg(p, "&7NPC немає."); return; }
        ColorUtils.msg(p, "&6=== NPC список ===");
        for (NpcData d : npcs.values()) {
            p.sendMessage(ColorUtils.color(
                    "  &e" + d.getId() + " &7| &f" + d.getName() +
                    " &7| Тип: &b" + (d.getNpcType() != null ? d.getNpcType() : "custom") +
                    (d.getCommand() != null ? " &7| Команда: &a" + d.getCommand() : "") +
                    (d.getSkinName() != null ? " &7| Скін: &d" + d.getSkinName() : "")));
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
            cfg.set(path + ".name",    d.getName());
            cfg.set(path + ".stand",   d.getStandUuid().toString());
            cfg.set(path + ".owner",   d.getOwner().toString());
            cfg.set(path + ".command", d.getCommand());
            cfg.set(path + ".skin",    d.getSkinName());
            cfg.set(path + ".type",    d.getNpcType());
            cfg.set(path + ".world",   d.getLocation().getWorld().getName());
            cfg.set(path + ".x",       d.getLocation().getX());
            cfg.set(path + ".y",       d.getLocation().getY());
            cfg.set(path + ".z",       d.getLocation().getZ());
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
                Location loc   = new Location(w, cfg.getDouble(path+".x"), cfg.getDouble(path+".y"), cfg.getDouble(path+".z"));
                NpcData data   = new NpcData(id, standUuid, loc, owner, cfg.getString(path + ".name", "NPC"));
                data.setCommand(cfg.getString(path + ".command"));
                data.setSkinName(cfg.getString(path + ".skin"));
                data.setNpcType(cfg.getString(path + ".type", "custom"));
                npcs.put(standUuid, data);
            }
        }, 40L);
    }

    public Collection<NpcData> getAll() { return npcs.values(); }
}
