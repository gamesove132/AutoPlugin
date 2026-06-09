package me.auto.npc;

import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/**
 * Simple NPC using ArmorStand + Player Head.
 * For full skin support a Citizens-based NPC would be needed,
 * but this works without extra dependencies.
 */
public class NpcManager {

    private final AutoPlugin plugin;
    private final Map<UUID, NpcData> npcs = new HashMap<>();           // stand UUID -> data
    private final Map<UUID, List<UUID>> waitingConfig = new HashMap<>(); // player UUID -> NPC UUID (config mode)
    private final File dataFile;
    private final NamespacedKey npcKey;

    public NpcManager(AutoPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.npcKey   = new NamespacedKey(plugin, "doors_npc_id");
        loadAll();
    }

    // ── Create ────────────────────────────────────────────────────────

    public void createNpc(Player creator, String name) {
        Location loc = creator.getLocation();
        World w = loc.getWorld();

        // Spawn invisible ArmorStand as base
        ArmorStand stand = w.spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setSmall(false);
            s.setArms(false);
            s.setBasePlate(false);
            s.setCustomName(ColorUtils.color(
                    plugin.getConfig().getString("npc.name-color", "&e") + name));
            s.setCustomNameVisible(plugin.getConfig().getBoolean("npc.show-name", true));
        });

        // Player head on top with default skin
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        stand.getEquipment().setHelmet(head);

        // Tag with NPC ID
        String id = UUID.randomUUID().toString().substring(0, 8);
        stand.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);

        NpcData data = new NpcData(id, stand.getUniqueId(), loc, creator.getUniqueId(), name);
        npcs.put(stand.getUniqueId(), data);
        saveAll();

        ColorUtils.msg(creator, "&aNPC &e" + name + " &aстворено!");
        ColorUtils.msg(creator, "&7Команди: &e/autonpc command " + id + " <команда>");
        ColorUtils.msg(creator, "&7Скін:     &e/autonpc skin " + id + " <нік_гравця>");
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
        // Use offline player profile for skin
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
        if (e != null) e.setCustomName(ColorUtils.color(
                plugin.getConfig().getString("npc.name-color", "&e") + newName));
        saveAll();
    }

    // ── Interact ──────────────────────────────────────────────────────

    public void onInteract(Player p, ArmorStand stand) {
        NpcData data = npcs.get(stand.getUniqueId());
        if (data == null) return;

        String cmd = data.getCommand();
        if (cmd == null || cmd.isEmpty()) {
            ColorUtils.msg(p, "&7NPC &e" + data.getName() + " &7[ID: " + data.getId() + "]");
            ColorUtils.msg(p, "&7Команда не налаштована. Адмін: &e/autonpc command " + data.getId() + " <команда>");
            return;
        }

        // Execute command as player
        String finalCmd = cmd.replace("{player}", p.getName());
        if (finalCmd.startsWith("/")) finalCmd = finalCmd.substring(1);
        p.performCommand(finalCmd);
    }

    // ── List ──────────────────────────────────────────────────────────

    public void listNpcs(Player p) {
        if (npcs.isEmpty()) { ColorUtils.msg(p, "&7NPC не створено."); return; }
        ColorUtils.msg(p, "&6=== NPC список ===");
        for (NpcData data : npcs.values()) {
            p.sendMessage(ColorUtils.color(
                    "  &e" + data.getId() + " &7| &f" + data.getName() +
                    " &7| Команда: &a" + (data.getCommand() != null ? data.getCommand() : "немає") +
                    " &7| Скін: &b" + (data.getSkinName() != null ? data.getSkinName() : "стандартний")));
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

                NpcData data = new NpcData(id, standUuid, loc, owner, name);
                data.setCommand(command);
                data.setSkinName(skin);
                npcs.put(standUuid, data);
            }
        }, 40L); // wait for worlds to load
    }

    public Collection<NpcData> getAll() { return npcs.values(); }
}
