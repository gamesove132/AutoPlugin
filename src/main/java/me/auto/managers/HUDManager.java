package me.auto.managers;

import me.auto.AutoPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

public class HUDManager {
    private final AutoPlugin plugin;
    public HUDManager(AutoPlugin plugin) { this.plugin = plugin; }

    public void sendActionBar(Player p, String msg) {
        p.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }
}
