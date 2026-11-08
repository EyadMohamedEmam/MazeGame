package com.mazegame;

import java.util.*;

/**
 * Generates perfect mazes using Recursive Backtracker.
 * Supports seeding, and "moving walls" by toggling a few interior walls
 * while keeping the maze fully connected.
 */
public class MazeGenerator {

    public static class Cell {
        // walls: top, right, bottom, left
        public boolean[] walls = {true, true, true, true};
        public boolean visited = false;
    }

    public static class Maze {
        public final int width, height;
        public final Cell[][] grid;
        public final int startX, startY, exitX, exitY;
        // convenient wall arrays for fast toggling
        // horWalls[x][y] = wall between (x,y-1) and (x,y)   (y from 0..height)
        // verWalls[x][y] = wall between (x-1,y) and (x,y)   (x from 0..width)
        public final boolean[][] horWalls;
        public final boolean[][] verWalls;

        public Maze(int width, int height, Cell[][] grid) {
            this.width = width;
            this.height = height;
            this.grid = grid;
            startX = 0; startY = 0;
            exitX = width - 1; exitY = height - 1;
            // build wall arrays
            horWalls = new boolean[width][height + 1];
            verWalls = new boolean[width + 1][height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    Cell c = grid[x][y];
                    // top wall of cell (x,y) -> horWalls[x][y]
                    horWalls[x][y] = c.walls[0];   // top
                    // bottom wall of cell (x,y) -> horWalls[x][y+1]
                    horWalls[x][y + 1] = c.walls[2];
                    // left wall -> verWalls[x][y]
                    verWalls[x][y] = c.walls[3];
                    // right wall -> verWalls[x+1][y]
                    verWalls[x + 1][y] = c.walls[1];
                }
            }
        }
    }

    /**
     * Generate a perfect maze using recursive backtracking.
     */
    public static Maze generateMaze(int width, int height, long seed) {
        Cell[][] grid = new Cell[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                grid[x][y] = new Cell();

        Random rand = new Random(seed);
        Stack<int[]> stack = new Stack<>();
        int sx = 0, sy = 0;
        grid[sx][sy].visited = true;
        stack.push(new int[]{sx, sy});

        int[][] dirs = {{0,-1}, {1,0}, {0,1}, {-1,0}}; // up, right, down, left

        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            int cx = cur[0], cy = cur[1];
            List<Integer> neighbours = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int nx = cx + dirs[i][0], ny = cy + dirs[i][1];
                if (nx >= 0 && ny >= 0 && nx < width && ny < height && !grid[nx][ny].visited)
                    neighbours.add(i);
            }
            if (neighbours.isEmpty()) {
                stack.pop();
            } else {
                int dirIdx = neighbours.get(rand.nextInt(neighbours.size()));
                int nx = cx + dirs[dirIdx][0], ny = cy + dirs[dirIdx][1];
                // remove walls between current and chosen
                grid[cx][cy].walls[dirIdx] = false;               // direction from current
                grid[nx][ny].walls[(dirIdx + 2) % 4] = false;     // opposite direction
                grid[nx][ny].visited = true;
                stack.push(new int[]{nx, ny});
            }
        }
        return new Maze(width, height, grid);
    }

    /**
     * Modify the maze by toggling a few interior walls, only if full connectivity is maintained.
     * Returns a list of toggled walls in the format ["H:x:y","V:x:y"] that clients can apply.
     */
    public static List<String> modifyWithMovingWalls(Maze maze, Random rand) {
        List<String> toggles = new ArrayList<>();
        int attempts = 0;
        // try up to 10 times to find a valid toggle
        while (attempts < 50) {
            // pick a random interior horizontal wall
            // horWall indices: x in [0,width-1], y in [1,height-1]  (interior not border)
            int hx = rand.nextInt(maze.width);
            int hy = 1 + rand.nextInt(maze.height - 1);
            // pick a random interior vertical wall
            int vx = 1 + rand.nextInt(maze.width - 1);
            int vy = rand.nextInt(maze.height);

            // toggle horizontal wall
            boolean oldH = maze.horWalls[hx][hy];
            maze.horWalls[hx][hy] = !oldH;
            // also update the two adjacent cells' walls
            if (hy > 0) {
                maze.grid[hx][hy-1].walls[2] = maze.horWalls[hx][hy]; // bottom of upper
            }
            if (hy < maze.height) {
                maze.grid[hx][hy].walls[0] = maze.horWalls[hx][hy];   // top of lower
            }

            // toggle vertical wall
            boolean oldV = maze.verWalls[vx][vy];
            maze.verWalls[vx][vy] = !oldV;
            if (vx > 0) {
                maze.grid[vx-1][vy].walls[1] = maze.verWalls[vx][vy]; // right of left cell
            }
            if (vx < maze.width) {
                maze.grid[vx][vy].walls[3] = maze.verWalls[vx][vy];   // left of right cell
            }

            // check connectivity between start and exit
            if (isConnected(maze)) {
                toggles.add("H:" + hx + ":" + hy);
                toggles.add("V:" + vx + ":" + vy);
                return toggles;
            } else {
                // revert toggles
                maze.horWalls[hx][hy] = oldH;
                if (hy > 0) maze.grid[hx][hy-1].walls[2] = oldH;
                if (hy < maze.height) maze.grid[hx][hy].walls[0] = oldH;
                maze.verWalls[vx][vy] = oldV;
                if (vx > 0) maze.grid[vx-1][vy].walls[1] = oldV;
                if (vx < maze.width) maze.grid[vx][vy].walls[3] = oldV;
            }
            attempts++;
        }
        return toggles; // empty if none found
    }

    private static boolean isConnected(Maze maze) {
        boolean[][] visited = new boolean[maze.width][maze.height];
        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{maze.startX, maze.startY});
        visited[maze.startX][maze.startY] = true;
        int[][] dirs = {{0,-1},{1,0},{0,1},{-1,0}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1];
            if (x == maze.exitX && y == maze.exitY) return true;
            for (int i = 0; i < 4; i++) {
                if (!maze.grid[x][y].walls[i]) {
                    int nx = x + dirs[i][0], ny = y + dirs[i][1];
                    if (nx >= 0 && ny >= 0 && nx < maze.width && ny < maze.height && !visited[nx][ny]) {
                        visited[nx][ny] = true;
                        q.add(new int[]{nx, ny});
                    }
                }
            }
        }
        return false;
    }
}