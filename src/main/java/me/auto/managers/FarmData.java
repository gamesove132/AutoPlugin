package me.auto.managers;

import org.bukkit.Location;
import java.util.UUID;

public class FarmData extends AutoBlock {
    private int sizeX = 9;
    private int sizeZ = 9;

    public FarmData(String id, Location center, UUID owner, String name) {
        super(id, Type.FARM, center, owner, name);
    }

    public int getSizeX() { return sizeX; }
    public void setSizeX(int v) { sizeX = v; }
    public int getSizeZ() { return sizeZ; }
    public void setSizeZ(int v) { sizeZ = v; }
}
