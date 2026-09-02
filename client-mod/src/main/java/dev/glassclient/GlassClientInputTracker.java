package dev.glassclient;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Backs the keystrokes/CPS HUD widget. Fed by KeystrokeTrackerMixin
 * (KeyboardHandler.keyPress) and MouseTrackerMixin (MouseHandler.onPress) —
 * kept as a plain static tracker rather than folding into those mixins
 * directly so the render-side mixin doesn't need to know about either of
 * them.
 */
public final class GlassClientInputTracker {

    private GlassClientInputTracker() {}

    // Standard, stable GLFW key codes for W/A/S/D/C. C matches OptiFine and
    // Lunar Client's own classic default zoom key.
    private static final int KEY_W = 87;
    private static final int KEY_A = 65;
    private static final int KEY_S = 83;
    private static final int KEY_D = 68;
    private static final int KEY_C = 67;

    private static volatile boolean keyW;
    private static volatile boolean keyA;
    private static volatile boolean keyS;
    private static volatile boolean keyD;
    private static volatile boolean zoomHeld;

    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static final long CPS_WINDOW_MS = 1000;

    public static void onKey(int key, int action) {
        // GLFW action: 0 = release, 1 = press, 2 = repeat — repeat still
        // counts as "held down" for a keystrokes display.
        boolean down = action != 0;
        if (key == KEY_W) {
            keyW = down;
        } else if (key == KEY_A) {
            keyA = down;
        } else if (key == KEY_S) {
            keyS = down;
        } else if (key == KEY_D) {
            keyD = down;
        } else if (key == KEY_C) {
            zoomHeld = down;
        }
    }

    public static void onMouseButton(int button, int action) {
        if (action != 1) {
            return; // only count presses, not releases
        }
        long now = System.currentTimeMillis();
        if (button == 0) {
            recordClick(leftClicks, now);
        } else if (button == 1) {
            recordClick(rightClicks, now);
        }
    }

    private static void recordClick(Deque<Long> clicks, long now) {
        clicks.addLast(now);
        prune(clicks, now);
    }

    private static void prune(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > CPS_WINDOW_MS) {
            clicks.pollFirst();
        }
    }

    private static int countRecent(Deque<Long> clicks) {
        prune(clicks, System.currentTimeMillis());
        return clicks.size();
    }

    public static boolean isW() {
        return keyW;
    }

    public static boolean isA() {
        return keyA;
    }

    public static boolean isS() {
        return keyS;
    }

    public static boolean isD() {
        return keyD;
    }

    public static int leftCps() {
        return countRecent(leftClicks);
    }

    public static int rightCps() {
        return countRecent(rightClicks);
    }

    public static boolean isZoomHeld() {
        return zoomHeld;
    }

    // Zoom progress (0 = not zoomed, 1 = fully zoomed), not a plain boolean
    // flip — a first version toggled FOV instantly on key press/release and
    // it looked choppy in testing. Two rounds of tuning based on actually
    // trying it in-game: first pass added linear easing over ~330ms, still
    // felt too fast/mechanical; this version takes a full ~1.4s to
    // transition AND runs the raw linear ramp through smoothstep
    // (t*t*(3-2t)) before it's used anywhere, for a genuine ease-in/ease-
    // out curve rather than a constant-speed linear ramp (which still
    // reads as slightly robotic even when slow). Computed against
    // wall-clock time between calls (not a fixed per-tick step) so it
    // stays correct regardless of how many times this is called in a
    // given frame — both ZoomMixin and ZoomSensitivityMixin call it.
    private static final float ZOOM_TRANSITION_SPEED = 1.4f; // linear progress/sec
    private static volatile float zoomProgress = 0f;
    private static volatile long lastZoomUpdateMs = System.currentTimeMillis();

    public static float updateAndGetZoomProgress() {
        long now = System.currentTimeMillis();
        float dt = (now - lastZoomUpdateMs) / 1000f;
        lastZoomUpdateMs = now;

        float target = zoomHeld ? 1f : 0f;
        if (zoomProgress < target) {
            zoomProgress = Math.min(target, zoomProgress + ZOOM_TRANSITION_SPEED * dt);
        } else if (zoomProgress > target) {
            zoomProgress = Math.max(target, zoomProgress - ZOOM_TRANSITION_SPEED * dt);
        }
        return smoothstep(zoomProgress);
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
}
