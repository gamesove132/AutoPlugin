package me.auto.listeners;

import me.auto.AutoPlugin;
import me.auto.managers.MineData;
import me.auto.managers.MineManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class MineListener implements Listener {
    private final AutoPlugin plugin;
    public MineListener(AutoPlugin p) { this.plugin = p; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        MineData mine = plugin.getMineManager().getMineAt(e.getBlock().getLocation());
        if (mine == null) return;

        if (!MineManager.isMinePickaxe(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            plugin.getHudManager().sendActionBar(p, "&cВикористовуй &e⛏ Шахтарську кирку!");
            return;
        }

        e.setDropItems(false);
        e.setExpToDrop(0);

        if (plugin.getMineManager().getPlayerMine(p) == null)
            plugin.getMineManager().enterMine(p, mine);

        double reward = plugin.getConfig().getDouble("mine.rewards." + e.getBlock().getType().name(), 0.0);
        if (reward > 0) {
            // Накопичуємо pending — гроші дасть NPC
            plugin.getNpcManager().addPendingMine(p.getUniqueId(), reward);
            double totalPending = plugin.getNpcManager().getPending(p.getUniqueId(), "mine");
            String matName = e.getBlock().getType().name()
                    .replace("DEEPSLATE_","").replace("_ORE","").toLowerCase();
            plugin.getHudManager().sendActionBar(p,
                    "&a+" + String.format("%.0f", reward) + " &7за &e" + matName +
                    " &8| &7Накопичено: &e$" + String.format("%.0f", totalPending));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 2f);
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
