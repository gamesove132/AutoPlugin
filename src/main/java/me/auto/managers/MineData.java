package me.auto.managers;

import org.bukkit.Location;
import java.util.UUID;

public class MineData extends AutoBlock {
    private int sizeX = 15;
    private int sizeZ = 15;
    private int depth = 10;

    public MineData(String id, Location center, UUID owner, String name) {
        super(id, Type.MINE, center, owner, name);
    }

    public int getSizeX() { return sizeX; }
    public void setSizeX(int v) { sizeX = v; }
    public int getSizeZ() { return sizeZ; }
    public void setSizeZ(int v) { sizeZ = v; }
    public int getDepth() { return depth; }
    public void setDepth(int v) { depth = v; }
}
