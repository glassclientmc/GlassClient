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

    private static volatile boolean showHitboxes = true;

    public static boolean showHitboxes() {
        return showHitboxes;
    }

    public static void setShowHitboxes(boolean value) {
        showHitboxes = value;
    }

    private static volatile boolean showReach = true;

    public static boolean showReach() {
        return showReach;
    }

    public static void setShowReach(boolean value) {
        showReach = value;
    }

    private static volatile boolean showCosmeticCape = true;

    public static boolean showCosmeticCape() {
        return showCosmeticCape;
    }

    public static void setShowCosmeticCape(boolean value) {
        showCosmeticCape = value;
    }

    private static volatile boolean fullBright = false;

    public static boolean fullBright() {
        return fullBright;
    }

    public static void setFullBright(boolean value) {
        fullBright = value;
    }

    private static volatile boolean noHurtCam = false;

    public static boolean noHurtCam() {
        return noHurtCam;
    }

    public static void setNoHurtCam(boolean value) {
        noHurtCam = value;
    }
}
