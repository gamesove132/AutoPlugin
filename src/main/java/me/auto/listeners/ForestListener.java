package me.auto.listeners;
import me.auto.AutoPlugin;
import me.auto.managers.ForestData;
import org.bukkit.Material;
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
        e.setCancelled(true);
        e.setDropItems(false);
        if (plugin.getForestManager().getPlayerForest(p) == null)
            plugin.getForestManager().enterForest(p, forest);
        plugin.getForestManager().onChop(p, b, forest);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (plugin.getForestManager().getPlayerForest(p) == null) {
            ForestData f = plugin.getForestManager().getForestAt(p.getLocation());
            if (f != null) plugin.getForestManager().enterForest(p, f);
        }
    }
}
