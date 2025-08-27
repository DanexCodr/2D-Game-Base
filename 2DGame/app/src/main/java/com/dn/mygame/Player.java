package com.dn.mygame;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Player extends Entity {
    private Paint paint;

    public Player(int startX, int startY, TileMap tileMap) {
        super(startX, startY, tileMap);
        paint = new Paint();
        paint.setColor(Color.BLUE);
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
        float drawX = getDrawX();
        float drawY = getDrawY();
        float halfTile = tileSize / 2f;
        paint.setColor(Color.BLUE);
        canvas.drawCircle(
                drawX + halfTile,
                drawY + halfTile,
                halfTile - 4,
                paint
        );

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
}