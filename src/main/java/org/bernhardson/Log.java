package org.bernhardson;

public class Log {

    public static void info(String format, Object... args) {
        System.out.printf("[INFO] " + format + "%n", args);
    }

    public static void warn(String format, Object... args) {
        System.err.printf("[WARN] " + format + "%n", args);
    }

    public static void debug(boolean enabled, String format, Object... args) {
        if (enabled) {
            System.out.printf("[DEBUG] " + format + "%n", args);
        }
    }
}
