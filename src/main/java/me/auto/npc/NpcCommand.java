package me.auto.npc;

import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class NpcCommand implements CommandExecutor, TabCompleter {
    private final AutoPlugin plugin;
    public NpcCommand(AutoPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { ColorUtils.msg(sender, "&cТільки для гравців."); return true; }
        if (args.length == 0) { sendHelp(p); return true; }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!perm(p, "auto.npc.create")) return true;
                if (args.length < 2) { ColorUtils.msg(p, "&cВикористання: /anpc create <назва> [mine|farm|forest]"); return true; }
                String lastArg = args[args.length - 1].toLowerCase();
                String type; String name;
                if (args.length > 2 && List.of("mine","farm","forest","custom").contains(lastArg)) {
                    type = lastArg;
                    name = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                } else {
                    type = "custom";
                    name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                }
                plugin.getNpcManager().createNpc(p, name, type);
            }
            case "delete" -> {
                if (!perm(p, "auto.npc.delete")) return true;
                if (args.length < 2) { ColorUtils.msg(p, "&cВкажіть ID."); return true; }
                ColorUtils.msg(p, plugin.getNpcManager().deleteNpc(args[1]) ? "&aВидалено!" : "&cНе знайдено.");
            }
            case "list" -> {
                if (!perm(p, "auto.npc.list")) return true;
                plugin.getNpcManager().listNpcs(p);
            }
            case "command" -> {
                if (!perm(p, "auto.npc.command")) return true;
                if (args.length < 3) { ColorUtils.msg(p, "&cВикористання: /anpc command <id> <команда>"); return true; }
                if (plugin.getNpcManager().getNpcById(args[1]) == null) { ColorUtils.msg(p, "&cNPC не знайдено."); return true; }
                String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getNpcManager().setCommand(args[1], command);
                ColorUtils.msg(p, "&aКоманду встановлено: &e" + command);
            }
            case "skin" -> {
                if (!perm(p, "auto.npc.skin")) return true;
                if (args.length < 3) { ColorUtils.msg(p, "&cВикористання: /anpc skin <id> <нік гравця>"); return true; }
                plugin.getNpcManager().setSkin(p, args[1], args[2]);
            }
            case "name" -> {
                if (!perm(p, "auto.npc.name")) return true;
                if (args.length < 3) { ColorUtils.msg(p, "&cВикористання: /anpc name <id> <назва>"); return true; }
                if (plugin.getNpcManager().getNpcById(args[1]) == null) { ColorUtils.msg(p, "&cNPC не знайдено."); return true; }
                plugin.getNpcManager().setName(args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                ColorUtils.msg(p, "&aНазву змінено!");
            }
            default -> sendHelp(p);
        }
        return true;
    }

    private boolean perm(Player p, String node) {
        if (p.hasPermission(node) || p.hasPermission("auto.npc.admin") || p.hasPermission("auto.admin")) return true;
        ColorUtils.msg(p, "&cНемає права &e" + node); return false;
    }

    private void sendHelp(Player p) {
        p.sendMessage(ColorUtils.color("&8━━━ &6NPC &8━━━"));
        p.sendMessage(ColorUtils.color("&e/anpc create <назва> [mine|farm|forest]"));
        p.sendMessage(ColorUtils.color("&e/anpc delete <id>"));
        p.sendMessage(ColorUtils.color("&e/anpc skin <id> <нік> &7— встановити скін"));
        p.sendMessage(ColorUtils.color("&e/anpc command <id> <команда> &7— для custom типу"));
        p.sendMessage(ColorUtils.color("&e/anpc name <id> <назва>"));
        p.sendMessage(ColorUtils.color("&e/anpc list"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                      @NotNull String a, @NotNull String[] args) {
        if (!(s instanceof Player)) return List.of();
        if (args.length == 1) return List.of("create","delete","list","command","skin","name");
        if (args.length == 2 && List.of("delete","skin","command","name").contains(args[0].toLowerCase())) {
            List<String> ids = new ArrayList<>();
            plugin.getNpcManager().getAll().forEach(n -> ids.add(n.getId()));
            return ids;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("create"))
            return List.of("mine","farm","forest","custom");
        return List.of();
    }
}
