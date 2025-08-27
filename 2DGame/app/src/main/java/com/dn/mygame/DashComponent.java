package com.dn.mygame;

public class DashComponent {
    private Entity entity;
    private int dashCooldown = 0;
    private final int dashDistance = 2;
    private final int dashCooldownDuration = 20;
    private boolean accelerateSlide = false;

    public DashComponent(Entity entity) {
        this.entity = entity;
    }

    public void dash(int dx, int dy, TileMap map) {
        if (dashCooldown > 0
                || entity.isSliding()
                || map.isSlippery(entity.getX(), entity.getY())) return;

        int maxStep = 0;
        boolean foundTeleporter = false;

        for (int step = 1; step <= dashDistance; step++) {
            int checkX = entity.getX() + dx * step;
            int checkY = entity.getY() + dy * step;

            if (map.isObstacle(checkX, checkY)) break;

            if (map.isTeleporter(checkX, checkY)) {
                maxStep = step;
                foundTeleporter = true;
                break;
            }

            if (map.isTraversable(checkX, checkY)) {
                maxStep = step;
            } else {
                break;
            }
        }

        if (maxStep > 0) {
            entity.setPosition(
                            entity.getX() + dx * maxStep,
                            entity.getY() + dy * maxStep);
            dashCooldown = dashCooldownDuration;

            if (foundTeleporter) {
                entity.snapToPosition();
                entity.handleTeleport(map);
            }
            entity.setFacingDirection(dx, dy);
        }
    }

    public void update() {
        if (dashCooldown > 0) dashCooldown--;
    }

    public void setAccelerateSlide(boolean accelerate) {
        accelerateSlide = accelerate;
    }

    // Getters
    public boolean isDashing() {
        return dashCooldown > 0;
    }

    public boolean isAcceleratingSlide() {
        return accelerateSlide;
    }
}