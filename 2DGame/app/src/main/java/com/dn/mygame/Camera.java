package com.dn.mygame;

public class Camera {
    private float viewOffsetX, viewOffsetY;
    private int screenWidth, screenHeight;
    private int screenCenterX, screenCenterY;
    private int deadZoneHalfWidth, deadZoneHalfHeight;
    private final Player player;
    
    // Dynamic smoothing configuration
    private static final float BASE_SMOOTHING = 0.08f; // Reduced from 0.1f
    private static final float MIN_SMOOTHING = 0.04f;  // Adjusted min
    private static final float MAX_SMOOTHING = 0.2f;   // Adjusted max
    private static final float TILE_MOVE_SMOOTHING_FACTOR = 0.1f; // Increased from 0.05f
    private static final int REFERENCE_SCREEN_WIDTH = 1080;
    private static final int REFERENCE_TILE_SIZE = 64;
    private float dynamicSmoothing;
    
    // Reduced dead-zone ratio for tighter camera follow
    private static final float DEADZONE_TILE_RATIO = 0.15f; // Reduced from 0.25f

    public Camera(int screenWidth, int screenHeight, Player player) {
        this.player = player;
        updateScreenSize(screenWidth, screenHeight);
        recalcInitialOffset();
    }

    public void updateScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        this.screenCenterX = width / 2;
        this.screenCenterY = height / 2;
        recalcDeadZone();
    }

    private void recalcDeadZone() {
        int tileSize = player.getTileSize();
        if (tileSize <= 0) {
            throw new IllegalStateException("Invalid tile size");
        }

        // Prevent division by zero
        float visibleTilesX = Math.max(1, (float) screenWidth / tileSize);
        float visibleTilesY = Math.max(1, (float) screenHeight / tileSize);
        
        deadZoneHalfWidth = (int) (visibleTilesX * DEADZONE_TILE_RATIO) * tileSize;
        deadZoneHalfHeight = (int) (visibleTilesY * DEADZONE_TILE_RATIO) * tileSize;
    }

    public void recalcInitialOffset() {
        float half = player.getTileSize() * 0.5f;
        viewOffsetX = screenCenterX - (player.getDrawX() + half);
        viewOffsetY = screenCenterY - (player.getDrawY() + half);
    }

    public void update() {
    
    if (player.getTileMap() == null) return;
        calculateSmoothing();
        float half = player.getTileSize() * 0.5f;
        float px = player.getDrawX() + half;
        float py = player.getDrawY() + half;

        float screenX = px + viewOffsetX;
        float screenY = py + viewOffsetY;

        float leftBound = screenCenterX - deadZoneHalfWidth;
        float rightBound = screenCenterX + deadZoneHalfWidth;
        float topBound = screenCenterY - deadZoneHalfHeight;
        float bottomBound = screenCenterY + deadZoneHalfHeight;

        float adjustX = 0, adjustY = 0;

        if (screenX < leftBound) {
            adjustX = leftBound - screenX;
        } else if (screenX > rightBound) {
            adjustX = rightBound - screenX;
        }

        if (screenY < topBound) {
            adjustY = topBound - screenY;
        } else if (screenY > bottomBound) {
            adjustY = bottomBound - screenY;
        }

        viewOffsetX += adjustX * dynamicSmoothing;
        viewOffsetY += adjustY * dynamicSmoothing;

        // Clamp offsets to keep player in dead-zone
        viewOffsetX = Math.max(leftBound - px, Math.min(viewOffsetX, rightBound - px));
        viewOffsetY = Math.max(topBound - py, Math.min(viewOffsetY, bottomBound - py));
    }

          private void calculateSmoothing() {
        float screenFactor = (float) screenWidth / REFERENCE_SCREEN_WIDTH;
        float tileFactor = player.getTileSize() / REFERENCE_TILE_SIZE;
        float speedFactor = 1.0f + player.getVelocityMagnitude() * 0.8f;
        
        // Factor in recent tile movements for smoother transitions during rapid moves
        float tileMoveFactor = 1.0f + (player.getRecentTileMoves() * TILE_MOVE_SMOOTHING_FACTOR);

        dynamicSmoothing = BASE_SMOOTHING * screenFactor * tileFactor * tileMoveFactor / speedFactor;
        dynamicSmoothing = Math.max(MIN_SMOOTHING, Math.min(MAX_SMOOTHING, dynamicSmoothing));
    }

    public float getViewOffsetX() { return viewOffsetX; }
    public float getViewOffsetY() { return viewOffsetY; }
}
