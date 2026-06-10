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
                // /anpc create <ім'я> [mine|farm|forest]
                if (args.length < 2) { ColorUtils.msg(p, "&cВикористання: /anpc create <ім'я> [mine|farm|forest]"); return true; }
                String type = "custom";
                String name;
                // Якщо останній аргумент — тип
                String lastArg = args[args.length - 1].toLowerCase();
                if (args.length > 2 && List.of("mine","farm","forest","custom").contains(lastArg)) {
                    type = lastArg;
                    name = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                } else {
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
                if (args.length < 3) {
                    ColorUtils.msg(p, "&cВикористання: /anpc command <id> <команда>");
                    ColorUtils.msg(p, "&7{player} = замінить на ім'я гравця");
                    return true;
                }
                NpcData data = plugin.getNpcManager().getNpcById(args[1]);
                if (data == null) { ColorUtils.msg(p, "&cNPC не знайдено."); return true; }
                String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getNpcManager().setCommand(args[1], command);
                ColorUtils.msg(p, "&aКоманду встановлено: &e/" + command);
            }
            case "skin" -> {
                if (!perm(p, "auto.npc.skin")) return true;
                if (args.length < 3) { ColorUtils.msg(p, "&cВикористання: /anpc skin <id> <нік>"); return true; }
                if (plugin.getNpcManager().getNpcById(args[1]) == null) { ColorUtils.msg(p, "&cNPC не знайдено."); return true; }
                plugin.getNpcManager().setSkin(args[1], args[2]);
                ColorUtils.msg(p, "&aСкін встановлено на &e" + args[2]);
            }
            case "name" -> {
                if (!perm(p, "auto.npc.name")) return true;
                if (args.length < 3) { ColorUtils.msg(p, "&cВикористання: /anpc name <id> <назва>"); return true; }
                if (plugin.getNpcManager().getNpcById(args[1]) == null) { ColorUtils.msg(p, "&cNPC не знайдено."); return true; }
                String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getNpcManager().setName(args[1], newName);
                ColorUtils.msg(p, "&aНазву змінено на: &e" + newName);
            }
            default -> sendHelp(p);
        }
        return true;
    }

    private boolean perm(Player p, String node) {
        if (p.hasPermission(node) || p.hasPermission("auto.npc.admin") || p.hasPermission("auto.admin")) return true;
        ColorUtils.msg(p, "&cНемає права &e" + node);
        ColorUtils.msg(p, "&7LuckPerms: &f/lp user " + p.getName() + " permission set " + node + " true");
        return false;
    }

    private void sendHelp(Player p) {
        p.sendMessage(ColorUtils.color("&8━━━ &6NPC &8━━━"));
        if (p.hasPermission("auto.npc.create"))  p.sendMessage(ColorUtils.color("&e/anpc create <назва> [mine|farm|forest]"));
        if (p.hasPermission("auto.npc.delete"))  p.sendMessage(ColorUtils.color("&e/anpc delete <id>"));
        if (p.hasPermission("auto.npc.skin"))    p.sendMessage(ColorUtils.color("&e/anpc skin <id> <нік>"));
        if (p.hasPermission("auto.npc.command")) p.sendMessage(ColorUtils.color("&e/anpc command <id> <команда>"));
        if (p.hasPermission("auto.npc.name"))    p.sendMessage(ColorUtils.color("&e/anpc name <id> <назва>"));
        if (p.hasPermission("auto.npc.list"))    p.sendMessage(ColorUtils.color("&e/anpc list"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                      @NotNull String a, @NotNull String[] args) {
        if (!(s instanceof Player p)) return List.of();
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (p.hasPermission("auto.npc.create"))  list.add("create");
            if (p.hasPermission("auto.npc.delete"))  list.add("delete");
            if (p.hasPermission("auto.npc.skin"))    list.add("skin");
            if (p.hasPermission("auto.npc.command")) list.add("command");
            if (p.hasPermission("auto.npc.name"))    list.add("name");
            if (p.hasPermission("auto.npc.list"))    list.add("list");
            return list;
        }
        if (args.length == 2 && List.of("delete","skin","command","name").contains(args[0].toLowerCase())) {
            List<String> ids = new ArrayList<>();
            plugin.getNpcManager().getAll().forEach(n -> ids.add(n.getId()));
            return ids;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("create")) {
            return List.of("mine", "farm", "forest", "custom");
        }
        return List.of();
    }
}
