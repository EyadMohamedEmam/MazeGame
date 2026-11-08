package com.mazegame;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javafx.application.Platform;
import javafx.scene.paint.Color;

public class MazeClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean running = false;
    public GameState state = new GameState();
    public int playerId = -1;
    private Queue<String> messageQueue = new ConcurrentLinkedQueue<>();

    public void connect(String host, int port, String playerName) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out.println("JOIN:" + playerName);
        running = true;
        new Thread(this::listen).start();
    }

    private void listen() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                final String msg = line;
                Platform.runLater(() -> processMessage(msg));
            }
        } catch (IOException e) {
            if (running) e.printStackTrace();
        }
    }

    public void sendMove(String direction) {
        if (out != null) out.println("MOVE:" + direction);
    }

    public void sendMessage(String msg) {          // NEW
        if (out != null) out.println(msg);
    }

    public void disconnect() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private void processMessage(String msg) {
        String[] parts = msg.split(":");
        if (parts.length == 0) return;
        switch (parts[0]) {
            case "WELCOME":
                playerId = Integer.parseInt(parts[1]);
                state.myPlayerId = playerId;
                state.myColorHex = parts[2];
                break;
            case "START":
                state.mazeWidth = Integer.parseInt(parts[1]);
                state.mazeHeight = Integer.parseInt(parts[2]);
                state.mazeSeed = Long.parseLong(parts[3]);
                state.startX = Integer.parseInt(parts[4]);
                state.startY = Integer.parseInt(parts[5]);
                state.exitX = Integer.parseInt(parts[6]);
                state.exitY = Integer.parseInt(parts[7]);
                state.generateMaze();
                state.reset();
                break;
            case "TRAP":
                int tx = Integer.parseInt(parts[1]);
                int ty = Integer.parseInt(parts[2]);
                Trap.Type ttype = Trap.Type.valueOf(parts[3]);
                state.traps.add(new Trap(tx, ty, ttype));
                break;
            case "COLLECTIBLE":
                int cx = Integer.parseInt(parts[1]);
                int cy = Integer.parseInt(parts[2]);
                Collectible.Type ctype = Collectible.Type.valueOf(parts[3]);
                state.collectibles.add(new Collectible(cx, cy, ctype));
                break;
            case "COLLECTED":
                int collX = Integer.parseInt(parts[1]);
                int collY = Integer.parseInt(parts[2]);
                state.collectibles.removeIf(c -> c.x == collX && c.y == collY);
                break;
            case "POS":
                state.otherPlayers.clear();
                for (int i = 1; i < parts.length; i++) {
                    String[] playerData = parts[i].split(",");
                    if (playerData.length != 4) continue;
                    int pid = Integer.parseInt(playerData[0]);
                    int px = Integer.parseInt(playerData[1]);
                    int py = Integer.parseInt(playerData[2]);
                    int pstateOrd = Integer.parseInt(playerData[3]);
                    if (pid == playerId) {
                        state.myGridX = px;
                        state.myGridY = py;
                        state.myState = Player.State.values()[pstateOrd];
                        if (state.myPlayer == null) {
                            state.myPlayer = new Player(pid, "Me", state.myColorHex);
                        }
                        state.myPlayer.gridX = px; state.myPlayer.gridY = py;
                        state.myPlayer.state = state.myState;
                    } else {
                        Player other = state.playerMap.get(pid);
                        if (other == null) {
                            other = new Player(pid, "P"+pid, "#808080");
                            state.playerMap.put(pid, other);
                        }
                        other.gridX = px; other.gridY = py;
                        other.state = Player.State.values()[pstateOrd];
                        state.otherPlayers.add(other);
                    }
                }
                break;
            case "WINNER":
                int winnerId = Integer.parseInt(parts[1]);
                String winnerName = parts[2];
                state.winnerName = winnerName;
                state.winnerId = winnerId;
                break;
            case "EFFECT":
                int effectPlayerId = Integer.parseInt(parts[1]);
                String effectType = parts[2];
                long duration = Long.parseLong(parts[3]);
                if (effectPlayerId == playerId) {
                    if (effectType.equals("FOG_BOOST")) {
                        state.fogBoostUntil = System.currentTimeMillis() + duration;
                    }
                }
                break;
            case "SCORE":
                break;
            case "WALL_TOGGLE":
                String toggleType = parts[1];
                int wx = Integer.parseInt(parts[2]);
                int wy = Integer.parseInt(parts[3]);
                if (state.maze != null) {
                    if (toggleType.equals("H")) {
                        state.maze.horWalls[wx][wy] = !state.maze.horWalls[wx][wy];
                        if (wy > 0) state.maze.grid[wx][wy-1].walls[2] = state.maze.horWalls[wx][wy];
                        if (wy < state.mazeHeight) state.maze.grid[wx][wy].walls[0] = state.maze.horWalls[wx][wy];
                    } else if (toggleType.equals("V")) {
                        state.maze.verWalls[wx][wy] = !state.maze.verWalls[wx][wy];
                        if (wx > 0) state.maze.grid[wx-1][wy].walls[1] = state.maze.verWalls[wx][wy];
                        if (wx < state.mazeWidth) state.maze.grid[wx][wy].walls[3] = state.maze.verWalls[wx][wy];
                    }
                }
                break;
            case "RESTARTING":                     // NEW
                state.reset();
                break;
        }
    }

    public static class GameState {
        public int myPlayerId;
        public String myColorHex;
        public int myGridX, myGridY;
        public Player.State myState = Player.State.ALIVE;
        public Player myPlayer;
        public List<Player> otherPlayers = new ArrayList<>();
        public Map<Integer, Player> playerMap = new HashMap<>();
        public MazeGenerator.Maze maze;
        public int mazeWidth, mazeHeight;
        public long mazeSeed;
        public int startX, startY, exitX, exitY;
        public List<Trap> traps = new ArrayList<>();
        public List<Collectible> collectibles = new ArrayList<>();
        public String winnerName;
        public int winnerId = -1;
        public long fogBoostUntil = 0;

        public void generateMaze() {
            maze = MazeGenerator.generateMaze(mazeWidth, mazeHeight, mazeSeed);
        }

        public void reset() {
            otherPlayers.clear();
            playerMap.clear();
            traps.clear();
            collectibles.clear();
            winnerName = null;
            winnerId = -1;
            fogBoostUntil = 0;
        }
    }
}