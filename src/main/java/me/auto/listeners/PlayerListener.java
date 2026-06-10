package me.auto.listeners;

import me.auto.AutoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public class PlayerListener implements Listener {
    private final AutoPlugin plugin;
    public PlayerListener(AutoPlugin p) { this.plugin = p; }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.getMineManager().leaveMine(p);
        plugin.getForestManager().leaveForest(p);
        plugin.getFarmManager().leaveFarm(p);
        plugin.getScoreboardManager().removeBoard(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // НЕ показуємо скорборд при вході в лоббі — конфлікт з TAB
        // Скорборд з'явиться тільки коли гравець зайде в зону
    }
}
