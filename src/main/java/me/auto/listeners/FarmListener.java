package me.auto.listeners;

import me.auto.AutoPlugin;
import me.auto.managers.FarmData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
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

        // Кавун/тиква — одразу
        Material type = b.getType();
        if (type == Material.MELON || type == Material.PUMPKIN) {
            double reward = plugin.getConfig().getDouble("farm.rewards." + type.name(), 4.0);
            plugin.getNpcManager().addPendingFarm(p.getUniqueId(), reward);
            b.setType(Material.AIR);
            showBar(p, reward, type.name().toLowerCase(), "farm");
            p.playSound(p.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
            return;
        }

        if (!(b.getBlockData() instanceof Ageable age)) return;
        if (age.getAge() < age.getMaximumAge()) {
            plugin.getHudManager().sendActionBar(p, "&cНе дозрів!");
            return;
        }

        double reward = plugin.getConfig().getDouble("farm.rewards." + type.name(), 2.0);
        plugin.getNpcManager().addPendingFarm(p.getUniqueId(), reward);

        // Відновлення одразу
        b.setType(type);
        if (b.getBlockData() instanceof Ageable fresh) {
            fresh.setAge(0);
            b.setBlockData(fresh);
        }

        showBar(p, reward, type.name().toLowerCase().replace("_",""), "farm");
        p.playSound(p.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
    }

    private void showBar(Player p, double reward, String what, String zone) {
        double total = plugin.getNpcManager().getPending(p.getUniqueId(), zone);
        plugin.getHudManager().sendActionBar(p,
                "&a+" + reward + " &7(" + what + ") &8| &7Накоп: &e$" + String.format("%.1f", total));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Player p = e.getPlayer();
        if (plugin.getFarmManager().getPlayerFarm(p) == null) {
            FarmData f = plugin.getFarmManager().getFarmAt(p.getLocation());
            if (f != null) plugin.getFarmManager().enterFarm(p, f);
        }
    }

    private boolean isCrop(Material m) {
        return switch (m) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, MELON, PUMPKIN -> true;
            default -> false;
        };
    }
}
