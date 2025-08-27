package com.dn.mygame;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Optimized Singleton TileLibrary for Java 1.7
 *
 * <p>Uses try-with-resources
 *
 * <p>Caches slices and full tiles
 *
 * <p>Parses JSON assets efficiently
 *
 * <p>Now uses only a single "flip" string in JSON ("horizontally-vertically").
 */
public class TileLibrary {
    private static volatile TileLibrary instance;
    private static final int TILE_SIZE = 64;
    private static final int SLICE_SIZE = 16;

    // ID management
    private final Map<String, Byte> nameToId = new HashMap<>();
    private final Map<Byte, String> idToName = new HashMap<>();
    private byte nextId = 1; // 0 reserved for empty

    // Existing fields
    private final Bitmap[] sliceBitmaps;
    private final Map<String, Integer> tileLogic = new HashMap<>();
    private final Map<String, Bitmap> fullTileCache = new HashMap<>();
    private final Rect drawRect = new Rect();

    /** Private constructor */
    private TileLibrary(Context ctx) {
        Context appCtx = ctx.getApplicationContext();
        sliceBitmaps = loadSlices(appCtx.getAssets(), "tileset.png");
        Tile.initialize(sliceBitmaps);

        try {
            JSONObject logicJson = loadJson(appCtx.getAssets(), "tile_logic.json");
            if (logicJson != null) parseLogic(logicJson);

            JSONObject piecesJson = loadJson(appCtx.getAssets(), "tile_pieces.json");
            if (piecesJson != null) buildFullTiles(piecesJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** Double-checked locking singleton */
    public static TileLibrary getInstance(Context ctx) {
        if (instance == null) {
            synchronized (TileLibrary.class) {
                if (instance == null) {
                    instance = new TileLibrary(ctx);
                }
            }
        }
        return instance;
    }

    public int getTileSize() {
        return TILE_SIZE;
    }

        /** New: Get byte ID for texture name */
    public byte getId(String name) {
        if (!nameToId.containsKey(name)) {
            nameToId.put(name, nextId);
            idToName.put(nextId, name);
            nextId++;
        }
        return nameToId.get(name);
    }

    /** New: Get texture name from byte ID */
    public String getName(byte id) {
        return idToName.getOrDefault(id, "");
    }

    /** Updated: Get logic constant by ID */
    public int getLogic(byte id) {
        String name = getName(id);
        return tileLogic.getOrDefault(name, TileMap.SPACE);
    }

    /** New: Get bitmap by ID */
    public Bitmap getBitmap(byte id) {
        return fullTileCache.get(getName(id));
    }

    /** Existing: Get bitmap by name (keep for compatibility) */
    public Bitmap getBitmap(String name) {
        return fullTileCache.get(name);
    }

    /** Load and slice master tileset into SLICE_SIZE pieces */
    private Bitmap[] loadSlices(AssetManager assets, String assetName) {
        Bitmap tileset;
        try (InputStream is = assets.open(assetName)) {
            tileset = BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load tileset", e);
        }

        int cols = tileset.getWidth() / SLICE_SIZE;
        int rows = tileset.getHeight() / SLICE_SIZE;
        Bitmap[] slices = new Bitmap[cols * rows];
        int idx = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                slices[idx++] =
                        Bitmap.createBitmap(
                                tileset, x * SLICE_SIZE, y * SLICE_SIZE, SLICE_SIZE, SLICE_SIZE);
            }
        }
        tileset.recycle();
        return slices;
    }

    /** Read an asset into a JSONObject */
    private JSONObject loadJson(AssetManager assets, String assetName) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = assets.open(assetName);
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return new JSONObject(sb.toString());
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Populate tileLogic map from JSON */
      /** Updated logic parser with ID registration */
    private void parseLogic(JSONObject root) throws JSONException {
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            int logicVal = TileMap.SPACE;
            try {
                logicVal = TileMap.class.getField(key).getInt(null);
            } catch (Exception ignored) {}

            JSONArray arr = root.getJSONArray(key);
            for (int i = 0; i < arr.length(); i++) {
                String tileName = arr.getString(i);
                tileLogic.put(tileName, logicVal);
                getId(tileName); // Register ID
            }
        }
    }


    /** Build full 64x64 tiles from JSON definitions using only "flip" string */
        /** Updated tile builder with ID registration */
    private void buildFullTiles(JSONObject root) throws JSONException {
        int half = TILE_SIZE / 2;
        Iterator<String> names = root.keys();

        while (names.hasNext()) {
            String name = names.next();
            getId(name); // Register main tile name
            
            JSONArray arr = root.getJSONArray(name);
            Bitmap full = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(full);

            for (int i = 0; i < 4; i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.getInt("id");
                String flipStr = o.optString("flip", "false-false");
                String[] parts = flipStr.split("-");
                boolean fh = Boolean.parseBoolean(parts[0]);
                boolean fv = Boolean.parseBoolean(parts[1]);
                int rot = o.optInt("rotate", 0);

                Tile part = Tile.get(id).transform(fh, fv, rot);
                int dx = (i & 1) * half;
                int dy = ((i >>> 1) & 1) * half;
                drawRect.set(dx, dy, dx + half, dy + half);
                canvas.drawBitmap(part.getBitmap(), null, drawRect, null);
            }
            fullTileCache.put(name, full);
        }
    }
}