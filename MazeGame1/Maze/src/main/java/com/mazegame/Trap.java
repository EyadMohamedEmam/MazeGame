package com.mazegame;

public class Trap {
    public enum Type { SLOW, REVERSE }
    public int x, y;
    public Type type;

    public Trap(int x, int y, Type type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }
}