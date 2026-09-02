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

    // Standard, stable GLFW key codes for W/A/S/D.
    private static final int KEY_W = 87;
    private static final int KEY_A = 65;
    private static final int KEY_S = 83;
    private static final int KEY_D = 68;

    private static volatile boolean keyW;
    private static volatile boolean keyA;
    private static volatile boolean keyS;
    private static volatile boolean keyD;

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
}
