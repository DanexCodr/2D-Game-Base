package com.dn.mygame;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.SparseArray;
import android.view.MotionEvent;
import java.util.*;

public class Dpad {
    // Direction constants
    public static final String DIR_UP = "up";
    public static final String DIR_DOWN = "down";
    public static final String DIR_LEFT = "left";
    public static final String DIR_RIGHT = "right";

    // Diagonal direction constants
    public static final String DIR_UP_LEFT = "up-left";
    public static final String DIR_UP_RIGHT = "up-right";
    public static final String DIR_DOWN_LEFT = "down-left";
    public static final String DIR_DOWN_RIGHT = "down-right";

    public static final String DIR_CENTER = "center";

    private static final String[] DIRECTIONS = {
        DIR_UP,
        DIR_DOWN,
        DIR_LEFT,
        DIR_RIGHT,
        DIR_UP_LEFT,
        DIR_UP_RIGHT,
        DIR_DOWN_LEFT,
        DIR_DOWN_RIGHT,
        DIR_CENTER
    };

  // Unicode symbols for directions (same order as DIRECTIONS array)
    private static final String[] DIRECTION_SYMBOLS = {
        "↑",    // DIR_UP
        "↓",    // DIR_DOWN
        "←",    // DIR_LEFT
        "→",    // DIR_RIGHT
        "↖",    // DIR_UP_LEFT
        "↗",    // DIR_UP_RIGHT
        "↙",    // DIR_DOWN_LEFT
        "↘",    // DIR_DOWN_RIGHT
        "●"     // DIR_CENTER
    };

    // Timing constants
    private static final long MULTI_TAP_THRESHOLD = 400; // ms for double-tap detection
    private static final long RUN_HOLD_THRESHOLD = 500; // ms to hold for running

    public interface OnDashListener {
        void onDash(String direction);
    }

    // Layout properties
    private final int dpadSize;
    private final int marginX;
    private final int marginY;

    // Visual elements
    private final Rect[] directionRects = new Rect[8];
    private final Rect centerRect = new Rect();
    private boolean centerHeld;
    private final Paint dpadPaint;
    // Text paints for symbols
    private final Paint inactiveTextPaint;
    private final Paint activeTextPaint;
    // Input state
    private String heldDirection;
    private boolean isTouchingDpad;

    // Multi-tap and running state
    private final Map<String, Long> lastTapTime = new HashMap<String, Long>();
    private final Map<String, Integer> tapCount = new HashMap<String, Integer>();
    private boolean readyToRun;
    private boolean running;
    private long holdStartTime;

    // Visual elements
    private final Paint activePaint; // Paint for active direction
    private String lastTappedDirection;
    private long tapHighlightExpireTime;

    // Listener
    private OnDashListener dashListener;

    private final SparseArray<String> activePointers = new SparseArray<>();

    public Dpad(int screenWidth, int screenHeight, int dpadSize, int marginX, int marginY) {
        for (int i = 0; i < directionRects.length; i++) {
            directionRects[i] = new Rect();
        }
        this.dpadSize = dpadSize;
        this.marginX = marginX;
        this.marginY = marginY;

        // Initialize paint
        dpadPaint = new Paint();
        dpadPaint.setColor(Color.argb(150, 255, 255, 255)); // Semi-transparent white
        activePaint = new Paint();
        activePaint.setColor(Color.argb(200, 0, 255, 0)); // Brighter semi-transparent green
                // Initialize text paints
        inactiveTextPaint = new Paint();
        inactiveTextPaint.setColor(Color.BLACK);
        inactiveTextPaint.setTextSize(dpadSize * 0.6f);
        inactiveTextPaint.setTextAlign(Paint.Align.CENTER);
        inactiveTextPaint.setAntiAlias(true);

        activeTextPaint = new Paint(inactiveTextPaint);
        activeTextPaint.setColor(Color.WHITE);
        // Initialize rects
        for (int i = 0; i < directionRects.length; i++) {
            directionRects[i] = new Rect();
        }

        updateScreenSize(screenWidth, screenHeight);
    }

    // Update updateScreenSize to position diagonal Rects
    public void updateScreenSize(int screenWidth, int screenHeight) {
        int centerX = marginX;
        int centerY = screenHeight - marginY;
        int buttonSize = dpadSize;

        // Existing main directions
        directionRects[0].set(centerX, centerY - buttonSize, centerX + buttonSize, centerY); // Up
        directionRects[1].set(
                centerX,
                centerY + buttonSize,
                centerX + buttonSize,
                centerY + 2 * buttonSize); // Down
        directionRects[2].set(centerX - buttonSize, centerY, centerX, centerY + buttonSize); // Left
        directionRects[3].set(
                centerX + buttonSize,
                centerY,
                centerX + 2 * buttonSize,
                centerY + buttonSize); // Right

        // Diagonal directions
        directionRects[4].set(
                centerX - buttonSize, centerY - buttonSize, centerX, centerY); // Up-Left
        directionRects[5].set(
                centerX + buttonSize,
                centerY - buttonSize,
                centerX + 2 * buttonSize,
                centerY); // Up-Right
        directionRects[6].set(
                centerX - buttonSize,
                centerY + buttonSize,
                centerX,
                centerY + 2 * buttonSize); // Down-Left
        directionRects[7].set(
                centerX + buttonSize,
                centerY + buttonSize,
                centerX + 2 * buttonSize,
                centerY + 2 * buttonSize); // Down-Right
        // Center button (same size with other buttons but is at the center of all of it)
        centerRect.set(centerX, centerY, centerX + buttonSize, centerY + buttonSize);
    }

