package me.auto.farm;
import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class FarmCommand implements CommandExecutor, TabCompleter {
    private final AutoPlugin plugin;
    public FarmCommand(AutoPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { ColorUtils.msg(sender, "&cТільки для гравців."); return true; }
        if (args.length == 0) { sendHelp(p, label); return true; }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!perm(p, "auto.farm.create")) return true;
                plugin.getFarmManager().createFarm(p, args.length > 1 ? args[1] : "Farm");
            }
            case "delete" -> {
                if (!perm(p, "auto.farm.delete")) return true;
                if (args.length < 2) { ColorUtils.msg(p, "&cВкажіть ID."); return true; }
                ColorUtils.msg(p, plugin.getFarmManager().deleteFarm(args[1]) ? "&aВидалено!" : "&cНе знайдено.");
            }
            case "list" -> {
                if (!perm(p, "auto.farm.list")) return true;
                plugin.getFarmManager().getAll().forEach(b ->
                    p.sendMessage(ColorUtils.color("  &e" + b.getId() + " &7| &f" + b.getName())));
            }
            case "reload" -> {
                if (!perm(p, "auto.farm.reload")) return true;
                plugin.reloadConfig(); ColorUtils.msg(p, "&aПерезавантажено.");
            }
            default -> sendHelp(p, label);
        }
        return true;
    }

    private boolean perm(Player p, String node) {
        if (p.hasPermission(node) || p.hasPermission("auto.farm.admin") || p.hasPermission("auto.admin")) return true;
        ColorUtils.msg(p, "&cНемає права &e" + node);
        ColorUtils.msg(p, "&7LuckPerms: &f/lp user " + p.getName() + " permission set " + node + " true");
        return false;
    }

    private void sendHelp(Player p, String lbl) {
        p.sendMessage(ColorUtils.color("&8━━━ &6Авто-ферма &8━━━"));
        if (p.hasPermission("auto.farm.create")) p.sendMessage(ColorUtils.color("&e/" + lbl + " create <назва>"));
        if (p.hasPermission("auto.farm.delete")) p.sendMessage(ColorUtils.color("&e/" + lbl + " delete <id>"));
        if (p.hasPermission("auto.farm.list"))   p.sendMessage(ColorUtils.color("&e/" + lbl + " list"));
        if (p.hasPermission("auto.farm.reload")) p.sendMessage(ColorUtils.color("&e/" + lbl + " reload"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                      @NotNull String a, @NotNull String[] args) {
        if (!(s instanceof Player p)) return List.of();
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (p.hasPermission("auto.farm.create")) list.add("create");
            if (p.hasPermission("auto.farm.delete")) list.add("delete");
            if (p.hasPermission("auto.farm.list"))   list.add("list");
            if (p.hasPermission("auto.farm.reload")) list.add("reload");
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            List<String> ids = new ArrayList<>();
            plugin.getFarmManager().getAll().forEach(b -> ids.add(b.getId()));
            return ids;
        }
        return List.of();
    }
}
