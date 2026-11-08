package com.mazegame;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/MazeGame";
    private static final String USER = "root";
    private static final String PASS = "";  // TODO: change to your MySQL root password

    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (Exception e) { e.printStackTrace(); }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    public static int insertMazeSeed(long seed, int width, int height) {
        String sql = "INSERT INTO MazeSeed (seed, width, height) VALUES (?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, seed);
            stmt.setInt(2, width);
            stmt.setInt(3, height);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public static void insertTrapPlacement(int mazeSeedId, int x, int y, String trapType) {
        String sql = "INSERT INTO TrapPlacement (mazeSeedId, x, y, trapType) VALUES (?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mazeSeedId);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setString(4, trapType);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void updatePlayerStats(String playerName, boolean won, double time) {
        String checkSql = "SELECT id, wins, totalGames, avgTime FROM PlayerStats WHERE playerName = ?";
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, playerName);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                int wins = rs.getInt("wins");
                int total = rs.getInt("totalGames");
                double avg = rs.getDouble("avgTime");
                if (won) wins++;
                total++;
                avg = ((avg * (total-1)) + time) / total;
                String upd = "UPDATE PlayerStats SET wins=?, totalGames=?, avgTime=? WHERE id=?";
                try (PreparedStatement upStmt = conn.prepareStatement(upd)) {
                    upStmt.setInt(1, wins);
                    upStmt.setInt(2, total);
                    upStmt.setDouble(3, avg);
                    upStmt.setInt(4, id);
                    upStmt.executeUpdate();
                }
            } else {
                String ins = "INSERT INTO PlayerStats (playerName, wins, totalGames, avgTime) VALUES (?,?,?,?)";
                try (PreparedStatement insStmt = conn.prepareStatement(ins)) {
                    insStmt.setString(1, playerName);
                    insStmt.setInt(2, won ? 1 : 0);
                    insStmt.setInt(3, 1);
                    insStmt.setDouble(4, time);
                    insStmt.executeUpdate();
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void insertFastestClear(String playerName, String mazeSize, double timeSeconds) {
        String sql = "INSERT INTO FastestClears (playerName, mazeSize, timeSeconds) VALUES (?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            stmt.setString(2, mazeSize);
            stmt.setDouble(3, timeSeconds);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void saveCustomMaze(String name, int width, int height, String wallData) {
        String sql = "INSERT INTO CustomMazes (name, width, height, wallData) VALUES (?,?,?,?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setInt(2, width);
            stmt.setInt(3, height);
            stmt.setString(4, wallData);
            stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}