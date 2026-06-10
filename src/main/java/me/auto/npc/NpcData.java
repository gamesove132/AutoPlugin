package me.auto.npc;

import org.bukkit.Location;
import java.util.UUID;

public class NpcData {
    private final String id;
    private final UUID standUuid;
    private final Location location;
    private final UUID owner;
    private String name;
    private String command;
    private String skinName;
    private String npcType = "CUSTOM"; // CUSTOM | MINE | FARM | FOREST

    public NpcData(String id, UUID standUuid, Location location, UUID owner, String name) {
        this.id = id;
        this.standUuid = standUuid;
        this.location = location;
        this.owner = owner;
        this.name = name;
    }

    public String getId()           { return id; }
    public UUID getStandUuid()      { return standUuid; }
    public Location getLocation()   { return location; }
    public UUID getOwner()          { return owner; }
    public String getName()         { return name; }
    public void setName(String n)   { this.name = n; }
    public String getCommand()      { return command; }
    public void setCommand(String c){ this.command = c; }
    public String getSkinName()     { return skinName; }
    public void setSkinName(String s){ this.skinName = s; }
    public String getNpcType()      { return npcType; }
    public void setNpcType(String t){ this.npcType = t != null ? t.toUpperCase() : "CUSTOM"; }
}
