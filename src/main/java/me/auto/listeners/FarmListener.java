package me.auto.listeners;
import me.auto.AutoPlugin;
import me.auto.managers.FarmData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class FarmListener implements Listener {
    private final AutoPlugin plugin;
    public FarmListener(AutoPlugin p) { this.plugin = p; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if (!isCrop(b.getType())) return;
        FarmData farm = plugin.getFarmManager().getFarmAt(b.getLocation());
        if (farm == null) return;
        e.setCancelled(true);
        e.setDropItems(false);
        if (plugin.getFarmManager().getPlayerFarm(p) == null)
            plugin.getFarmManager().enterFarm(p, farm);
        plugin.getFarmManager().onHarvest(p, b, farm);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (plugin.getFarmManager().getPlayerFarm(p) == null) {
            FarmData f = plugin.getFarmManager().getFarmAt(p.getLocation());
            if (f != null) plugin.getFarmManager().enterFarm(p, f);
        }
    }

    private boolean isCrop(Material m) {
        return switch (m) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS,
                 MELON_STEM, PUMPKIN_STEM, MELON, PUMPKIN -> true;
            default -> false;
        };
    }
}
