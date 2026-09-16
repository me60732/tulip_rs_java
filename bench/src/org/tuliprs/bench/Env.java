package org.tuliprs.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal KEY=VALUE .env loader (mirrors the Go bench's LoadDotEnv):
 * walks up from the working directory looking for .env / bench/.env.
 * Values land as system properties (read via {@link #envOr}).
 */
public final class Env {

    private Env() {}

    public static void loadDotEnv() {
        try {
            Path dir = Path.of("").toAbsolutePath();
            for (int up = 0; dir != null && up < 4; up++, dir = dir.getParent()) {
                for (String cand : new String[] {"/.env", "/bench/.env"}) {
                    Path f = Path.of(dir.toString() + cand);
                    if (Files.isRegularFile(f)) {
                        loadFile(f);
                        return;
                    }
                }
            }
            String override = System.getenv("DOTENV_PATH");
            if (override != null) {
                loadFile(Path.of(override));
            }
        } catch (IOException ignored) {
            // .env is optional — missing config just means env defaults
        }
    }

    private static void loadFile(Path f) throws IOException {
        for (String line : Files.readAllLines(f)) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if (val.length() >= 2 && ((val.charAt(0) == '"' && val.endsWith("\""))
                    || (val.charAt(0) == '\'' && val.endsWith("'")))) {
                val = val.substring(1, val.length() - 1);
            }
            if (System.getProperty(key) == null) {
                System.setProperty(key, val);
            }
        }
    }

    /** Env var or system property (.env) lookup. */
    public static String envOr(String name, String def) {
        String v = System.getenv(name);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(name);
        }
        return v == null || v.isEmpty() ? def : v;
    }

    public static int envInt(String name, int def) {
        try {
            return Integer.parseInt(envOr(name, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
