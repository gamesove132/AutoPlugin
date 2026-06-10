package me.auto.mine;
import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class MineCommand implements CommandExecutor, TabCompleter {
    private final AutoPlugin plugin;
    public MineCommand(AutoPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { ColorUtils.msg(sender, "&cТільки для гравців."); return true; }
        if (args.length == 0) { sendHelp(p, label); return true; }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!perm(p, "auto.mine.create")) return true;
                plugin.getMineManager().createMine(p, args.length > 1 ? args[1] : "Mine");
            }
            case "delete" -> {
                if (!perm(p, "auto.mine.delete")) return true;
                if (args.length < 2) { ColorUtils.msg(p, "&cВкажіть ID."); return true; }
                ColorUtils.msg(p, plugin.getMineManager().deleteMine(args[1]) ? "&aВидалено!" : "&cНе знайдено.");
            }
            case "list" -> {
                if (!perm(p, "auto.mine.list")) return true;
                plugin.getMineManager().getAll().forEach(b ->
                    p.sendMessage(ColorUtils.color("  &e" + b.getId() + " &7| &f" + b.getName())));
            }
            case "reload" -> {
                if (!perm(p, "auto.mine.reload")) return true;
                plugin.reloadConfig();
                ColorUtils.msg(p, "&aПерезавантажено.");
            }
            default -> sendHelp(p, label);
        }
        return true;
    }

    private boolean perm(Player p, String node) {
        if (p.hasPermission(node) || p.hasPermission("auto.mine.admin") || p.hasPermission("auto.admin")) return true;
        ColorUtils.msg(p, "&cНемає права &e" + node);
        ColorUtils.msg(p, "&7LuckPerms: &f/lp user " + p.getName() + " permission set " + node + " true");
        return false;
    }

    private void sendHelp(Player p, String lbl) {
        p.sendMessage(ColorUtils.color("&8━━━ &6Авто-шахта &8━━━"));
        if (p.hasPermission("auto.mine.create"))  p.sendMessage(ColorUtils.color("&e/" + lbl + " create <назва>"));
        if (p.hasPermission("auto.mine.delete"))  p.sendMessage(ColorUtils.color("&e/" + lbl + " delete <id>"));
        if (p.hasPermission("auto.mine.list"))    p.sendMessage(ColorUtils.color("&e/" + lbl + " list"));
        if (p.hasPermission("auto.mine.reload"))  p.sendMessage(ColorUtils.color("&e/" + lbl + " reload"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                      @NotNull String a, @NotNull String[] args) {
        if (!(s instanceof Player p)) return List.of();
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (p.hasPermission("auto.mine.create")) list.add("create");
            if (p.hasPermission("auto.mine.delete")) list.add("delete");
            if (p.hasPermission("auto.mine.list"))   list.add("list");
            if (p.hasPermission("auto.mine.reload")) list.add("reload");
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            List<String> ids = new ArrayList<>();
            plugin.getMineManager().getAll().forEach(b -> ids.add(b.getId()));
            return ids;
        }
        return List.of();
    }
}
