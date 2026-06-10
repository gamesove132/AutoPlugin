package me.auto.listeners;
import me.auto.AutoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

public class NpcListener implements Listener {
    private final AutoPlugin plugin;
    public NpcListener(AutoPlugin p) { this.plugin = p; }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent e) {
        if (!(e.getRightClicked() instanceof org.bukkit.entity.ArmorStand stand)) return;
        Player player = e.getPlayer();
        plugin.getNpcManager().onInteract(p, stand);
    }
}
