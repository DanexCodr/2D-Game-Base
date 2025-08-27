// File: GameView.java
package com.dn.mygame;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.dn.mygame.TileMap.CHUNK_SIZE;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Dpad.OnDashListener {
    // D-pad configuration
    private static final int DPAD_SIZE = 120;
    private static final int DPAD_MARGIN_X = 150;
    private static final int DPAD_MARGIN_Y = 250;
    private Dpad dpad;

    private Paint fadePaint = new Paint(), coordinatesPaint = new Paint();
    private long lastMoveTime = 0;
    private static final long MOVE_DELAY = 150;
    private static final long RUN_MOVE_DELAY = 75;

    // Direction mapping
    private final Map<String, Point> dirMap = new HashMap<>();

    // Game world
    private GameThread gameThread;
    private Player player;
    NPC npc;
    private TileMap tileMap;
    private Camera camera;

    private List<Entity> entities = new ArrayList<>(); // Track all entities

    // Camera
    private boolean teleportingBefore = false;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        fadePaint.setColor(Color.BLACK);
        coordinatesPaint.setColor(Color.WHITE);
        coordinatesPaint.setTextSize(20);
        coordinatesPaint.setTextAlign(Paint.Align.RIGHT);
        tileMap = new TileMap(context);

        TileMap.Point spawnPoint = tileMap.getSpawnPoint();
        player = new Player(spawnPoint.x, spawnPoint.y, tileMap);
        npc = new NPC(spawnPoint.x + 2, spawnPoint.y - 3, tileMap);
        entities.add(player); // Add to entity list
        entities.add(npc); // Add to entity list
        dirMap.put("up", new Point(0, -1));
        dirMap.put("down", new Point(0, 1));
        dirMap.put("left", new Point(-1, 0));
        dirMap.put("right", new Point(1, 0));
        dirMap.put("up-left", new Point(-1, -1));
        dirMap.put("up-right", new Point(1, -1));
        dirMap.put("down-left", new Point(-1, 1));
        dirMap.put("down-right", new Point(1, 1));
        dirMap.put("center", new Point(0, 0));
        setFocusable(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        dpad = new Dpad(getWidth(), getHeight(), DPAD_SIZE, DPAD_MARGIN_X, DPAD_MARGIN_Y);
        dpad.setOnDashListener(this);

        gameThread = new GameThread(holder, this);
        gameThread.setRunning(true);
        gameThread.start();

        camera = new Camera(getWidth(), getHeight(), player);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (dpad != null) {
            dpad.updateScreenSize(width, height);
        }
        if (camera != null) {
            camera.updateScreenSize(width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        gameThread.setRunning(false);
        while (retry) {
            try {
                gameThread.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void update() {
        if (dpad == null) return;
        long now = System.currentTimeMillis();
        dpad.update();
        String currentDir = dpad.getHeldDirection();
        long delay = dpad.isRunning() ? RUN_MOVE_DELAY : MOVE_DELAY;

        if (dpad.isCenterHeld()) {
            if (currentDir != null) {
                Point d = dirMap.get(currentDir);
                player.setHoldDirection(d.x, d.y, false);
            }
        } else {
            if (currentDir != null && now - lastMoveTime > delay) {
                Point d = dirMap.get(currentDir);
                if (d != null) {
                    player.move(d.x, d.y, tileMap, entities);
                    lastMoveTime = now;
                }
            }

            if (currentDir != null) {
                Point d = dirMap.get(currentDir);
                player.setHoldDirection(d.x, d.y, dpad.isRunning());
            } else {
                player.setHoldDirection(0, 0, false);
            }
        }
        boolean wasTeleporting = player.isTeleporting();
        player.update(tileMap);
        npc.update(tileMap);
        npc.updateAI(entities);
        tileMap.update();
        camera.update();

        if (wasTeleporting && player.isTeleporting()) {
            camera.recalcInitialOffset();
        }

        // Clear D-pad input when teleportation finishes
        if (wasTeleporting && !player.isTeleporting()) {
            dpad.resetInputState(); // Correctly clear held directions
        }

        if (player.getX() == player.getSpawnX()
                && player.getY() == player.getSpawnY()
                && player.isInvulnerable()) {
            camera.recalcInitialOffset();
        }

        int chunkRange = 3;
        int playerChunkX = Math.floorDiv(player.getX(), CHUNK_SIZE);
        int playerChunkY = Math.floorDiv(player.getY(), CHUNK_SIZE);
        // Update active chunks around the player (radius = 3)
        tileMap.updateActiveChunks(playerChunkX, playerChunkY, 3);
        preloadChunks(playerChunkX, playerChunkY, chunkRange);
        teleportingBefore = player.isTeleporting();
    }

    private void preloadChunks(int centerX, int centerY, int radius) {
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                int cx = centerX + x;
                int cy = centerY + y;
                tileMap.getTile(cx * CHUNK_SIZE, cy * CHUNK_SIZE);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas == null || dpad == null || camera == null) return;

        boolean teleporting = player.isTeleporting();
        int tTimer = player.getTeleportTimer();
        int tDur = player.getTeleportDuration();

        int phase1 = tDur * 2 / 3;
        int phase2 = tDur / 3;

        canvas.drawColor(Color.BLACK); // Always clear screen

        if (teleporting) {
            if (tTimer > phase1) {
                drawWorld(canvas);
            } else if (tTimer > phase2) {
                drawWorldAndPlayer(canvas);
            } else {
                drawWorld(canvas);
                int alpha = (int) (255 * (float) tTimer / phase2);
                fadePaint.setAlpha(alpha);
                canvas.drawRect(0, 0, getWidth(), getHeight(), fadePaint);
            }
        } else {
            drawWorldAndPlayer(canvas);
        }

        if (!teleportingBefore) {
            dpad.draw(canvas); // Draw Dpad on top
        }

        if (player != null) {
            String coordinates =
                    String.format("Coordinates: {x: %d, y: %d}", player.getX(), player.getY());
            float x = getWidth() - 20;
            float y = 50;
            canvas.drawText(coordinates, x, y, coordinatesPaint);
        }
    }

    private void drawWorld(Canvas canvas) {
        canvas.save();
        canvas.translate(camera.getViewOffsetX(), camera.getViewOffsetY());
        tileMap.draw(canvas);
        canvas.restore();
    }

    private void drawWorldAndPlayer(Canvas canvas) {
        canvas.save();
        canvas.translate(camera.getViewOffsetX(), camera.getViewOffsetY());
        tileMap.draw(canvas);
        player.draw(canvas);
        npc.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (teleportingBefore || dpad == null) return true;

        boolean handled = dpad.onTouchEvent(e);
        if (handled) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                String dir = dpad.getHeldDirection();
                if (player.isSliding() && dir != null) {
                    Point d = dirMap.get(dir);
                    if (d.x == -player.getSlideDx() && d.y == -player.getSlideDy()) {
                        player.requestStopSliding();
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onDash(String direction) {
        Point d = dirMap.get(direction);
        if (d != null) {
            player.dash(d.x, d.y, tileMap);
        }
    }

    public void pause() {
        gameThread.setRunning(false);
        try {
            gameThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    public void resume() {
        if (gameThread == null || !gameThread.isAlive()) {
            gameThread = new GameThread(getHolder(), this);
            gameThread.setRunning(true);
            gameThread.start();
        }
    }

    public void saveState() {
        SharedPreferences prefs =
                getContext().getSharedPreferences("GameState", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("playerX", player.getX())
                .putInt("playerY", player.getY())
                .putInt("spawnX", player.getSpawnX())
                .putInt("spawnY", player.getSpawnY())
                .putInt("npcX", npc.getX())
                .putInt("npcY", npc.getY())
                .apply();
    }

    public void loadState() {
        SharedPreferences prefs =
                getContext().getSharedPreferences("GameState", Context.MODE_PRIVATE);
        player.setPosition(
                prefs.getInt("playerX", player.getSpawnX()),
                prefs.getInt("playerY", player.getSpawnY()));
        player.setSpawn(
                prefs.getInt("spawnX", player.getSpawnX()),
                prefs.getInt("spawnY", player.getSpawnY()));
        npc.setPosition(prefs.getInt("npcX", npc.getX()), prefs.getInt("npcY", npc.getY()));
        // Recenter the camera safely after resuming
        player.snapToPosition();
        npc.snapToPosition();
        if (camera != null) {
            camera.recalcInitialOffset();
        }
    }
}
