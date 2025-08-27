package com.dn.mygame;

import android.graphics.Bitmap;
import android.graphics.Matrix;

public class Tile {
    private static Bitmap[] sourceBitmaps;
    private final Bitmap bitmap;

    private Tile(Bitmap bmp) {
        this.bitmap = bmp;
    }

    /** Initializes the shared source bitmap array. */
    public static void initialize(Bitmap[] bitmaps) {
        sourceBitmaps = bitmaps;
    }

    /** Retrieves a Tile by its slice ID. */
    public static Tile get(int id) {
        if (sourceBitmaps != null && id >= 0 && id < sourceBitmaps.length) {
            return new Tile(sourceBitmaps[id]);
        }
        return null;
    }

    /** Returns the underlying Bitmap. */
    public Bitmap getBitmap() {
        return bitmap;
    }

    /**
     * Flip and rotate the tile around its center in one matrix operation.
     *
     * @param flipH true to flip horizontally
     * @param flipV true to flip vertically
     * @param rotateDeg rotation in degrees (must be a multiple of 90)
     * @return a new Tile containing the transformed bitmap
     */
    public Tile transform(boolean flipH, boolean flipV, int rotateDeg) {
        // Quick no-op if nothing to do
        int normalized = ((rotateDeg % 360) + 360) % 360;
        if (!flipH && !flipV && normalized == 0) {
            return this;
        }

        Matrix m = new Matrix();
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        float cx = w / 2f, cy = h / 2f;

        // 1) Rotate around center
        if (normalized != 0) {
            m.postRotate(normalized, cx, cy);
        }

        // 2) Flip around center
        float sx = flipH ? -1f : 1f;
        float sy = flipV ? -1f : 1f;
        if (flipH || flipV) {
            m.postScale(sx, sy, cx, cy);
        }

        // Create transformed bitmap
        Bitmap transformed = Bitmap.createBitmap(bitmap, 0, 0, w, h, m, true);
        return new Tile(transformed);
    }

    /** Legacy method for flipping only (zero rotation). */
    public Tile reverse(boolean flipH, boolean flipV) {
        return transform(flipH, flipV, 0);
    }
}
