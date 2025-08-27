package com.dn.mygame;

public class StatusComponent {
    private boolean isInvulnerable = false;
    private int invulnerabilityTimer = 0;

    public void setInvulnerable(boolean invulnerable) {
        isInvulnerable = invulnerable;
        if (invulnerable) invulnerabilityTimer = 30; // 0.5 seconds at 60 FPS
    }

    public void update() {
        if (isInvulnerable && --invulnerabilityTimer <= 0) {
            isInvulnerable = false;
        }
    }

    // Getters
    public boolean isInvulnerable() { return isInvulnerable; }
    
    public int getInvulnerabilityTimer() { return invulnerabilityTimer; }
}