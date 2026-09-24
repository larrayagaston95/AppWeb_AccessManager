package com.atomg.accessmanager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;

public class DropIndex {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/accessmanager_db?serverTimezone=UTC";
        String user = "root";
        String pass = "AtomG-S0ft";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE empleados");
            if (rs.next()) {
                System.out.println(rs.getString(2));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
