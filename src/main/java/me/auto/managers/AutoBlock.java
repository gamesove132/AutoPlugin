package me.auto.managers;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Base data class for all auto-blocks (mine, forest, farm)
 */
public class AutoBlock {

    public enum Type { MINE, FOREST, FARM }

    private final String id;
    private final Type type;
    private final Location center;
    private final String worldName;
    private UUID owner;
    private String name;

    // Current player using this block
    private UUID activePlayer;
    private long lastReset;

    public AutoBlock(String id, Type type, Location center, UUID owner, String name) {
        this.id        = id;
        this.type      = type;
        this.center    = center.clone();
        this.worldName = center.getWorld().getName();
        this.owner     = owner;
        this.name      = name;
        this.lastReset = System.currentTimeMillis();
    }

    public String getId()       { return id; }
    public Type getType()       { return type; }
    public Location getCenter() { return center.clone(); }
    public String getWorldName(){ return worldName; }
    public UUID getOwner()      { return owner; }
    public String getName()     { return name; }
    public void setName(String n){ this.name = n; }

    public UUID getActivePlayer()          { return activePlayer; }
    public void setActivePlayer(UUID uuid) { this.activePlayer = uuid; }
    public boolean hasActivePlayer()       { return activePlayer != null; }

    public long getLastReset()             { return lastReset; }
    public void setLastReset(long t)       { this.lastReset = t; }

    public boolean isActive() { return activePlayer != null; }
}
