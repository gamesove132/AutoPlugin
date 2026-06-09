package me.auto.managers;

import org.bukkit.Location;
import java.util.UUID;

public class FarmData extends AutoBlock {
    public FarmData(String id, Location center, UUID owner, String name) {
        super(id, Type.FARM, center, owner, name);
    }
}
