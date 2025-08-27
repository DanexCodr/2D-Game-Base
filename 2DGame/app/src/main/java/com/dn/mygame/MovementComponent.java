package com.dn.mygame;

import java.util.List;

public class MovementComponent {
    private Entity entity;
    private int x, y;
    private int facingDx = 0, facingDy = 1;
    private boolean isSliding = false;
    private int slideDx, slideDy, slideDelay = 5, slideTimer = 0;
    private boolean stopRequested = false;
    private int stopSlideCount = 0;
    private boolean isJumping = false;
    private int jumpTimer = 0;
    private final int jumpDuration = 10;
    private int moveCooldown = 0;
    private final int moveDelay = 0;
    private int recentTileMoves = 0;
    private static final int TILE_MOVE_DECAY_RATE = 3;
    private boolean isMovingThisFrame = false;

    public MovementComponent(Entity entity, int startX, int startY) {
        this.entity = entity;
        this.x = startX;
        this.y = startY;
    }

    public void move(int dx, int dy, TileMap map, List<Entity> allEntities) {
        if (moveCooldown > 0 || isSliding || isJumping || entity.isInvulnerable() 
                || entity.isTeleporting() || entity.needsTeleportBack() 
                || entity.isWaitingForCenter()) {
            return;
        }

        if (map.isJumpPad(x, y)) {
            attemptJump(dx, dy, map);
            return;
        }

        int nx = x + dx, ny = y + dy;

        if (map.isObstacle(nx, ny)) {
            facingDx = dx;
            facingDy = dy;
            return;
        }

        for (Entity e : allEntities) {
            if (e != entity && e.getX() == nx && e.getY() == ny) {
                facingDx = dx;
                facingDy = dy;
                return;
            }
        }

        if (map.isTraversable(nx, ny) || map.isTeleporter(nx, ny)) {
            x = nx;
            y = ny;
            recentTileMoves += 1;
            isMovingThisFrame = true;
            moveCooldown = moveDelay;
            facingDx = dx;
            facingDy = dy;

            if (map.isCheckpoint(x, y)) {
                entity.setSpawn(x, y);
            }
            if (map.isSlippery(x, y)) {
                stopRequested = false;
                stopSlideCount = 0;
                isSliding = true;
                slideDx = dx;
                slideDy = dy;
                slideTimer = slideDelay;
            }
        }
    }

    private void attemptJump(int dx, int dy, TileMap map) {
        int j1x = x + dx, j1y = y + dy;
        int j2x = j1x + dx, j2y = j1y + dy;
        boolean canJump = (map.isPit(j1x, j1y) || map.isTraversable(j1x, j1y))
                && map.isTraversable(j2x, j2y)
                && !map.isObstacle(j2x, j2y);
        if (canJump) {
            isJumping = true;
            jumpTimer = jumpDuration;
            x = j2x;
            y = j2y;
            facingDx = dx;
            facingDy = dy;
        }
    }

    public void requestStopSliding() {
        if (isSliding && !stopRequested) {
            stopRequested = true;
            stopSlideCount = 2;
        }
    }

    public void update(TileMap map) {
        isMovingThisFrame = false;

        if (isSliding && slideTimer <= 0) {
            recentTileMoves += 1;
            isMovingThisFrame = true;
        }

        if (!isMovingThisFrame) {
            recentTileMoves = Math.max(0, recentTileMoves - TILE_MOVE_DECAY_RATE);
        }

        if (isJumping && --jumpTimer <= 0) {
            isJumping = false;
        }

        if (moveCooldown > 0) moveCooldown--;

        // Handle sliding
        if (isSliding) {
            slideTimer--;
            if (entity.isAcceleratingSlide()) slideTimer--;

            if (slideTimer <= 0) {
                int nx = x + slideDx, ny = y + slideDy;
                if (map.isTraversable(nx, ny)) {
                    x = nx;
                    y = ny;
                    slideTimer = slideDelay;

                    if (stopRequested) {
                        stopSlideCount--;
                        if (stopSlideCount <= 0) {
                            isSliding = false;
                            stopRequested = false;
                        }
                    } else if (!map.isSlippery(x, y)) {
                        isSliding = false;
                    }
                } else {
                    isSliding = false;
                    stopRequested = false;
                }
            }
        }
    }

public void stopSliding() {
        isSliding = false;
        stopRequested = false;
    }

    public void stopJumping() {
        isJumping = false;
    }

    // Getters and setters

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    public void setFacingDirection(int dx, int dy) {
        facingDx = dx;
        facingDy = dy;
    }

public boolean isMoving() { return moveCooldown > 0; }
    
    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isSliding() { return isSliding; }
    public boolean isJumping() { return isJumping; }
    public int getFacingDx() { return facingDx; }
    public int getFacingDy() { return facingDy; }
    public int getSlideDx() { return slideDx; }
    public int getSlideDy() { return slideDy; }
    public int getRecentTileMoves() { return recentTileMoves; }
}