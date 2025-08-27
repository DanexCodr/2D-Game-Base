package com.dn.mygame;

public class SpawnComponent {
    private int spawnX, spawnY;

    public SpawnComponent(int startX, int startY) {
        this.spawnX = startX;
        this.spawnY = startY;
    }

    public void setSpawn(int x, int y) {
        spawnX = x;
        spawnY = y;
    }

    // Getters
    public int getSpawnX() { return spawnX; }
    public int getSpawnY() { return spawnY; }
}