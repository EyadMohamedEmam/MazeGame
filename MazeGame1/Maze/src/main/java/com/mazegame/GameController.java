package com.mazegame;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import java.util.*;

public class GameController extends Application {
    private static final int TILE_SIZE = 24;
    private static final int FOG_RADIUS = 5;
    private static final int FOG_BOOST_INCREASE = 5;

    private MazeClient client;
    private Canvas canvas;
    private Canvas miniMapCanvas;
    private GraphicsContext gc, miniGc;
    private boolean minimapVisible = false;
    private Set<KeyCode> pressedKeys = new HashSet<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        client = new MazeClient();
        try {
            client.connect("172.24.3.195", 5555, "Player" + new Random().nextInt(1000));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        StackPane root = new StackPane();
        canvas = new Canvas(800, 600);
        gc = canvas.getGraphicsContext2D();
        root.getChildren().add(canvas);

        miniMapCanvas = new Canvas(150, 120);
        miniMapCanvas.setVisible(false);
        miniGc = miniMapCanvas.getGraphicsContext2D();
        StackPane.setAlignment(miniMapCanvas, javafx.geometry.Pos.TOP_RIGHT);
        root.getChildren().add(miniMapCanvas);

        Button restartButton = new Button("Restart");
        restartButton.setStyle("-fx-font-size: 14px; -fx-background-color: #333; -fx-text-fill: white;");
        restartButton.setOnAction(e -> client.sendMessage("RESTART"));
        StackPane.setAlignment(restartButton, javafx.geometry.Pos.BOTTOM_CENTER);

        // ---- Fix focus: prevent button from eating arrow keys ----
        restartButton.setFocusTraversable(false);
        root.setFocusTraversable(true);
        // ---------------------------------------------------------

        root.getChildren().add(restartButton);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(e -> {
            pressedKeys.add(e.getCode());
            if (e.getCode() == KeyCode.M) {
                minimapVisible = !minimapVisible;
                miniMapCanvas.setVisible(minimapVisible);
            }
        });
        scene.setOnKeyReleased(e -> pressedKeys.remove(e.getCode()));

        primaryStage.setTitle("Maze Explorer");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Give focus to the root so arrow keys work immediately
        root.requestFocus();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                sendMovementIfNeeded();
                render();
            }
        };
        timer.start();
    }

    private void sendMovementIfNeeded() {
        if (client.state.myState != Player.State.ALIVE) return;

        if (pressedKeys.contains(KeyCode.UP)) {
            client.sendMove("UP");
        } else if (pressedKeys.contains(KeyCode.DOWN)) {
            client.sendMove("DOWN");
        } else if (pressedKeys.contains(KeyCode.LEFT)) {
            client.sendMove("LEFT");
        } else if (pressedKeys.contains(KeyCode.RIGHT)) {
            client.sendMove("RIGHT");
        }
    }

    private int getEffectiveFogRadius() {
        long currentMillis = System.currentTimeMillis();
        return (currentMillis < client.state.fogBoostUntil) ? FOG_RADIUS + FOG_BOOST_INCREASE : FOG_RADIUS;
    }

    private void render() {
        MazeClient.GameState state = client.state;
        if (state.maze == null) return;
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        int offsetX = 10, offsetY = 10;
        boolean isGhost = state.myState == Player.State.GHOST;
        int myX = state.myGridX, myY = state.myGridY;
        int fogRadius = getEffectiveFogRadius();

        Color myColor = Color.web(state.myColorHex);

        // Draw maze cells
        for (int x = 0; x < state.mazeWidth; x++) {
            for (int y = 0; y < state.mazeHeight; y++) {
                double px = offsetX + x * TILE_SIZE;
                double py = offsetY + y * TILE_SIZE;
                boolean visible = isWithinFog(myX, myY, x, y, fogRadius);
                if (!visible && !isGhost) continue;

                gc.setFill(visible ? Color.LIGHTGRAY : Color.DARKGRAY.darker());
                gc.fillRect(px, py, TILE_SIZE, TILE_SIZE);

                gc.setStroke(visible ? Color.BLACK : Color.DARKGRAY);
                gc.setLineWidth(2);
                MazeGenerator.Cell cell = state.maze.grid[x][y];
                if (cell.walls[0]) gc.strokeLine(px, py, px + TILE_SIZE, py);
                if (cell.walls[1]) gc.strokeLine(px + TILE_SIZE, py, px + TILE_SIZE, py + TILE_SIZE);
                if (cell.walls[2]) gc.strokeLine(px, py + TILE_SIZE, px + TILE_SIZE, py + TILE_SIZE);
                if (cell.walls[3]) gc.strokeLine(px, py, px, py + TILE_SIZE);
            }
        }

        // Traps
        for (Trap t : state.traps) {
            if (!isWithinFog(myX, myY, t.x, t.y, fogRadius)) continue;
            double px = offsetX + t.x * TILE_SIZE + TILE_SIZE/2;
            double py = offsetY + t.y * TILE_SIZE + TILE_SIZE/2;
            gc.setFill(t.type == Trap.Type.SLOW ? Color.BLUE : Color.RED);
            gc.fillOval(px-5, py-5, 10, 10);
        }

        // Collectibles
        for (Collectible c : state.collectibles) {
            if (c.collected) continue;
            if (!isWithinFog(myX, myY, c.x, c.y, fogRadius)) continue;
            double px = offsetX + c.x * TILE_SIZE + TILE_SIZE/2;
            double py = offsetY + c.y * TILE_SIZE + TILE_SIZE/2;
            gc.setFill(c.type == Collectible.Type.SPEED_BOOST ? Color.ORANGE : Color.YELLOW);
            gc.fillOval(px-4, py-4, 8, 8);
        }

        // Start and exit
        gc.setFill(Color.GREEN);
        gc.fillRect(offsetX + state.startX * TILE_SIZE, offsetY + state.startY * TILE_SIZE, TILE_SIZE, TILE_SIZE);
        gc.setFill(Color.RED);
        gc.fillRect(offsetX + state.exitX * TILE_SIZE, offsetY + state.exitY * TILE_SIZE, TILE_SIZE, TILE_SIZE);

        // Other players
        for (Player p : state.otherPlayers) {
            if (state.myState == Player.State.ALIVE && p.state == Player.State.GHOST) continue;
            if (state.myState == Player.State.GHOST && p.state != Player.State.GHOST) continue;
            if (!isWithinFog(myX, myY, p.gridX, p.gridY, fogRadius) && state.myState != Player.State.GHOST) continue;
            double px = offsetX + p.gridX * TILE_SIZE + TILE_SIZE/2;
            double py = offsetY + p.gridY * TILE_SIZE + TILE_SIZE/2;
            Color pColor = Color.web(p.colorHex);
            gc.setFill(pColor);
            gc.fillOval(px-6, py-6, 12, 12);
            if (p.state == Player.State.GHOST) gc.setStroke(Color.WHITE);
            gc.strokeOval(px-6, py-6, 12, 12);
        }

        // My player
        if (state.myPlayer != null) {
            double px = offsetX + state.myPlayer.gridX * TILE_SIZE + TILE_SIZE/2;
            double py = offsetY + state.myPlayer.gridY * TILE_SIZE + TILE_SIZE/2;
            gc.setFill(myColor);
            gc.fillOval(px-7, py-7, 14, 14);
            gc.setStroke(Color.WHITE);
            gc.strokeOval(px-7, py-7, 14, 14);
        }

        // Winner overlay
        if (state.winnerName != null) {
            gc.setFill(Color.rgb(0,0,0,0.7));
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setFill(Color.WHITE);
            gc.setFont(new Font(24));
            gc.fillText("Winner: " + state.winnerName, 300, 300);
        }

        // Minimap (full maze, clean walls)
        if (minimapVisible) {
            renderMinimap(state, myColor);
        }
    }

    private boolean isWithinFog(int px, int py, int tx, int ty, int radius) {
        int dx = px - tx, dy = py - ty;
        return (dx*dx + dy*dy) <= radius*radius;
    }

    private void renderMinimap(MazeClient.GameState state, Color myColor) {
        double scale = (double) miniMapCanvas.getWidth() / (state.mazeWidth * TILE_SIZE);
        miniGc.clearRect(0, 0, miniMapCanvas.getWidth(), miniMapCanvas.getHeight());

        miniGc.setFill(Color.rgb(20, 20, 30));
        miniGc.fillRect(0, 0, miniMapCanvas.getWidth(), miniMapCanvas.getHeight());

        miniGc.setFill(Color.rgb(180, 180, 180));
        for (int x = 0; x < state.mazeWidth; x++) {
            for (int y = 0; y < state.mazeHeight; y++) {
                double px = x * TILE_SIZE * scale;
                double py = y * TILE_SIZE * scale;
                double cellSize = TILE_SIZE * scale;
                miniGc.fillRect(px, py, cellSize, cellSize);
            }
        }

        miniGc.setStroke(Color.rgb(60, 60, 60));
        miniGc.setLineWidth(1.0);
        for (int x = 0; x < state.mazeWidth; x++) {
            for (int y = 0; y < state.mazeHeight; y++) {
                double px = x * TILE_SIZE * scale;
                double py = y * TILE_SIZE * scale;
                double cellSize = TILE_SIZE * scale;
                MazeGenerator.Cell c = state.maze.grid[x][y];

                if (c.walls[0]) {
                    miniGc.strokeLine(px, py, px + cellSize, py);
                }
                if (c.walls[3]) {
                    miniGc.strokeLine(px, py, px, py + cellSize);
                }
            }
        }

        miniGc.setFill(myColor);
        double mypx = state.myGridX * TILE_SIZE * scale + TILE_SIZE * scale / 2;
        double mypy = state.myGridY * TILE_SIZE * scale + TILE_SIZE * scale / 2;
        miniGc.fillOval(mypx - 3, mypy - 3, 6, 6);
        miniGc.setStroke(Color.WHITE);
        miniGc.setLineWidth(1);
        miniGc.strokeOval(mypx - 3, mypy - 3, 6, 6);
    }
}