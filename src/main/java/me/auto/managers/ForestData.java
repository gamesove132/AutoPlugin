package me.auto.managers;

import org.bukkit.Location;
import java.util.*;

public class ForestData extends AutoBlock {
    private List<Location> treeLocations = new ArrayList<>();

    public ForestData(String id, Location center, UUID owner, String name) {
        super(id, Type.FOREST, center, owner, name);
    }

    public List<Location> getTreeLocations() { return treeLocations; }
    public void setTreeLocations(List<Location> locs) { this.treeLocations = locs; }
}
