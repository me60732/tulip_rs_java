import java.sql.*;

public class DbProbe {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://192.168.50.10:5433/stocks?connectTimeout=5&user=tulip&password=tulip";
        try (Connection c = DriverManager.getConnection(url)) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT code, exchange_code, count(*) FROM listing l INNER JOIN adj_eod e USING (listing_id) WHERE code IN ('BHP','CBA','AAPL','MSFT') GROUP BY code, exchange_code")) {
                while (rs.next()) {
                    System.out.println("OK " + rs.getString(1) + "_" + rs.getString(2) + " rows=" + rs.getLong(3));
                }
            }
        }
    }
}
