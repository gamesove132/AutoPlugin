package me.auto.managers;

import org.bukkit.Location;
import java.util.*;

public class ForestData extends AutoBlock {
    private List<Location> treeLocations = new ArrayList<>();
    private int sizeX = 15;
    private int sizeZ = 15;

    public ForestData(String id, Location center, UUID owner, String name) {
        super(id, Type.FOREST, center, owner, name);
    }

    public List<Location> getTreeLocations() { return treeLocations; }
    public void setTreeLocations(List<Location> locs) { treeLocations = locs; }
    public int getSizeX() { return sizeX; }
    public void setSizeX(int v) { sizeX = v; }
    public int getSizeZ() { return sizeZ; }
    public void setSizeZ(int v) { sizeZ = v; }
}
