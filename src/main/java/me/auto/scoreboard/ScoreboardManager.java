package me.auto.scoreboard;

import me.auto.AutoPlugin;
import me.auto.managers.MineData;
import me.auto.managers.ForestData;
import me.auto.managers.FarmData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * Скорборд:
 *  - В лоббі: НЕ показується взагалі (вирішує конфлікт з TAB)
 *  - В шахті:  показується Mine-скорборд з цінами руд
 *  - На фермі: показується Farm-скорборд
 *  - В лісі:   показується Forest-скорборд
 *  - Вихід із зони → повертає гравцю mainScoreboard (лоббі = нічого)
 */
public class ScoreboardManager {

    private final AutoPlugin plugin;
    // Зберігаємо лише UUID гравців у яких зараз є активна дошка
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitRunnable ticker;

    public ScoreboardManager(AutoPlugin plugin) {
        this.plugin = plugin;
        startTicker();
    }

    // ── Публічні методи ───────────────────────────────────────────────

    /** Лоббі — знімаємо скорборд повністю */
    public void showLobbyBoard(Player p) {
        boards.remove(p.getUniqueId());
        // Повертаємо стандартний серверний scoreboard (зазвичай порожній)
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Шахта — свій скорборд з цінами руд */
    public void showMineBoard(Player p) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        Scoreboard board = buildBoard(p, "mine");
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
    }

    /** Ферма */
    public void showFarmBoard(Player p) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        Scoreboard board = buildBoard(p, "farm");
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
    }

    /** Ліс */
    public void showForestBoard(Player p) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        Scoreboard board = buildBoard(p, "forest");
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
    }

    /** Застаріла сумісність — з PlayerListener при join тепер НЕ викликається */
    public void showBoard(Player p) {
        // Нічого — скорборд показується тільки в зонах
    }

    public void removeBoard(Player p) {
        showLobbyBoard(p);
    }

    // ── Будуємо Scoreboard ────────────────────────────────────────────

    private Scoreboard buildBoard(Player p, String zone) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        String title = getTitle(zone);
        Objective obj = board.registerNewObjective("auto", Criteria.DUMMY,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        fillBoard(p, board, obj, zone);
        return board;
    }

    private String getTitle(String zone) {
        return switch (zone) {
            case "mine"   -> "&8⛏ &6&lАвто-шахта";
            case "farm"   -> "&2🌾 &a&lАвто-ферма";
            case "forest" -> "&2🪵 &a&lАвто-ліс";
            default       -> "&6&lАвто-блоки";
        };
    }

    private void fillBoard(Player p, Scoreboard board, Objective obj, String zone) {
        List<String> lines = getLines(p, zone);
        // Очищаємо старе
        for (String entry : new HashSet<>(board.getEntries())) board.resetScores(entry);
        int score = lines.size();
        for (String line : lines) {
            String colorized = net.md_5.bungee.api.ChatColor
                    .translateAlternateColorCodes('&', line);
            while (board.getEntries().contains(colorized)) colorized += "§r";
            obj.getScore(colorized).setScore(score--);
        }
    }

    private List<String> getLines(Player p, String zone) {
        String balance = String.format("%.1f", plugin.getEconomy().getBalance(p));
        List<String> lines = new ArrayList<>();

        switch (zone) {
            case "mine" -> {
                lines.add("&7Баланс: &e$" + balance);
                lines.add("&8──────────────");
                lines.add("&6Ціни руд:");
                lines.add("  &bАлмаз:   &f$100");
                lines.add("  &dЛазурит: &f$50");
                lines.add("  &eЗолото:  &f$25");
                lines.add("  &7Залізо:  &f$20");
                lines.add("  &cРедстоун:&f$45");
                lines.add("  &8Вугілля: &f$10");
                lines.add("&8──────────────");
                MineData mine = plugin.getMineManager().getPlayerMine(p);
                lines.add("&7Шахта: &a" + (mine != null ? mine.getName() : "?"));
            }
            case "farm" -> {
                lines.add("&7Баланс: &e$" + balance);
                lines.add("&8──────────────");
                lines.add("&aЦіни врожаю:");
                lines.add("  &eПшениця:   &f$2");
                lines.add("  &eМорква:    &f$2.5");
                lines.add("  &eКартопля:  &f$2");
                lines.add("  &eБуряк:     &f$3");
                lines.add("  &eКавун:     &f$4");
                lines.add("  &eГарбуз:    &f$4");
                lines.add("&8──────────────");
                FarmData farm = plugin.getFarmManager().getPlayerFarm(p);
                lines.add("&7Ферма: &a" + (farm != null ? farm.getName() : "?"));
            }
            case "forest" -> {
                lines.add("&7Баланс: &e$" + balance);
                lines.add("&8──────────────");
                lines.add("&2Ціни деревини:");
                lines.add("  &7Дуб:    &f$3");
                lines.add("  &fБереза: &f$3");
                lines.add("  &9Ялина:  &f$3.5");
                lines.add("&8──────────────");
                ForestData forest = plugin.getForestManager().getPlayerForest(p);
                lines.add("&7Ліс: &a" + (forest != null ? forest.getName() : "?"));
            }
            default -> {
                lines.add("&7Баланс: &e$" + balance);
                lines.add("&7Онлайн: &e" + Bukkit.getOnlinePlayers().size());
            }
        }
        return lines;
    }

    // ── Ticker ────────────────────────────────────────────────────────

    private void startTicker() {
        int ticks = plugin.getConfig().getInt("scoreboard.update-ticks", 20);
        ticker = new BukkitRunnable() {
            @Override public void run() {
                for (Map.Entry<UUID, Scoreboard> entry : new HashMap<>(boards).entrySet()) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null) continue;
                    Scoreboard board = entry.getValue();
                    Objective obj = board.getObjective("auto");
                    if (obj == null) continue;

                    // Визначаємо в якій зоні гравець
                    String zone = "lobby";
                    if (plugin.getMineManager().getPlayerMine(p) != null)     zone = "mine";
                    else if (plugin.getFarmManager().getPlayerFarm(p) != null) zone = "farm";
                    else if (plugin.getForestManager().getPlayerForest(p) != null) zone = "forest";

                    fillBoard(p, board, obj, zone);
                }
            }
        };
        ticker.runTaskTimer(plugin, ticks, ticks);
    }
}
