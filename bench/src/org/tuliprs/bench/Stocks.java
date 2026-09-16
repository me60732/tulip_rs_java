package org.tuliprs.bench;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the benchmark tickers from the stocks Postgres — the SAME four
 * tickers, query and 6705-bar limit as the Go/Python/Rust bench suites.
 */
public final class Stocks {

    private static final int DATA_LIMIT = 6705;
    private static final String[][] STOCKS = {
            {"BHP", "ASX"}, {"CBA", "ASX"}, {"AAPL", "NYSE"}, {"MSFT", "NYSE"},
    };

    private Stocks() {}

    public static List<Stock> load() throws Exception {
        String dbUrl = Env.envOr("DATABASE_URL",
                "jdbc:postgresql://192.168.50.10:5433/stocks?sslmode=disable&user=tulip&password=tulip");

        String query = """
            SELECT e.open, e.high, e.low, e.close, e.volume
            FROM listing l
            INNER JOIN adj_eod e ON l.listing_id = e.listing_id
            WHERE l.code = ?
              AND l.exchange_code = ?
              AND e.volume > 0
            ORDER BY e.ts ASC
            LIMIT ?
            """;

        List<Stock> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(dbUrl)) {
            for (String[] s : STOCKS) {
                try (PreparedStatement ps = c.prepareStatement(query)) {
                    ps.setString(1, s[0]);
                    ps.setString(2, s[1]);
                    ps.setInt(3, DATA_LIMIT);
                    List<double[]> bars = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            bars.add(new double[] {
                                    rs.getDouble(1), rs.getDouble(2), rs.getDouble(3),
                                    rs.getDouble(4), rs.getDouble(5),
                            });
                        }
                    }
                    if (bars.isEmpty()) {
                        System.err.println("[warn] no data for " + s[0] + "/" + s[1] + " — skipping");
                        continue;
                    }
                    int n = bars.size();
                    double[] o = new double[n], h = new double[n], l = new double[n];
                    double[] cl = new double[n], v = new double[n];
                    for (int i = 0; i < n; i++) {
                        double[] b = bars.get(i);
                        o[i] = b[0]; h[i] = b[1]; l[i] = b[2]; cl[i] = b[3]; v[i] = b[4];
                    }
                    String sym = s[0] + "_" + s[1];
                    System.out.printf("  loaded %d bars  %s (from Postgres)%n", n, sym);
                    out.add(new Stock(sym, o, h, l, cl, v));
                }
            }
        }
        return out;
    }
}
