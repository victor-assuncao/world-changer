package com.worldchanger;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

/**
 * Dados de nivel proprios de cada mundo: gamerules e spawn independentes.
 * Hora, clima, gamerules e spawn sao independentes do overworld.
 */
public class WorldLevelData extends DerivedLevelData {
    private final GameRules gameRules;
    private BlockPos spawn = BlockPos.ZERO;
    private float spawnAngle = 0f;
    private boolean spawnInitialized;
    private long gameTime;
    private long dayTime;
    private int clearWeatherTime;
    private int rainTime;
    private int thunderTime;
    private boolean raining;
    private boolean thundering;

    public WorldLevelData(WorldData worldData, ServerLevelData overworldData, GameRules gameRules) {
        super(worldData, overworldData);
        this.gameRules = gameRules;
        this.gameTime = overworldData.getGameTime();
        this.dayTime = overworldData.getDayTime();
        this.clearWeatherTime = overworldData.getClearWeatherTime();
        this.rainTime = overworldData.getRainTime();
        this.thunderTime = overworldData.getThunderTime();
        this.raining = overworldData.isRaining();
        this.thundering = overworldData.isThundering();
    }

    @Override
    public GameRules getGameRules() {
        return gameRules;
    }

    @Override
    public BlockPos getSpawnPos() {
        return spawn;
    }

    @Override
    public float getSpawnAngle() {
        return spawnAngle;
    }

    @Override
    public void setSpawn(BlockPos pos, float angle) {
        this.spawn = pos;
        this.spawnAngle = angle;
        this.spawnInitialized = true;
    }

    public boolean hasSpawn() { return spawnInitialized; }

    @Override public long getGameTime() { return gameTime; }
    @Override public long getDayTime() { return dayTime; }
    @Override public void setGameTime(long value) { gameTime = value; }
    @Override public void setDayTime(long value) { dayTime = value; }
    @Override public int getClearWeatherTime() { return clearWeatherTime; }
    @Override public void setClearWeatherTime(int value) { clearWeatherTime = value; }
    @Override public int getRainTime() { return rainTime; }
    @Override public void setRainTime(int value) { rainTime = value; }
    @Override public int getThunderTime() { return thunderTime; }
    @Override public void setThunderTime(int value) { thunderTime = value; }
    @Override public boolean isRaining() { return raining; }
    @Override public void setRaining(boolean value) { raining = value; }
    @Override public boolean isThundering() { return thundering; }
    @Override public void setThundering(boolean value) { thundering = value; }
}
