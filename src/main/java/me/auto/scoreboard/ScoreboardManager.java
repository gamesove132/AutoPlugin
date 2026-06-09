package me.auto.scoreboard;

import me.auto.AutoPlugin;
import me.auto.managers.MineData;
import me.auto.managers.ForestData;
import me.auto.managers.FarmData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class ScoreboardManager {

    private final AutoPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitRunnable ticker;

    public ScoreboardManager(AutoPlugin plugin) {
        this.plugin = plugin;
        startTicker();
    }

    public void showBoard(Player p) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        String title = plugin.getConfig().getString("scoreboard.title", "&6&lАвто-блоки");
        Objective obj = board.registerNewObjective("auto", Criteria.DUMMY,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
        updateBoard(p, board, obj);
    }

    public void removeBoard(Player p) {
        boards.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void updateBoard(Player p, Scoreboard board, Objective obj) {
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        String balance = String.format("%.1f", plugin.getEconomy().getBalance(p));

        MineData mine     = plugin.getMineManager().getPlayerMine(p);
        ForestData forest = plugin.getForestManager().getPlayerForest(p);
        FarmData farm     = plugin.getFarmManager().getPlayerFarm(p);

        // Clear old scores
        for (String entry : new HashSet<>(board.getEntries())) board.resetScores(entry);

        int score = lines.size();
        for (String line : lines) {
            String formatted = line
                .replace("{balance}",       balance)
                .replace("{mine_status}",   mine   != null ? mine.getName()   : "Немає")
                .replace("{forest_status}", forest != null ? forest.getName() : "Немає")
                .replace("{farm_status}",   farm   != null ? farm.getName()   : "Немає")
                .replace("{online}",        String.valueOf(Bukkit.getOnlinePlayers().size()));

            String colorized = net.md_5.bungee.api.ChatColor
                    .translateAlternateColorCodes('&', formatted);
            // Ensure unique entries by padding with invisible chars
            while (board.getEntries().contains(colorized))
                colorized += "§r";

            obj.getScore(colorized).setScore(score--);
        }
    }

    private void startTicker() {
        int ticks = plugin.getConfig().getInt("scoreboard.update-ticks", 20);
        ticker = new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Scoreboard board = boards.get(p.getUniqueId());
                    if (board == null) continue;
                    Objective obj = board.getObjective("auto");
                    if (obj == null) continue;
                    updateBoard(p, board, obj);
                }
            }
        };
        ticker.runTaskTimer(plugin, ticks, ticks);
    }
}
