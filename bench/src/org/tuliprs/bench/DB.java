package org.tuliprs.bench;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

/** Opens a JDBC connection from either a {@code postgres://} or {@code jdbc:postgresql://} URL. */
final class DB {

    private DB() {}

    static Connection open(String url) throws Exception {
        if (url.startsWith("jdbc:")) {
            if (!url.contains("connectTimeout")) {
                url = url + (url.contains("?") ? "&" : "?") + "connectTimeout=5";
            }
            return DriverManager.getConnection(url);
        }
        // postgres://user:pass@host:port/db?params → split creds into properties
        String rest = url.substring(url.indexOf("://") + 3);
        Properties props = new Properties();
        int at = rest.indexOf('@');
        if (at >= 0) {
            String creds = rest.substring(0, at);
            rest = rest.substring(at + 1);
            int colon = creds.indexOf(':');
            if (colon >= 0) {
                props.setProperty("user", creds.substring(0, colon));
                props.setProperty("password", creds.substring(colon + 1));
            } else {
                props.setProperty("user", creds);
            }
        }
        if (!url.contains("connectTimeout")) {
            props.setProperty("connectTimeout", "5");
        }
        return DriverManager.getConnection("jdbc:postgresql://" + rest, props);
    }
}
