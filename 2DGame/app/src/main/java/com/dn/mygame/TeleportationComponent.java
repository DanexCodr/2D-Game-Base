package com.dn.mygame;

public class TeleportationComponent {
    private Entity entity;
    private boolean needsTeleportBack = false;
    private int teleportBackTimer = 0;
    private boolean justTeleported = false;
    private int lastTeleportedX = -999, lastTeleportedY = -999;
    private boolean isTeleporting = false;
    private int teleportTimer = 0;
    private final int teleportDuration = 10;
    private int[] delayedMovementTarget = null;
    private boolean waitingForCenter = false;
    private int delayedActionTimer = 0;
    private final int CENTER_WAIT_DURATION = 5 * 60;
    private final int postTeleportWaitDuration = 5 * 60;

    public TeleportationComponent(Entity entity) {
        this.entity = entity;
    }

    public void handleTeleport(TileMap map) {
        if (justTeleported || isTeleporting) return;

        float tileSize = entity.getTileMap().getTileSize();
        float centerThreshold = tileSize * 0.25f;
        float drawX = entity.getDrawX();
        float drawY = entity.getDrawY();
        int currentX = entity.getX();
        int currentY = entity.getY();

        float offsetX = Math.abs(drawX - currentX * tileSize);
        float offsetY = Math.abs(drawY - currentY * tileSize);

        if (offsetX > centerThreshold || offsetY > centerThreshold) return;

        TileMap.Point destination = map.getTeleporterDestination(currentX, currentY);
        if (destination == null) return;

        lastTeleportedX = currentX;
        lastTeleportedY = currentY;
        isTeleporting = true;
        teleportTimer = teleportDuration;
        justTeleported = true;

        entity.setPosition(destination.x, destination.y);
        int destChunkX = Math.floorDiv(destination.x, TileMap.CHUNK_SIZE);
        int destChunkY = Math.floorDiv(destination.y, TileMap.CHUNK_SIZE);
        map.updateActiveChunks(destChunkX, destChunkY, 3);

        waitingForCenter = false;
        delayedMovementTarget = null;
        delayedActionTimer = 0;
    }

     public void update(TileMap map) {
        if (isTeleporting) {
            teleportTimer--;
            if (teleportTimer <= 0) {
                isTeleporting = false;
                entity.snapToPosition();
            }
        }

        if (justTeleported && !isTeleporting) {
            attemptImmediateMovement(map);
            justTeleported = false;
        }

        if (needsTeleportBack) {
            teleportBackTimer--;
            float tileSize = entity.getTileMap().getTileSize();
            float drawX = entity.getDrawX();
            float drawY = entity.getDrawY();
            int targetX = entity.getX() * (int)tileSize;
            int targetY = entity.getY() * (int)tileSize;

            if (teleportBackTimer <= 0
                    && Math.abs(drawX - targetX) <= tileSize * 0.1f
                    && Math.abs(drawY - targetY) <= tileSize * 0.1f) {
                if (lastTeleportedX != -999 && lastTeleportedY != -999) {
                    entity.setPosition(lastTeleportedX, lastTeleportedY);
                    entity.snapToPosition();
                    isTeleporting = true;
                    teleportTimer = teleportDuration;
                }
                needsTeleportBack = false;
            }
        }

        if (waitingForCenter) {
            float tileSize = entity.getTileMap().getTileSize();
            float drawX = entity.getDrawX();
            float drawY = entity.getDrawY();
            int targetX = entity.getX() * (int)tileSize;
            int targetY = entity.getY() * (int)tileSize;

            if (Math.abs(drawX - targetX) <= tileSize * 0.1f
                    && Math.abs(drawY - targetY) <= tileSize * 0.1f) {

                if (delayedActionTimer <= 0) {
                    delayedActionTimer = CENTER_WAIT_DURATION;
                } else {
                    delayedActionTimer--;
                    if (delayedActionTimer <= 0) {
                        if (delayedMovementTarget != null) {
                            entity.setPosition(
                                delayedMovementTarget[0], 
                                delayedMovementTarget[1]
                            );
                        } else if (lastTeleportedX != -999 && lastTeleportedY != -999) {
                            entity.setPosition(lastTeleportedX, lastTeleportedY);
                            entity.snapToPosition();
                            isTeleporting = true;
                            teleportTimer = teleportDuration;
                        }
                        waitingForCenter = false;
                        delayedMovementTarget = null;
                    }
                }
            }
        }
    }

    private void attemptImmediateMovement(TileMap map) {
        int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int facingDx = entity.getFacingDx();
        int facingDy = entity.getFacingDy();
        int x = entity.getX();
        int y = entity.getY();

        int fx = x + facingDx;
        int fy = y + facingDy;
        if (map.isTraversable(fx, fy) && !map.isTeleporter(fx, fy) && !map.isObstacle(fx, fy)) {
            entity.setPosition(fx, fy);
            return;
        }

        for (int[] dir : directions) {
            int checkX = x + dir[0];
            int checkY = y + dir[1];
            if (map.isTraversable(checkX, checkY) && !map.isTeleporter(checkX, checkY)) {
                entity.setPosition(checkX, checkY);
                entity.setFacingDirection(dir[0], dir[1]);
                return;
            }
        }

        boolean foundOther = false;
        for (int[] dir : directions) {
            int checkX = x + dir[0];
            int checkY = y + dir[1];
            if (map.isTraversable(checkX, checkY)) {
                delayedMovementTarget = new int[]{checkX, checkY};
                entity.setFacingDirection(dir[0], dir[1]);
                waitingForCenter = true;
                foundOther = true;
                break;
            }
        }

        if (!foundOther) {
            waitingForCenter = true;
            delayedMovementTarget = null;
            needsTeleportBack = true;
            teleportBackTimer = postTeleportWaitDuration;
        }
    }

    public void reset() {
        needsTeleportBack = false;
        waitingForCenter = false;
        delayedMovementTarget = null;
        teleportBackTimer = 0;
        delayedActionTimer = 0;
    lastTeleportedX = -999;
    lastTeleportedY = -999;
    }

    // Getters and setters
    public boolean isTeleporting() { return isTeleporting; }
    public boolean isWaitingForCenter() {
        return waitingForCenter;
    }

    public boolean needsTeleportBack() {
        return needsTeleportBack;
    }
    
    public int getTeleportDuration() { return teleportDuration; }
    public int getTeleportTimer() { return teleportTimer; }
}