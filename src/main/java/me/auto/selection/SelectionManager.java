package me.auto.selection;

import me.auto.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;

public class SelectionManager {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public void setPos1(Player p, Location loc) {
        pos1.put(p.getUniqueId(), loc.clone());
        ColorUtils.msg(p, "&aПозиція &e1 &7(ЛКМ): &f" + fmt(loc));
        if (pos2.containsKey(p.getUniqueId())) showSize(p);
    }

    public void setPos2(Player p, Location loc) {
        pos2.put(p.getUniqueId(), loc.clone());
        ColorUtils.msg(p, "&aПозиція &e2 &7(ПКМ): &f" + fmt(loc));
        if (pos1.containsKey(p.getUniqueId())) showSize(p);
    }

    public boolean hasSelection(Player p) {
        return pos1.containsKey(p.getUniqueId()) && pos2.containsKey(p.getUniqueId());
    }

    public Location getMin(Player p) {
        Location a = pos1.get(p.getUniqueId());
        Location b = pos2.get(p.getUniqueId());
        return new Location(a.getWorld(),
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
    }

    public Location getMax(Player p) {
        Location a = pos1.get(p.getUniqueId());
        Location b = pos2.get(p.getUniqueId());
        return new Location(a.getWorld(),
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
    }

    public int getSizeX(Player p) { return (int)(getMax(p).getX() - getMin(p).getX()) + 1; }
    public int getSizeY(Player p) { return (int)(getMax(p).getY() - getMin(p).getY()) + 1; }
    public int getSizeZ(Player p) { return (int)(getMax(p).getZ() - getMin(p).getZ()) + 1; }

    public Location getCenter(Player p) {
        Location min = getMin(p);
        Location max = getMax(p);
        return new Location(min.getWorld(),
                (min.getX() + max.getX()) / 2.0,
                min.getY(),
                (min.getZ() + max.getZ()) / 2.0);
    }

    public void clear(Player p) {
        pos1.remove(p.getUniqueId());
        pos2.remove(p.getUniqueId());
    }

    private void showSize(Player p) {
        ColorUtils.msg(p, "&7Виділено: &e" + getSizeX(p) + "x" + getSizeZ(p) +
                " &7(&f" + getSizeX(p)*getSizeZ(p) + " &7блоків). Введи команду створення.");
    }

    private String fmt(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }
}
