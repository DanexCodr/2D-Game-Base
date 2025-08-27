package com.dn.mygame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import java.util.*;

public class TileMap {
    // Logic constants
    public static final int SPAWN = 0;
    public static final int CHECKPOINT = 1;
    public static final int OBSTACLE = 2;
    public static final int SPACE = 3; // Traversable tileblock space
    public static final int SLIPPERY = 4;
    public static final int DYNAMIC_PIT = 5;
    public static final int PERMANENT_PIT = 6;
    public static final int JUMP_PAD = 7;
    public static final int TELEPORTER = 8;

    // Chunk config
    public static final int CHUNK_SIZE = 16;
    // Chunk config updates
    private static final int MAX_CACHED_CHUNKS = 100; // Added to limit total chunks

    // Track active chunks around the player
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkY = Integer.MIN_VALUE;

    // Dynamic-pit toggling
    private boolean dynamicPitActive = false;

    private long lastToggle = System.currentTimeMillis();
    private static final long TOGGLE_INTERVAL = 3000L;
    // Added pendingTeleporters map
    private final Map<String, List<Point>> pendingTeleporters = new HashMap<>();

    // Teleporter pairs
    private final Map<Point, Point> telePairs = new HashMap<>();

    // Chunk cache
    private final Map<String, TileData[][]> chunks = new HashMap<>();
    private final LinkedHashSet<String> activeChunks = new LinkedHashSet<>();

    // Tile library
    private final TileLibrary lib;

    // Update noise parameters (REPLACE EXISTING)
    private static final double BIOME_SCALE = 1 / 128.0; // Larger biome areas
    private static final double ELEVATION_BIAS = 0.2; // More mountainous areas
    private static final double MOISTURE_BIAS = -0.1; // Drier overall
    private static final double WATER_SCALE = 1 / 64.0; // Larger water bodies
    private static final double OCEAN_THRESHOLD = 0.55; // 55% of world as water
    private static final double DEEP_WATER_RATIO = 0.6; // 60% of water is deep

    public TileMap(Context ctx) {
        lib = TileLibrary.getInstance(ctx);
    }

    public int getTileSize() {
        return lib.getTileSize();
    }

