package com.dn.mygame;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.util.List;

public class NPC extends Entity {
    private Paint paint;

    public NPC(int startX, int startY, TileMap tileMap) {
        super(startX, startY, tileMap);
        paint = new Paint();
        paint.setColor(Color.RED);
    }

    @Override
    public void draw(Canvas canvas) {
            if (isTeleporting()) {
            float teleportProgress = (float) getTeleportTimer() / 
                                   getTeleportDuration();
            paint.setAlpha((int) (255 * (1 - teleportProgress)));
        } else {
            paint.setAlpha(255);
        }

        float tileSize = getTileMap().getTileSize();
        float halfTile = tileSize / 2f;
        float drawX = getDrawX();
        float drawY = getDrawY();

        paint.setColor(Color.RED);
        canvas.drawCircle(
                drawX + halfTile,
                drawY + halfTile,
                halfTile - 4,
                paint
        );

        // Example eye drawing for NPC
        paint.setColor(Color.WHITE);
        float eyeOffset = 10f;
        float pupilSize = 5f;
        int facingDx = getFacingDx();
        int facingDy = getFacingDy();
        float eyeCenterX = drawX + halfTile + facingDx * eyeOffset;
        float eyeCenterY = drawY + halfTile + facingDy * eyeOffset;

        if (facingDx != 0) {
            canvas.drawCircle(eyeCenterX, eyeCenterY - pupilSize, pupilSize, paint);
            canvas.drawCircle(eyeCenterX, eyeCenterY + pupilSize, pupilSize, paint);
        } else {
            canvas.drawCircle(eyeCenterX - pupilSize, eyeCenterY, pupilSize, paint);
            canvas.drawCircle(eyeCenterX + pupilSize, eyeCenterY, pupilSize, paint);
        }

        if (isInvulnerable() && 
           (getInvulnerabilityTimer() / 2) % 2 == 0) {
            return;
        }
        paint.setAlpha(255);
    }

    public void updateAI(List<Entity> entities) {
    if (Math.random() < 0.05) {
        // Randomly choose between 8 directions
        int[][] directions = {
            {-1, 0},  // Left
            {1, 0},   // Right
            {0, -1},  // Up
            {0, 1},   // Down
            {-1, -1}, // Up-Left
            {-1, 1},  // Down-Left
            {1, -1},  // Up-Right
            {1, 1}    // Down-Right
        };
        
        int index = (int) (Math.random() * directions.length);
        int dx = directions[index][0];
        int dy = directions[index][1];
        
        move(dx, dy, getTileMap(), entities);
    }
}
}