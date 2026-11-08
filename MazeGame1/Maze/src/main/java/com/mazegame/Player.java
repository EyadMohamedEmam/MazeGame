package com.mazegame;

public class Player {
    public enum State { ALIVE, GHOST, SPECTATOR }

    public int id;
    public String name;
    public String colorHex;
    public int gridX, gridY;
    public State state;
    public double displayX, displayY;
    public long speedBoostUntil = 0;
    public long slowUntil = 0;
    public long reverseUntil = 0;
    public long fogBoostUntil = 0;         // NEW: fog boost timer
    public double speedMultiplier = 1.0;
    public long lastMoveTime = 0;          // from previous fix (movement cooldown)

    public Player(int id, String name, String colorHex) {
        this.id = id;
        this.name = name;
        this.colorHex = colorHex;
        this.state = State.ALIVE;
    }

    public void setPosition(int x, int y) {
        this.gridX = x;
        this.gridY = y;
        this.displayX = x * 24 + 12;
        this.displayY = y * 24 + 12;
    }
}