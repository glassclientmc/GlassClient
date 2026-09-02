package dev.glassclient;

/**
 * In-memory only for now (resets each launch) — a real persisted config
 * file is future work once there's more than two toggles to justify it.
 */
public final class GlassClientConfig {

    private GlassClientConfig() {}

    private static volatile boolean showFps = true;
    private static volatile boolean showCoords = true;
    private static volatile boolean showKeystrokes = true;
    private static volatile boolean showCps = true;

    public static boolean showFps() {
        return showFps;
    }

    public static void setShowFps(boolean value) {
        showFps = value;
    }

    public static boolean showCoords() {
        return showCoords;
    }

    public static void setShowCoords(boolean value) {
        showCoords = value;
    }

    public static boolean showKeystrokes() {
        return showKeystrokes;
    }

    public static void setShowKeystrokes(boolean value) {
        showKeystrokes = value;
    }

    public static boolean showCps() {
        return showCps;
    }

    public static void setShowCps(boolean value) {
        showCps = value;
    }
}
