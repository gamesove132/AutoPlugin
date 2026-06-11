package me.auto.selection;

import me.auto.AutoPlugin;
import me.auto.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * ЛКМ по блоку = pos1, ПКМ по блоку = pos2
 * Тільки якщо в руці "Топор Виділення" (кам'яна сокира з нашим lore)
 */
public class SelectionListener implements Listener {

    private final AutoPlugin plugin;

    // Щоб не реагувати двічі на одну взаємодію
    private final Set<UUID> cooldown = new HashSet<>();

    public SelectionListener(AutoPlugin plugin) {
        this.plugin = plugin;
    }

    /** Видає топор виділення гравцю */
    public static ItemStack createWand() {
        ItemStack axe = new ItemStack(Material.STONE_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "⚒ Топор Виділення");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "ЛКМ по блоку — позиція 1");
        lore.add(ChatColor.GRAY + "ПКМ по блоку — позиція 2");
        lore.add(ChatColor.YELLOW + "Для: шахта / ферма / ліс");
        meta.setLore(lore);
        axe.setItemMeta(meta);
        return axe;
    }

    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.STONE_AXE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        return item.getItemMeta().getDisplayName().contains("Топор Виділення");
    }

    // ЛКМ — pos1 (BlockBreakEvent зручніший для ЛКМ по блоку)
    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isWand(item)) return;
        e.setCancelled(true);
        if (cooldown.contains(p.getUniqueId())) return;
        plugin.getSelectionManager().setPos1(p, e.getBlock().getLocation());
        startCooldown(p);
    }

    // ПКМ — pos2
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isWand(item)) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        e.setCancelled(true);
        if (cooldown.contains(p.getUniqueId())) return;
        plugin.getSelectionManager().setPos2(p, e.getClickedBlock().getLocation());
        startCooldown(p);
    }

    private void startCooldown(Player p) {
        cooldown.add(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> cooldown.remove(p.getUniqueId()), 2L);
    }
}
