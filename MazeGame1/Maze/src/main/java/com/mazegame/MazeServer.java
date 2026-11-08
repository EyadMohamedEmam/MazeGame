package com.mazegame;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MazeServer {
    private static final int PORT = 5555;
    private static final int TICK_MS = 100;
    private static final int WALL_MOVE_INTERVAL = 30_000;
    private static final int ROUND_COOLDOWN = 5_000;
    private static final int MOVE_DELAY_MS = 100;    // server-side movement cooldown

    private MazeGenerator.Maze currentMaze;
    private long mazeSeed;
    private int mazeWidth = 20, mazeHeight = 15;
    private List<Player> players = new CopyOnWriteArrayList<>();
    private List<Collectible> collectibles = new ArrayList<>();
    private List<Trap> traps = new ArrayList<>();
    private volatile boolean roundRunning = false;
    private volatile Player winner = null;
    private long roundStartTime;
    private int playerIdCounter = 0;
    private volatile boolean forceRestart = false;

    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new ArrayList<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static void main(String[] args) {
        new MazeServer().start();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);
            scheduler.scheduleAtFixedRate(this::gameLoop, 0, TICK_MS, TimeUnit.MILLISECONDS);
            scheduler.scheduleAtFixedRate(this::handleMovingWalls, WALL_MOVE_INTERVAL, WALL_MOVE_INTERVAL, TimeUnit.MILLISECONDS);
            new Thread(this::mazeGenerationThread).start();

            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void mazeGenerationThread() {
        while (true) {
            try {
                generateNewMaze();
                broadcastStart();
                roundRunning = true;
                winner = null;
                roundStartTime = System.currentTimeMillis();

                while (winner == null && !forceRestart) {
                    Thread.sleep(500);
                }

                if (forceRestart) {
                    forceRestart = false;
                    roundRunning = false;
                    collectibles.clear();
                    traps.clear();
                    for (Player p : players) {
                        p.state = Player.State.ALIVE;
                        p.speedBoostUntil = 0; p.slowUntil = 0; p.reverseUntil = 0;
                        p.fogBoostUntil = 0;
                        p.lastMoveTime = 0;
                    }
                    continue;
                }

                broadcast("WINNER:" + winner.id + ":" + winner.name);
                for (Player p : players) {
                    if (p.id != winner.id) p.state = Player.State.GHOST;
                    else p.state = Player.State.SPECTATOR;
                }
                Thread.sleep(ROUND_COOLDOWN);
                roundRunning = false;
                collectibles.clear();
                traps.clear();
                for (Player p : players) {
                    p.state = Player.State.ALIVE;
                    p.speedMultiplier = 1.0;
                    p.speedBoostUntil = 0; p.slowUntil = 0; p.reverseUntil = 0;
                    p.fogBoostUntil = 0;
                    p.lastMoveTime = 0;
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void generateNewMaze() {
        mazeSeed = new Random().nextLong();
        currentMaze = MazeGenerator.generateMaze(mazeWidth, mazeHeight, mazeSeed);
        Random rand = new Random();

        // Orange collectibles (reverse effect)
        for (int i = 0; i < 2; i++) {
            int x = rand.nextInt(mazeWidth);
            int y = rand.nextInt(mazeHeight);
            if ((x == 0 && y == 0) || (x == mazeWidth-1 && y == mazeHeight-1)) continue;
            collectibles.add(new Collectible(x, y, Collectible.Type.SPEED_BOOST));
        }
        // Yellow score points
        for (int i = 0; i < 3; i++) {
            int x = rand.nextInt(mazeWidth);
            int y = rand.nextInt(mazeHeight);
            collectibles.add(new Collectible(x, y, Collectible.Type.SCORE_POINTS));
        }
        // Traps (blue = fog boost, red = kill)
        traps.clear();
        String[] trapTypes = {"SLOW", "REVERSE"};
        for (int i = 0; i < 4; i++) {
            int x = rand.nextInt(mazeWidth);
            int y = rand.nextInt(mazeHeight);
            if ((x == 0 && y == 0) || (x == mazeWidth-1 && y == mazeHeight-1)) continue;
            String type = trapTypes[rand.nextInt(2)];
            traps.add(new Trap(x, y, Trap.Type.valueOf(type)));
        }
        int mazeSeedId = DBManager.insertMazeSeed(mazeSeed, mazeWidth, mazeHeight);
        if (mazeSeedId != -1) {
            for (Trap t : traps) {
                DBManager.insertTrapPlacement(mazeSeedId, t.x, t.y, t.type.name());
            }
        }
        for (Player p : players) {
            p.setPosition(currentMaze.startX, currentMaze.startY);
            p.lastMoveTime = 0;
        }
    }

    private void broadcastStart() {
        broadcast("START:" + mazeWidth + ":" + mazeHeight + ":" + mazeSeed + ":" +
                currentMaze.startX + ":" + currentMaze.startY + ":" +
                currentMaze.exitX + ":" + currentMaze.exitY);
        for (Trap t : traps) {
            broadcast("TRAP:" + t.x + ":" + t.y + ":" + t.type.name());
        }
        for (Collectible c : collectibles) {
            broadcast("COLLECTIBLE:" + c.x + ":" + c.y + ":" + c.type.name());
        }
    }

    private void handleMovingWalls() {
        if (!roundRunning || currentMaze == null) return;
        List<String> allToggles = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            List<String> toggles = MazeGenerator.modifyWithMovingWalls(currentMaze, new Random());
            allToggles.addAll(toggles);
        }
        if (!allToggles.isEmpty()) {
            System.out.println("Moving walls! Toggled: " + allToggles);
            for (String toggle : allToggles) {
                broadcast("WALL_TOGGLE:" + toggle);
            }
        } else {
            System.out.println("Moving walls skipped – no safe toggles found.");
        }
    }

    private void gameLoop() {
        if (!roundRunning || currentMaze == null) return;
        long now = System.currentTimeMillis();
        for (Player p : players) {
            if (p.state != Player.State.ALIVE) continue;
            int x = p.gridX, y = p.gridY;

            for (Collectible c : collectibles) {
                if (!c.collected && c.x == x && c.y == y) {
                    c.collected = true;
                    broadcast("COLLECTED:" + c.x + ":" + c.y);
                    if (c.type == Collectible.Type.SPEED_BOOST) {
                        p.reverseUntil = now + 5000;
                        broadcast("EFFECT:" + p.id + ":REVERSE:5000");
                    } else {
                        broadcast("SCORE:" + p.id + ":10");
                    }
                }
            }

            for (Trap t : traps) {
                if (t.x == x && t.y == y) {
                    if (t.type == Trap.Type.SLOW) {
                        p.fogBoostUntil = now + 8000;
                        broadcast("EFFECT:" + p.id + ":FOG_BOOST:8000");
                    } else if (t.type == Trap.Type.REVERSE) {
                        p.state = Player.State.GHOST;
                        broadcast("EFFECT:" + p.id + ":GHOST:0");
                    }
                }
            }

            if (x == currentMaze.exitX && y == currentMaze.exitY && winner == null) {
                winner = p;
                double timeTaken = (now - roundStartTime) / 1000.0;
                DBManager.updatePlayerStats(p.name, true, timeTaken);
                DBManager.insertFastestClear(p.name, mazeWidth + "x" + mazeHeight, timeTaken);
            }
        }

        StringBuilder posMsg = new StringBuilder("POS");
        for (Player p : players) {
            posMsg.append(":").append(p.id).append(",").append(p.gridX).append(",").append(p.gridY)
                    .append(",").append(p.state.ordinal());
        }
        broadcast(posMsg.toString());
    }

    private void broadcast(String msg) {
        for (ClientHandler ch : clients) {
            ch.send(msg);
        }
    }

    private void restartRound() {
        forceRestart = true;
        broadcast("RESTARTING");
    }

    // ----------------------------------------------------------------------
    class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private Player player;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String joinMsg = in.readLine();
                if (joinMsg == null || !joinMsg.startsWith("JOIN:")) {
                    socket.close(); return;
                }
                String name = joinMsg.substring(5);
                player = new Player(++playerIdCounter, name, randomColorHex());
                players.add(player);
                if (currentMaze != null) {
                    player.setPosition(currentMaze.startX, currentMaze.startY);
                    player.lastMoveTime = 0;
                }
                out.println("WELCOME:" + player.id + ":" + player.colorHex);
                if (roundRunning && currentMaze != null) {
                    out.println("START:" + mazeWidth + ":" + mazeHeight + ":" + mazeSeed + ":" +
                            currentMaze.startX + ":" + currentMaze.startY + ":" +
                            currentMaze.exitX + ":" + currentMaze.exitY);
                    for (Trap t : traps) {
                        out.println("TRAP:" + t.x + ":" + t.y + ":" + t.type.name());
                    }
                    for (Collectible c : collectibles) {
                        out.println("COLLECTIBLE:" + c.x + ":" + c.y + ":" + c.type.name());
                    }
                }

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("MOVE:")) {
                        handleMove(line.substring(5));
                    } else if (line.startsWith("RESTART")) {
                        restartRound();
                    } else if (line.startsWith("DISCONNECT")) {
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (player != null) players.remove(player);
                clients.remove(this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void handleMove(String dir) {
            if (!roundRunning || currentMaze == null || player.state != Player.State.ALIVE) return;

            long now = System.currentTimeMillis();
            // Server‑side cooldown: 100 ms between moves
            if (now - player.lastMoveTime < MOVE_DELAY_MS) return;

            // Reverse controls if affected by orange collectible
            if (player.reverseUntil > now) {
                switch (dir) {
                    case "UP":    dir = "DOWN"; break;
                    case "DOWN":  dir = "UP";   break;
                    case "LEFT":  dir = "RIGHT"; break;
                    case "RIGHT": dir = "LEFT";  break;
                }
            }

            int nx = player.gridX, ny = player.gridY;
            int wallIndex = -1;
            switch (dir) {
                case "UP":    ny--; wallIndex = 0; break;
                case "RIGHT": nx++; wallIndex = 1; break;
                case "DOWN":  ny++; wallIndex = 2; break;
                case "LEFT":  nx--; wallIndex = 3; break;
            }
            if (nx < 0 || ny < 0 || nx >= currentMaze.width || ny >= currentMaze.height) return;

            if (!currentMaze.grid[player.gridX][player.gridY].walls[wallIndex]) {
                player.gridX = nx;
                player.gridY = ny;
                player.lastMoveTime = now;   // record time of successful move
            }
        }

        public void send(String msg) {
            if (out != null) out.println(msg);
        }

        private String randomColorHex() {
            java.util.Random r = new java.util.Random();
            int red = r.nextInt(200) + 55;
            int green = r.nextInt(200) + 55;
            int blue = r.nextInt(200) + 55;
            return String.format("#%02X%02X%02X", red, green, blue);
        }
    }
}