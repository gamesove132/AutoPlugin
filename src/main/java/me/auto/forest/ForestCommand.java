package me.auto.forest;

import me.auto.AutoPlugin;
import me.auto.selection.SelectionListener;
import me.auto.utils.ColorUtils;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class ForestCommand implements CommandExecutor, TabCompleter {
    private final AutoPlugin plugin;
    public ForestCommand(AutoPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { ColorUtils.msg(sender, "&cТільки для гравців."); return true; }
        if (args.length == 0) { sendHelp(p, label); return true; }
        switch (args[0].toLowerCase()) {
            case "wand" -> {
                if (!perm(p, "auto.forest.create")) return true;
                p.getInventory().addItem(SelectionListener.createWand());
                p.updateInventory();
                ColorUtils.msg(p, "&aОтримано &e⚒ Топор Виділення!");
                ColorUtils.msg(p, "&7ЛКМ = точка 1 &8| &7ПКМ = точка 2");
                ColorUtils.msg(p, "&7Потім: &e/autoforest create <назва>");
            }
            case "create" -> {
                if (!perm(p, "auto.forest.create")) return true;
                String name = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Ліс";
                plugin.getForestManager().createForestFromSelection(p, name);
            }
            case "delete" -> {
                if (!perm(p, "auto.forest.delete")) return true;
                if (args.length < 2) { ColorUtils.msg(p, "&cВкажіть ID."); return true; }
                ColorUtils.msg(p, plugin.getForestManager().deleteForest(args[1]) ? "&aВидалено!" : "&cНе знайдено.");
            }
            case "list" -> {
                if (!perm(p, "auto.forest.list")) return true;
                if (plugin.getForestManager().getAll().isEmpty()) { ColorUtils.msg(p, "&7Лісів немає."); return true; }
                plugin.getForestManager().getAll().forEach(b ->
                    p.sendMessage(ColorUtils.color("  &e" + b.getId() + " &7| &f" + b.getName() +
                            " &7| &b" + b.getSizeX() + "x" + b.getSizeZ())));
            }
            case "reload" -> {
                if (!perm(p, "auto.forest.reload")) return true;
                plugin.reloadConfig(); ColorUtils.msg(p, "&aПерезавантажено.");
            }
            default -> sendHelp(p, label);
        }
        return true;
    }

    private boolean perm(Player p, String node) {
        if (p.hasPermission(node) || p.hasPermission("auto.forest.admin") || p.hasPermission("auto.admin")) return true;
        ColorUtils.msg(p, "&cНемає права &e" + node); return false;
    }

    private void sendHelp(Player p, String lbl) {
        p.sendMessage(ColorUtils.color("&8━━━ &6Авто-ліс &8━━━"));
        p.sendMessage(ColorUtils.color("&e/" + lbl + " wand &7— топор виділення"));
        if (p.hasPermission("auto.forest.create")) p.sendMessage(ColorUtils.color("&e/" + lbl + " create <назва>"));
        if (p.hasPermission("auto.forest.delete")) p.sendMessage(ColorUtils.color("&e/" + lbl + " delete <id>"));
        if (p.hasPermission("auto.forest.list"))   p.sendMessage(ColorUtils.color("&e/" + lbl + " list"));
        if (p.hasPermission("auto.forest.reload")) p.sendMessage(ColorUtils.color("&e/" + lbl + " reload"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                      @NotNull String a, @NotNull String[] args) {
        if (!(s instanceof Player p)) return List.of();
        if (args.length == 1) {
            List<String> list = new ArrayList<>(List.of("wand"));
            if (p.hasPermission("auto.forest.create")) list.add("create");
            if (p.hasPermission("auto.forest.delete")) list.add("delete");
            if (p.hasPermission("auto.forest.list"))   list.add("list");
            if (p.hasPermission("auto.forest.reload")) list.add("reload");
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            List<String> ids = new ArrayList<>();
            plugin.getForestManager().getAll().forEach(b -> ids.add(b.getId()));
            return ids;
        }
        return List.of();
    }
}
