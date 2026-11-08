package com.mazegame;

public class Collectible {
    public enum Type { SPEED_BOOST, SCORE_POINTS }
    public int x, y;
    public Type type;
    public boolean collected = false;

    public Collectible(int x, int y, Type type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }
}