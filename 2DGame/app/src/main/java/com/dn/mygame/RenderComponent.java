package com.dn.mygame;

public class RenderComponent {
    private Entity entity;
    private float drawX, drawY;
    private float prevDrawX, prevDrawY;
    private float velocityX, velocityY;
    private long lastUpdateTime;
    private static final float NANOS_TO_SECONDS = 1 / 1e9f;

    public RenderComponent(Entity entity, int startX, int startY, int tileSize) {
        this.entity = entity;
        this.drawX = startX * tileSize;
        this.drawY = startY * tileSize;
        this.prevDrawX = drawX;
        this.prevDrawY = drawY;
    }

    public void update() {
        prevDrawX = drawX;
        prevDrawY = drawY;

        float targetX = entity.getX() * entity.getTileMap().getTileSize();
        float targetY = entity.getY() * entity.getTileMap().getTileSize();
        float interpolationFactor = 0.85f;
        drawX += (targetX - drawX) * interpolationFactor;
        drawY += (targetY - drawY) * interpolationFactor;

        long now = System.nanoTime();
        float deltaTime = (now - lastUpdateTime) * NANOS_TO_SECONDS;
        lastUpdateTime = now;

        if (deltaTime > 0) {
            velocityX = (drawX - prevDrawX) / deltaTime;
            velocityY = (drawY - prevDrawY) / deltaTime;
        }
    }

    public void snapToPosition() {
        drawX = entity.getX() * entity.getTileMap().getTileSize();
        drawY = entity.getY() * entity.getTileMap().getTileSize();
    }

    // Getters
    public int getTileSize() {
        return entity.getTileMap().getTileSize();
    }
    public float getDrawX() { return drawX; }
    public float getDrawY() { return drawY; }
    public float getVelocityX() { return velocityX; }
    public float getVelocityY() { return velocityY; }
    
    public float getVelocityMagnitude() {
        return (float) Math.hypot(velocityX, velocityY);
    }
}