    public void draw(Canvas canvas) {
        if (canvas == null) return;

        long currentTime = System.currentTimeMillis();
        boolean showTapHighlight = currentTime < tapHighlightExpireTime;

        // Draw direction buttons with symbols
        for (int i = 0; i < directionRects.length; i++) {
            Rect rect = directionRects[i];
            String direction = DIRECTIONS[i];
            boolean isActive = direction.equals(heldDirection)
                    || (showTapHighlight && direction.equals(lastTappedDirection));

            // Draw button background
            canvas.drawRect(rect, isActive ? activePaint : dpadPaint);

            // Draw direction symbol
            String symbol = DIRECTION_SYMBOLS[i];
            Paint textPaint = isActive ? activeTextPaint : inactiveTextPaint;
            float x = rect.centerX();
            float y = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2;
            canvas.drawText(symbol, x, y, textPaint);
        }

        // Draw center button
        canvas.drawRect(centerRect, centerHeld ? activePaint : dpadPaint);
        String centerSymbol = DIRECTION_SYMBOLS[8];
        Paint centerTextPaint = centerHeld ? activeTextPaint : inactiveTextPaint;
        float x = centerRect.centerX();
        float y = centerRect.centerY() - (centerTextPaint.descent() + centerTextPaint.ascent()) / 2;
        canvas.drawText(centerSymbol, x, y, centerTextPaint);
    }

    // Ensure detectDirection checks all 8 directions
    private String detectDirection(int x, int y) {
        if (centerRect.contains(x, y)) {
            return DIR_CENTER;
        }
        for (int i = 0; i < directionRects.length; i++) {
            if (directionRects[i].contains(x, y)) {
                return DIRECTIONS[i];
            }
        }
        return null;
    }

    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        boolean handled = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                {
                    int x = (int) event.getX(pointerIndex);
                    int y = (int) event.getY(pointerIndex);
                    String dir = detectDirection(x, y);
                    if (dir != null) {
                        activePointers.put(pointerId, dir);
                        isTouchingDpad = true;
                        updateHeldState();
                        handled = true;
                    }
                    break;
                }
            case MotionEvent.ACTION_MOVE:
                {
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        int pid = event.getPointerId(i);
                        int x = (int) event.getX(i);
                        int y = (int) event.getY(i);
                        String newDir = detectDirection(x, y);
                        String oldDir = activePointers.get(pid, null);
                        if (oldDir != null && !oldDir.equals(newDir)) {
                            activePointers.put(pid, newDir);
                            updateHeldState();
                        }
                    }
                    handled = true;
                    break;
                }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                {
                    activePointers.remove(pointerId);
                    updateHeldState();
                    if (activePointers.size() == 0) {
                        isTouchingDpad = false;
                        resetInputState();
                    }
                    handled = true;
                    break;
                }
            default:
                handled = false;
        }

        return handled;
    }

    private void updateHeldState() {
        boolean newCenterHeld = false;
        String newHeldDirection = null;

        // Check if any pointer is on the center
        for (int i = 0; i < activePointers.size(); i++) {
            String dir = activePointers.valueAt(i);
            if (DIR_CENTER.equals(dir)) {
                newCenterHeld = true;
                break;
            }
        }

        // If center is held, find the first non-center direction
        if (newCenterHeld) {
            for (int i = 0; i < activePointers.size(); i++) {
                String dir = activePointers.valueAt(i);
                if (!DIR_CENTER.equals(dir)) {
                    newHeldDirection = dir;
                    break;
                }
            }
        } else {
            // If not center held, use the first active direction
            if (activePointers.size() > 0) {
                newHeldDirection = activePointers.valueAt(0);
            }
        }

        centerHeld = newCenterHeld;
        heldDirection = newHeldDirection;

        // Handle action down for directions when center is not held
        if (!newCenterHeld && newHeldDirection != null && isTouchingDpad) {
            handleActionDown(newHeldDirection);
        }
    }

    private void handleActionDown(String direction) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastTapTime.get(direction);
        int count = tapCount.containsKey(direction) ? tapCount.get(direction) : 0;

        // Reset count if too much time passed since last tap
        if (lastTime == null || (currentTime - lastTime) >= MULTI_TAP_THRESHOLD) {
            count = 1;
        } else {
            count++;
            // Set tap highlight
            lastTappedDirection = direction;
            tapHighlightExpireTime = System.currentTimeMillis() + 200; // Highlight for 200ms
        }

        // Update tap tracking
        tapCount.put(direction, count);
        lastTapTime.put(direction, currentTime);

        // Handle multi-tap actions
        if (count == 2) {
            readyToRun = true;
            holdStartTime = currentTime;
            running = false;
        } else if (count >= 3) {
            if (dashListener != null) {
                dashListener.onDash(direction);
            }
            tapCount.put(direction, 0); // Reset after dash
        }

        heldDirection = direction;
    }

    public boolean isCenterHeld() {
        return centerHeld;
    }

    public void resetInputState() {
        heldDirection = null;
        running = false;
        readyToRun = false;
        isTouchingDpad = false;
    }

    public void update() {
        if (readyToRun
                && !running
                && (System.currentTimeMillis() - holdStartTime) > RUN_HOLD_THRESHOLD) {
            running = true;
            readyToRun = false;
        }
    }

    public String getHeldDirection() {
        return heldDirection;
    }

    public boolean isRunning() {
        return running;
    }

    public void setOnDashListener(OnDashListener listener) {
        dashListener = listener;
    }
}