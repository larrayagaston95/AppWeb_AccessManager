import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DropIndex {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/accessmanager_db?serverTimezone=UTC";
        String user = "root";
        String pass = "AtomG-S0ft";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            
            stmt.executeUpdate("ALTER TABLE empleados DROP INDEX UK_legajo_reloj");
            System.out.println("Indice eliminado exitosamente.");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
