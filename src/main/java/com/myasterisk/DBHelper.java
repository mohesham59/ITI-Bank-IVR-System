package com.myasterisk;

import java.sql.*;

public class DBHelper {
    private static final String URL = "jdbc:postgresql://localhost:5432/iti_bank";
    private static final String USER = "bankuser";
    private static final String PASS = "bank123";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static double getBalance(String phone) {
        String sql = "SELECT balance FROM accounts WHERE phone = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static String getName(String phone) {
        String sql = "SELECT name FROM accounts WHERE phone = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Unknown";
    }
}
