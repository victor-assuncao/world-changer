package com.worldchanger;

import net.minecraft.server.level.ServerPlayer;

/** Switches the existing counter also cached by PlayerList. */
public interface WorldStatistics {
    void worldchanger$switchWorld(ServerPlayer player);
}