    public void draw(Canvas canvas) {
        Rect clip = canvas.getClipBounds();
        int ts = getTileSize();

        int startX = clip.left / ts - CHUNK_SIZE;
        int endX = clip.right / ts + CHUNK_SIZE;
        int startY = clip.top / ts - CHUNK_SIZE;
        int endY = clip.bottom / ts + CHUNK_SIZE;

        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                TileData td = getTile(x, y);
                drawTile(canvas, x, y, td);
            }
        }
    }

    private void drawTile(Canvas canvas, int x, int y, TileData td) {
        int ts = getTileSize();

        // Get texture names from TileLibrary using IDs
        String baseName = lib.getName(td.baseId);
        String overlayName = (td.overlayId != 0) ? lib.getName(td.overlayId) : null;
        int logic = getLogic(td);

        // Handle dynamic pit overlay
        if (logic == DYNAMIC_PIT) {
            overlayName = dynamicPitActive ? "pit-active" : "pit-inactive";
        }

        // Draw base tile
        Bitmap baseBitmap = lib.getBitmap(baseName);
        if (baseBitmap != null) {
            canvas.drawBitmap(baseBitmap, x * ts, y * ts, null);
        }

        // Draw overlay with flipH check
        if (overlayName != null) {
            Bitmap overlayBitmap = lib.getBitmap(overlayName);
            if (overlayBitmap != null) {
                if ((td.flags & 0x01) != 0) { // Check flipH flag (bit 0)
                    Matrix matrix = new Matrix();
                    matrix.postScale(-1, 1);
                    overlayBitmap =
                            Bitmap.createBitmap(
                                    overlayBitmap,
                                    0,
                                    0,
                                    overlayBitmap.getWidth(),
                                    overlayBitmap.getHeight(),
                                    matrix,
                                    true);
                }
                canvas.drawBitmap(overlayBitmap, x * ts, y * ts, null);
            }
        }
    }

    public void update() {
        long now = System.currentTimeMillis();
        if (now - lastToggle >= TOGGLE_INTERVAL) {
            dynamicPitActive = !dynamicPitActive;
            lastToggle = now;
        }
    }

    public Point getSpawnPoint() {
        // Check if chunk (0,0) is already loaded
        String homeKey = "0_0";
        if (!chunks.containsKey(homeKey)) {
            chunks.put(homeKey, generateChunk(0, 0));
            activeChunks.add(homeKey);
        }

        TileData[][] home = chunks.get(homeKey);
        for (int y = 0; y < CHUNK_SIZE; y++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                if (getLogic(home[y][x]) == SPAWN) {
                    return new Point(x, y); // Chunk-relative coordinates
                }
            }
        }
        return new Point(CHUNK_SIZE / 2, CHUNK_SIZE / 2);
    }

    public void updateActiveChunks(int playerChunkX, int playerChunkY, int radius) {
        // Only update chunks if the player moves to a new chunk
        if (playerChunkX == lastPlayerChunkX && playerChunkY == lastPlayerChunkY) return;
        lastPlayerChunkX = playerChunkX;
        lastPlayerChunkY = playerChunkY;

        // Load chunks in a radius around the player
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                int cx = playerChunkX + x;
                int cy = playerChunkY + y;
                String key = cx + "_" + cy;
                if (!chunks.containsKey(key)) {
                    chunks.put(key, generateChunk(cx, cy));
                    activeChunks.add(key);
                }
            }
        }

        evictOldChunks(); // Remove older chunks outside the radius
    }

    public TileData getTile(int worldX, int worldY) {
        int cx = Math.floorDiv(worldX, CHUNK_SIZE); // Direct use of Math.floorDiv
        int cy = Math.floorDiv(worldY, CHUNK_SIZE);
        String key = cx + "_" + cy;

        if (!chunks.containsKey(key)) {
            chunks.put(key, generateChunk(cx, cy));
            activeChunks.add(key);
            evictOldChunks();
        }

        int lx = Math.floorMod(worldX, CHUNK_SIZE); // Direct use of Math.floorMod
        int ly = Math.floorMod(worldY, CHUNK_SIZE);
        return chunks.get(key)[ly][lx];
    }

    private TileData[][] generateChunk(int cx, int cy) {
        boolean[][] hasPit = new boolean[CHUNK_SIZE][CHUNK_SIZE];
        // Phase 1: Base terrain with pits, using hasPit for proximity checks
        TileData[][] baseChunk = createBaseChunk(cx, cy, hasPit);
        // Phase 2: Add pit clusters with proximity checks
        TileData[][] withPits = addPitClusters(baseChunk, cx, cy, hasPit);
        // Remaining phases unchanged
        TileData[][] connectedChunk = processConnections(withPits, cx, cy);
        return addVerticalStructures(connectedChunk, cx, cy);
    }

    private TileData[][] addPitClusters(
            TileData[][] baseChunk, int cx, int cy, boolean[][] hasPit) {
        Random rnd = new Random((cx * 397) ^ cy + 3);
        TileData[][] newChunk = deepCopy(baseChunk);

        if (rnd.nextFloat() < 0.1f) {
            int clusterX = rnd.nextInt(CHUNK_SIZE - 2) + 1;
            int clusterY = rnd.nextInt(CHUNK_SIZE - 2) + 1;

            for (int y = clusterY - 1; y <= clusterY + 1; y++) {
                for (int x = clusterX - 1; x <= clusterX + 1; x++) {
                    if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_SIZE) {
                        boolean canPlace = true;
                        // Proximity check remains the same
                        for (int dy = -1; dy <= 1 && canPlace; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int nx = x + dx;
                                int ny = y + dy;
                                if (nx >= 0
                                        && nx < CHUNK_SIZE
                                        && ny >= 0
                                        && ny < CHUNK_SIZE
                                        && hasPit[ny][nx]) {
                                    canPlace = false;
                                    break;
                                }
                            }
                        }
                        // Check base using ID
                        String currentBase = lib.getName(newChunk[y][x].baseId);
                        if (canPlace
                                && rnd.nextFloat() < 0.3f
                                && !currentBase.equals("shallow-water")) {
                            byte baseId = lib.getId(currentBase);
                            byte overlayId =
                                    lib.getId(rnd.nextBoolean() ? "pit-active" : "pit-inactive");
                            newChunk[y][x] = new TileData(baseId, overlayId);

                            // Mark proximity
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dx = -1; dx <= 1; dx++) {
                                    int nx = x + dx;
                                    int ny = y + dy;
                                    if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE) {
                                        hasPit[ny][nx] = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return newChunk;
    }

    private TileData[][] createBaseChunk(int cx, int cy, boolean[][] hasPit) {
        TileData[][] chunk = new TileData[CHUNK_SIZE][CHUNK_SIZE];
        Random rnd = new Random((cx * 397) ^ cy);

        for (int y = 0; y < CHUNK_SIZE; y++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                chunk[y][x] = createBaseTile(cx, cy, x, y, rnd, hasPit);
            }
        }
        return chunk;
    }

    // Add noise generation methods
    private double noise(double x, double y, double scale) {
        double fx = x * scale;
        double fy = y * scale;
        return (noise2D(fx, fy) * 0.5
                + (noise2D(fx * 2, fy * 2) * 0.25)
                + (noise2D(fx * 4, fy * 4) * 0.125));
    }

    private double noise2D(double x, double y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;

        double n0 = dotGridGradient(ix, iy, x, y);
        double n1 = dotGridGradient(ix + 1, iy, x, y);
        double nx0 = lerp(n0, n1, fx);

        n0 = dotGridGradient(ix, iy + 1, x, y);
        n1 = dotGridGradient(ix + 1, iy + 1, x, y);
        double nx1 = lerp(n0, n1, fx);

        return lerp(nx0, nx1, fy);
    }

    private double dotGridGradient(int ix, int iy, double x, double y) {
        Random gradRnd = new Random(ix * 374761393 + iy * 668265263);
        double angle = gradRnd.nextDouble() * Math.PI * 2;
        double dx = x - ix;
        double dy = y - iy;
        return dx * Math.cos(angle) + dy * Math.sin(angle);
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private String getBaseTerrain(int worldX, int worldY, Random rnd) {
        // Normalized noise values between -1 and 1
        double elevation = noise(worldX, worldY, BIOME_SCALE) + ELEVATION_BIAS;
        double moisture = noise(worldX + 1234, worldY + 5678, BIOME_SCALE) + MOISTURE_BIAS;

        // Normalize to 0-1 range
        elevation = Math.max(0, Math.min(1, (elevation + 1) / 2));
        moisture = Math.max(0, Math.min(1, (moisture + 1) / 2));

        // Water generation - add octave blending
        double water =
                noise(worldX, worldY, WATER_SCALE) * 0.7
                        + noise(worldX / 2.0, worldY / 2.0, WATER_SCALE / 2) * 0.3;
        water = (water + 1) / 2; // Normalize 0-1

        // Ocean core detection
        if (water > OCEAN_THRESHOLD) {
            return "shallow-water";
        }

        // Elevation-based biomes
        if (elevation > 0.65) {
            return "snowy-ground"; // Mountains
        } else if (elevation > 0.45) {
            // Hills
            return moisture > 0.5 ? "rocky-ground" : "grassy-ground";
        } else {
            // Lowlands
            return moisture > 0.7 ? "marshland" : moisture > 0.4 ? "grassy-ground" : "rocky-ground";
        }
    }

    // Updated createBaseTile method
    private TileData createBaseTile(int cx, int cy, int x, int y, Random rnd, boolean hasPit[][]) {
        int worldX = cx * CHUNK_SIZE + x;
        int worldY = cy * CHUNK_SIZE + y;

        // Check for pending teleporters first
        String currentChunkKey = cx + "_" + cy;
        List<Point> pending = pendingTeleporters.get(currentChunkKey);
        if (pending != null) {
            Iterator<Point> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Point p = iterator.next();
                if (p.x == x && p.y == y) {
                    iterator.remove();
                    byte baseId = lib.getId("rocky-ground");
                    byte overlayId = lib.getId("portal");
                    return new TileData(baseId, overlayId);
                }
            }
        }

        // Spawn point handling
        if (cx == 0 && cy == 0 && x == CHUNK_SIZE / 2 && y == CHUNK_SIZE / 2) {
            byte baseId = lib.getId("grassy-ground");
            byte overlayId = lib.getId("spawnpoint");
            return new TileData(baseId, overlayId);
        }

        // Teleporter generation
        if (rnd.nextFloat() < 0.002f) {
            cacheTeleportPair(cx, cy, x, y);
            byte baseId = lib.getId("rocky-ground");
            byte overlayId = lib.getId("portal");
            return new TileData(baseId, overlayId);
        }

        // Get base terrain from noise
        String base = getBaseTerrain(worldX, worldY, rnd);

        // Pit placement with proximity check
        if (!base.equals("shallow-water") && rnd.nextFloat() < 0.001f) {
            boolean canPlace = true;
            // Check 3x3 area around (x,y)
            for (int dy = -1; dy <= 1 && canPlace; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0
                            && nx < CHUNK_SIZE
                            && ny >= 0
                            && ny < CHUNK_SIZE
                            && hasPit[ny][nx]) {
                        canPlace = false;
                        break;
                    }
                }
            }
            if (canPlace) {
                // Mark 3x3 area to prevent nearby pits
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < CHUNK_SIZE && ny >= 0 && ny < CHUNK_SIZE) {
                            hasPit[ny][nx] = true;
                        }
                    }
                }
                byte baseId = lib.getId(base);
                byte overlayId = lib.getId(rnd.nextBoolean() ? "pit-active" : "pit-inactive");
                return new TileData(baseId, overlayId);
            }
        }

        // Add features based on terrain type
        switch (base) {
            case "shallow-water":
                double deepNoise = noise(worldX / 4.0, worldY / 4.0, WATER_SCALE / 4);
                deepNoise = (deepNoise + 1) / 2;
                if (deepNoise > DEEP_WATER_RATIO) {
                    byte baseId = lib.getId("shallow-water");
                    byte overlayId = lib.getId("deep-water");
                    return new TileData(baseId, overlayId);
                } else {
                    byte baseId = lib.getId("shallow-water");
                    byte overlayId = lib.getId(null);
                    return new TileData(baseId, overlayId);
                }

            case "snowy-ground":
                if (rnd.nextFloat() < 0.2) {
                    byte baseId = lib.getId(base);
                    byte overlayId = lib.getId("ice-sheet");
                    return new TileData(baseId, overlayId);
                } else {
                    byte baseId = lib.getId(base);
                    byte overlayId = lib.getId(null);
                    return new TileData(baseId, overlayId);
                }

            case "marshland":
                if (rnd.nextFloat() < 0.15) {
                    byte baseId = lib.getId(base);
                    byte overlayId = lib.getId("bush");
                    return new TileData(baseId, overlayId);
                } else {
                    byte baseId = lib.getId(base);
                    byte overlayId = lib.getId(null);
                    return new TileData(baseId, overlayId);
                }
            default: // Grassy/rocky ground
                if (rnd.nextFloat() < 0.009) {
                    byte baseId = lib.getId(base);
                    byte overlayId = lib.getId("bush");
                    return new TileData(baseId, overlayId);
                } else {
                    if (rnd.nextFloat() < 0.003) {
                        byte baseId = lib.getId(base);
                        byte overlayId = lib.getId("dead-trunk");
                        return new TileData(baseId, overlayId);
                    } else {
                        byte baseId = lib.getId(base);
                        byte overlayId = lib.getId(null);
                        return new TileData(baseId, overlayId);
                    }
                }
        }
    }

    private TileData[][] processConnections(TileData[][] baseChunk, int cx, int cy) {
        TileData[][] connected = new TileData[CHUNK_SIZE][CHUNK_SIZE];

        for (int y = 0; y < CHUNK_SIZE; y++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                connected[y][x] = getConnectedVariant(baseChunk[y][x], cx, cy, x, y);
            }
        }
        return connected;
    }

    // Updated connection handling (Handler for future connections
    private TileData getConnectedVariant(TileData original, int cx, int cy, int x, int y) {
        String overlayName = (original.overlayId != 0) ? lib.getName(original.overlayId) : null;
        String baseName = lib.getName(original.baseId);

        // Preserve pit appearances
        if (overlayName != null
                && (overlayName.equals("pit-active") || overlayName.equals("pit-inactive"))) {
            return original;
        }

        // Water connections
        if (baseName.startsWith("shallow-water")) {
            byte baseId = lib.getId(baseName);
            byte overlayId = lib.getId(overlayName);
            return new TileData(baseId, overlayId);
        }

        // Ice sheet connections
        if (overlayName != null && overlayName.equals("ice-sheet")) {
            byte baseId = lib.getId(baseName);
            byte overlayId = lib.getId("ice-sheet");
            return new TileData(baseId, overlayId);
        }

        return original;
    }

    private TileData[][] addVerticalStructures(TileData[][] chunk, int cx, int cy) {
        TileData[][] finalChunk = deepCopy(chunk);
        Random rnd = new Random((cx * 397) ^ cy + 1);

        for (int y = CHUNK_SIZE - 1; y >= 0; y--) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                if (canPlaceTree(finalChunk, x, y) && rnd.nextFloat() < 0.03f) {
                    placeTree(finalChunk, x, y, rnd.nextInt(3) + 2, rnd);
                }
            }
        }
        return finalChunk;
    }

    private boolean canPlaceTree(TileData[][] chunk, int x, int baseY) {
        if (baseY < 3) return false;
        for (int dy = 0; dy < 3; dy++) {
            int checkY = baseY - dy;
            if (checkY < 0) return false;
            TileData tile = chunk[checkY][x];
            String overlayName = (tile.overlayId != 0) ? lib.getName(tile.overlayId) : null;
            if (overlayName != null) {
                if (overlayName.equals("pit-active") || overlayName.equals("pit-inactive")) {
                    return false;
                }
                if (overlayName.startsWith("fit-tree-")) {
                    return false;
                }
            }
        }
        return true;
    }

    private void placeTree(TileData[][] chunk, int x, int baseY, int height, Random rnd) {
        for (int dy = 0; dy < height; dy++) {
            int y = baseY - dy;
            String part = getTreePart(dy, height);
            TileData original = chunk[y][x];
            byte baseId = lib.getId(lib.getName(original.baseId));
            byte overlayId = lib.getId(part);
            chunk[y][x] = new TileData(baseId, overlayId);
        }
    }

    private String getTreePart(int dy, int height) {
        if (dy == 0) return "fit-tree-base";
        if (dy == height - 1) return "fit-tree-top";
        return "fit-tree-continuous";
    }

    private TileData[][] deepCopy(TileData[][] original) {
        TileData[][] copy = new TileData[original.length][];
        for (int i = 0; i < original.length; i++) {
            copy[i] = Arrays.copyOf(original[i], original[i].length);
        }
        return copy;
    }

    // New method to preserve teleporters
    private void cacheTeleportersFromChunk(TileData[][] chunk, String chunkKey) {
        String[] parts = chunkKey.split("_");
        int cx = Integer.parseInt(parts[0]);
        int cy = Integer.parseInt(parts[1]);

        for (int y = 0; y < CHUNK_SIZE; y++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                if (getLogic(chunk[y][x]) == TELEPORTER) {
                    Point globalPos = new Point(cx * CHUNK_SIZE + x, cy * CHUNK_SIZE + y);
                    // Ensure pair remains in telePairs
                    if (!telePairs.containsKey(globalPos)) {
                        cacheTeleportPair(cx, cy, x, y);
                    }
                }
            }
        }
    }

    // Updated cacheTeleportPair for global coordinates
    private void cacheTeleportPair(int cx, int cy, int x, int y) {
        Point p1 = new Point(cx * CHUNK_SIZE + x, cy * CHUNK_SIZE + y);
        Random rnd = new Random((cx * 397) ^ cy); // Deterministic seed

        // Generate pair coordinates using chunk-based seed
        int dx = rnd.nextInt(30 * CHUNK_SIZE) + 20 * CHUNK_SIZE;
        int dy = rnd.nextInt(30 * CHUNK_SIZE) + 20 * CHUNK_SIZE;
        Point p2 = new Point(p1.x + dx, p1.y + dy);

        telePairs.put(p1, p2);
        telePairs.put(p2, p1);
        addPendingTeleporter(p2.x, p2.y);
    }

    // Global coordinate handling for pending teleporters
    private void addPendingTeleporter(int worldX, int worldY) {
        int cx = Math.floorDiv(worldX, CHUNK_SIZE);
        int cy = Math.floorDiv(worldY, CHUNK_SIZE);
        String chunkKey = cx + "_" + cy;

        List<Point> pending =
                pendingTeleporters.getOrDefault(
                        chunkKey, new ArrayList<Point>() // Explicit type
                        );
        pending.add(new Point(worldX - (cx * CHUNK_SIZE), worldY - (cy * CHUNK_SIZE)));
        pendingTeleporters.put(chunkKey, pending);
    }

    // Modified evictOldChunks to prevent memory leaks
    private void evictOldChunks() {
        while (chunks.size() > MAX_CACHED_CHUNKS) {
            String oldest = activeChunks.iterator().next();
            activeChunks.remove(oldest);

            // Preserve teleporters before eviction
            cacheTeleportersFromChunk(chunks.get(oldest), oldest);
            chunks.remove(oldest); // Critical addition!
        }
    }

    // Replace entire method with:
    private int getLogic(TileData td) {
        byte id = (td.overlayId != 0) ? td.overlayId : td.baseId;
        return lib.getLogic(id); // Use byte-based logic check
    }

    public boolean isTraversable(int x, int y) {
        return getLogic(getTile(x, y)) != OBSTACLE;
    }

    public boolean isObstacle(int x, int y) {
        return getLogic(getTile(x, y)) == OBSTACLE;
    }

    public boolean isPit(int x, int y) {
        int logic = getLogic(getTile(x, y));
        return logic == PERMANENT_PIT || (logic == DYNAMIC_PIT && dynamicPitActive);
    }

    public boolean isJumpPad(int x, int y) {
        return getLogic(getTile(x, y)) == JUMP_PAD;
    }

    public boolean isTeleporter(int x, int y) {
        return getLogic(getTile(x, y)) == TELEPORTER;
    }

    public boolean isCheckpoint(int x, int y) {
        return getLogic(getTile(x, y)) == CHECKPOINT;
    }

    public boolean isSlippery(int x, int y) {
        return getLogic(getTile(x, y)) == SLIPPERY;
    }

    public Point getTeleporterDestination(int x, int y) {
        return telePairs.get(new Point(x, y));
    }

    public static class Point {
        public final int x, y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return x == p.x && y == p.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    public static class TileData {
        public final byte baseId;
        public final byte overlayId;
        public final byte flags;

        // Remove static idMap and getOrCreateId
        public TileData(byte baseId, byte overlayId) {
            this.baseId = baseId;
            this.overlayId = overlayId;
            this.flags = 0;
        }
    }
}
