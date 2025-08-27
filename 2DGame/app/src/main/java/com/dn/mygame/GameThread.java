package com.dn.mygame;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

public class GameThread extends Thread {
    private SurfaceHolder surfaceHolder;
    private GameView gameView;
    private boolean running;

    public GameThread(SurfaceHolder holder, GameView view) {
        surfaceHolder = holder;
        gameView = view;
    }

    public void setRunning(boolean run) {
        running = run;
    }

@Override
public void run() {
    Canvas canvas;
    long targetTime = 1000 / 60; // Target 60 FPS (~16ms per frame)
    
    while (running) {
        long startTime = System.currentTimeMillis();
        canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            synchronized (surfaceHolder) {
                gameView.update();
                gameView.draw(canvas);
            }
        } finally {
            if (canvas != null)
                surfaceHolder.unlockCanvasAndPost(canvas);
        }

        // Frame timing control
        long frameTime = System.currentTimeMillis() - startTime;
        if (frameTime < targetTime) {
            try {
                sleep(targetTime - frameTime);
            } catch (InterruptedException e) { /* Handle */ }
        }
    }
}
}
