package com.mazegame;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Simple map editor: click on walls to toggle them.
 * Saves the maze layout (encoded as a string of bits) to the CustomMazes table.
 */
public class MapEditor extends Application {
    private static final int TILE_SIZE = 24;
    private int width = 20, height = 15;
    private MazeGenerator.Maze maze;
    private Canvas canvas;
    private GraphicsContext gc;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        canvas = new Canvas(width * TILE_SIZE, height * TILE_SIZE);
        gc = canvas.getGraphicsContext2D();
        root.setCenter(canvas);

        // Create a default maze (all walls present)
        MazeGenerator.Cell[][] cells = new MazeGenerator.Cell[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                cells[x][y] = new MazeGenerator.Cell();
        maze = new MazeGenerator.Maze(width, height, cells);
        drawMaze();

        canvas.setOnMouseClicked(e -> {
            int mx = (int)(e.getX() / TILE_SIZE);
            int my = (int)(e.getY() / TILE_SIZE);
            if (mx >= 0 && mx < width && my >= 0 && my < height) {
                // toggle wall closest to click? Simple: toggle top wall if click near top edge, etc.
                double dx = e.getX() - mx * TILE_SIZE;
                double dy = e.getY() - my * TILE_SIZE;
                if (dy < 4) { // top wall
                    maze.grid[mx][my].walls[0] = !maze.grid[mx][my].walls[0];
                    if (my > 0) maze.grid[mx][my-1].walls[2] = maze.grid[mx][my].walls[0];
                } else if (dy > TILE_SIZE - 4) { // bottom
                    maze.grid[mx][my].walls[2] = !maze.grid[mx][my].walls[2];
                    if (my < height-1) maze.grid[mx][my+1].walls[0] = maze.grid[mx][my].walls[2];
                } else if (dx < 4) { // left
                    maze.grid[mx][my].walls[3] = !maze.grid[mx][my].walls[3];
                    if (mx > 0) maze.grid[mx-1][my].walls[1] = maze.grid[mx][my].walls[3];
                } else if (dx > TILE_SIZE - 4) { // right
                    maze.grid[mx][my].walls[1] = !maze.grid[mx][my].walls[1];
                    if (mx < width-1) maze.grid[mx+1][my].walls[3] = maze.grid[mx][my].walls[1];
                }
                drawMaze();
            }
        });

        Button saveBtn = new Button("Save to DB");
        saveBtn.setOnAction(e -> saveMaze());
        root.setBottom(saveBtn);

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Maze Editor");
        primaryStage.show();
    }

    private void drawMaze() {
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double px = x * TILE_SIZE, py = y * TILE_SIZE;
                MazeGenerator.Cell c = maze.grid[x][y];
                gc.setStroke(Color.BLACK);
                if (c.walls[0]) gc.strokeLine(px, py, px+TILE_SIZE, py);
                if (c.walls[1]) gc.strokeLine(px+TILE_SIZE, py, px+TILE_SIZE, py+TILE_SIZE);
                if (c.walls[2]) gc.strokeLine(px, py+TILE_SIZE, px+TILE_SIZE, py+TILE_SIZE);
                if (c.walls[3]) gc.strokeLine(px, py, px, py+TILE_SIZE);
            }
        }
    }

    private void saveMaze() {
        // Encode walls into a string (0/1 per wall)
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                MazeGenerator.Cell c = maze.grid[x][y];
                for (int w = 0; w < 4; w++) {
                    sb.append(c.walls[w] ? '1' : '0');
                }
            }
        }
        String name = "custom_" + System.currentTimeMillis();
        DBManager.saveCustomMaze(name, width, height, sb.toString());
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Maze saved as " + name);
        alert.show();
    }
}