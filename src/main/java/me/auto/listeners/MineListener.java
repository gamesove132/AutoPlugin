package me.auto.listeners;

import me.auto.AutoPlugin;
import me.auto.managers.MineData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class MineListener implements Listener {

    private final AutoPlugin plugin;

    public MineListener(AutoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        MineData mine = plugin.getMineManager().getMineAt(e.getBlock().getLocation());
        if (mine == null) return;

        // Забороняємо дроп предметів з шахти
        e.setDropItems(false);

        if (plugin.getMineManager().getPlayerMine(p) == null)
            plugin.getMineManager().enterMine(p, mine);

        double reward = plugin.getConfig().getDouble("mine.rewards." + e.getBlock().getType().name(), 0);
        if (reward > 0) {
            plugin.getEconomy().depositPlayer(p, reward);
            String name = e.getBlock().getType().name()
                    .replace("DEEPSLATE_","").replace("_ORE","").toLowerCase();
            plugin.getHudManager().sendActionBar(p,
                    "&a+$" + String.format("%.1f", reward) + " &7за &e" + name + " &7руду");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.5f);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Player p = e.getPlayer();
        if (plugin.getMineManager().getPlayerMine(p) == null) {
            MineData mine = plugin.getMineManager().getMineAt(p.getLocation());
            if (mine != null) plugin.getMineManager().enterMine(p, mine);
        }
    }
}
