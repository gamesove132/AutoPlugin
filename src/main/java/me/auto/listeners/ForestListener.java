package me.auto.listeners;

import me.auto.AutoPlugin;
import me.auto.managers.ForestData;
import me.auto.managers.ForestManager;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class ForestListener implements Listener {
    private final AutoPlugin plugin;
    public ForestListener(AutoPlugin p) { this.plugin = p; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!b.getType().name().endsWith("_LOG") && !b.getType().name().endsWith("_WOOD")) return;

        ForestData forest = plugin.getForestManager().getForestAt(b.getLocation());
        if (forest == null) return;

        if (!ForestManager.isForestAxe(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            plugin.getHudManager().sendActionBar(p, "&cВикористовуй &a🪓 Лісорубську сокиру!");
            return;
        }

        e.setCancelled(true);
        e.setDropItems(false);

        if (plugin.getForestManager().getPlayerForest(p) == null)
            plugin.getForestManager().enterForest(p, forest);

        // onChop повертає true якщо дерево зрубано
        boolean chopped = plugin.getForestManager().onChop(p, b, forest);
        if (chopped) {
            String matName = b.getType().name().replace("_LOG","").toLowerCase();
            double reward  = plugin.getConfig().getDouble("forest.rewards." + b.getType().name(), 3.0);
            plugin.getNpcManager().addPendingForest(p.getUniqueId(), reward);
            double total = plugin.getNpcManager().getPending(p.getUniqueId(), "forest");
            plugin.getHudManager().sendActionBar(p,
                    "&a+" + reward + " &7(" + matName + ") &8| &7Накоп: &e$" + String.format("%.1f", total));
            p.playSound(p.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 1f);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Player p = e.getPlayer();
        if (plugin.getForestManager().getPlayerForest(p) == null) {
            ForestData f = plugin.getForestManager().getForestAt(p.getLocation());
            if (f != null) plugin.getForestManager().enterForest(p, f);
        }
    }
}
