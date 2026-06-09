package me.auto.managers;

import org.bukkit.Location;
import java.util.UUID;

public class MineData extends AutoBlock {
    public MineData(String id, Location center, UUID owner, String name) {
        super(id, Type.MINE, center, owner, name);
    }
}
