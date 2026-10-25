package com.worldchanger;

import net.minecraft.server.level.ServerPlayer;

/** Implemented by the player's existing tracker, also held by PlayerList. */
public interface WorldAdvancements {
    void worldchanger$switchWorld(ServerPlayer player);
}
