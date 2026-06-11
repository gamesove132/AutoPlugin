package me.auto.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class ColorUtils {
    private ColorUtils() {}

    public static String color(String s) {
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', s);
    }

    public static Component component(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    public static void msg(CommandSender s, String msg) {
        s.sendMessage(color("&8[&6Auto&8] &r" + msg));
    }

    public static void actionBar(org.bukkit.entity.Player p, String msg) {
        p.sendActionBar(component(msg));
    }
}
