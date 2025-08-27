package com.dn.mygame;

import android.graphics.Canvas;
import java.util.List;

public abstract class Entity {
    private final MovementComponent movement;
    private final TeleportationComponent teleportation;
    private final RenderComponent render;
    private final SpawnComponent spawn;
    private final StatusComponent status;
    private final DashComponent dash;
    private final TileMap tileMap;

    public Entity(int startX, int startY, TileMap tileMap) {
        this.tileMap = tileMap;
        this.movement = new MovementComponent(this, startX, startY);
        this.teleportation = new TeleportationComponent(this);
        this.render = new RenderComponent(this, startX, startY, tileMap.getTileSize());
        this.spawn = new SpawnComponent(startX, startY);
        this.status = new StatusComponent();
        this.dash = new DashComponent(this);
    }

    // Core functionality methods
    public void move(int dx, int dy, TileMap map, List<Entity> allEntities) {
        movement.move(dx, dy, map, allEntities);
    }

    public void update(TileMap map) {
        movement.update(map);
        teleportation.update(map);
        dash.update();
        status.update();
        render.update();

        if (map.isPit(movement.getX(), movement.getY()) && !movement.isJumping()) {
            respawn();
        }

        if (map.isTeleporter(movement.getX(), movement.getY())) {
            teleportation.handleTeleport(map);
        }
    }

    public void respawn() {
        movement.setPosition(spawn.getSpawnX(), spawn.getSpawnY());
        render.snapToPosition();
        status.setInvulnerable(true);
        teleportation.reset();
        movement.stopSliding();
        movement.stopJumping();
    }

public void dash(int dx, int dy, TileMap map) {
dash.dash(dx, dy, map);
}

public void setPosition(int x, int y) {
movement.setPosition(x, y);
}

public void setFacingDirection(int x, int y) {
movement.setFacingDirection(x, y);
}

public void snapToPosition() {
render.snapToPosition();
}

public void handleTeleport(TileMap map) {
teleportation.handleTeleport(map);
}

    // Common property accessors
    public int getX() { return movement.getX(); }
    public int getY() { return movement.getY(); }
    public float getDrawX() { return render.getDrawX(); }
    public float getDrawY() { return render.getDrawY(); }
    public int getTileSize() { return tileMap.getTileSize(); }
    
    // Movement properties
    public boolean isMoving() { return movement.isMoving(); }
    public boolean isSliding() { return movement.isSliding(); }
    public boolean isJumping() { return movement.isJumping(); }
    public int getFacingDx() { return movement.getFacingDx(); }
    public int getFacingDy() { return movement.getFacingDy(); }
    public int getSlideDx() { return movement.getSlideDx(); }
    public int getSlideDy() { return movement.getSlideDy(); }
    public int getRecentTileMoves() { return movement.getRecentTileMoves(); }

    // Teleportation properties
    public boolean isTeleporting() { return teleportation.isTeleporting(); }
    public int getTeleportTimer() { return teleportation.getTeleportTimer(); }
    public int getTeleportDuration() { return teleportation.getTeleportDuration(); }
    public boolean isWaitingForCenter() { return teleportation.isWaitingForCenter(); }
    public boolean needsTeleportBack() { return teleportation.needsTeleportBack(); }

    // Render properties
    public float getVelocityX() { return render.getVelocityX(); }
    public float getVelocityY() { return render.getVelocityY(); }
    public float getVelocityMagnitude() { return render.getVelocityMagnitude(); }

    // Spawn properties
    public int getSpawnX() { return spawn.getSpawnX(); }
    public int getSpawnY() { return spawn.getSpawnY(); }
    public void setSpawn(int x, int y) { spawn.setSpawn(x, y); }

    // Status properties
    public boolean isInvulnerable() { return status.isInvulnerable(); }
    public int getInvulnerabilityTimer() { return status.getInvulnerabilityTimer(); }

    // Dash properties
    public boolean isDashing() { return dash.isDashing(); }
    public boolean isAcceleratingSlide() { return dash.isAcceleratingSlide(); }



    // Input handling
    public void setHoldDirection(int dx, int dy, boolean running) {
        movement.setFacingDirection(dx, dy);
        dash.setAccelerateSlide(movement.isSliding() && 
            dx == movement.getSlideDx() && 
            dy == movement.getSlideDy() && 
            running
        );
    }

    public void requestStopSliding() {
        movement.requestStopSliding();
    }

    public abstract void draw(Canvas canvas);
    public TileMap getTileMap() { return tileMap; }
}