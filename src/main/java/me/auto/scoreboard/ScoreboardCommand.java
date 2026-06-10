package me.auto.scoreboard;
import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class ScoreboardCommand implements CommandExecutor, TabCompleter {
    private final AutoPlugin plugin;
    public ScoreboardCommand(AutoPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) { ColorUtils.msg(sender, "&cТільки для гравців."); return true; }
        if (args.length == 0) { sendHelp(p); return true; }
        switch (args[0].toLowerCase()) {
            case "show" -> {
                if (!perm(p, "auto.scoreboard.show")) return true;
                plugin.getScoreboardManager().showBoard(p);
                ColorUtils.msg(p, "&aСкорборд увімкнено.");
            }
            case "hide" -> {
                if (!perm(p, "auto.scoreboard.hide")) return true;
                plugin.getScoreboardManager().removeBoard(p);
                ColorUtils.msg(p, "&7Скорборд вимкнено.");
            }
            case "reload" -> {
                if (!perm(p, "auto.scoreboard.reload")) return true;
                plugin.reloadConfig();
                ColorUtils.msg(p, "&aКонфіг перезавантажено.");
            }
            default -> sendHelp(p);
        }
        return true;
    }

    private boolean perm(Player p, String node) {
        if (p.hasPermission(node) || p.hasPermission("auto.scoreboard.admin") || p.hasPermission("auto.admin")) return true;
        ColorUtils.msg(p, "&cНемає права &e" + node);
        ColorUtils.msg(p, "&7LuckPerms: &f/lp user " + p.getName() + " permission set " + node + " true");
        return false;
    }

    private void sendHelp(Player p) {
        p.sendMessage(ColorUtils.color("&8━━━ &6Скорборд &8━━━"));
        if (p.hasPermission("auto.scoreboard.show"))   p.sendMessage(ColorUtils.color("&e/asb show &7— показати"));
        if (p.hasPermission("auto.scoreboard.hide"))   p.sendMessage(ColorUtils.color("&e/asb hide &7— сховати"));
        if (p.hasPermission("auto.scoreboard.reload")) p.sendMessage(ColorUtils.color("&e/asb reload &7— перезавантажити"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                      @NotNull String a, @NotNull String[] args) {
        if (!(s instanceof Player p)) return List.of();
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (p.hasPermission("auto.scoreboard.show"))   list.add("show");
            if (p.hasPermission("auto.scoreboard.hide"))   list.add("hide");
            if (p.hasPermission("auto.scoreboard.reload")) list.add("reload");
            return list;
        }
        return List.of();
    }
}
