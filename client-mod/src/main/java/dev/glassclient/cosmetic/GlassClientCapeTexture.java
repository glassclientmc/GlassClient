package dev.glassclient.cosmetic;

import java.io.IOException;
import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds GlassClient's cape texture in memory rather than shipping a PNG
 * resource under Minecraft's own asset convention — this mod isn't a
 * Fabric/Forge mod and never registers a resource pack with Minecraft's
 * ResourceManager, so a bundled texture wouldn't actually resolve via a
 * normal ResourceLocation lookup. The real logo art IS bundled (as a plain
 * classpath resource, loaded via this class's own ClassLoader, not
 * Minecraft's) — only the final composited cape texture is registered
 * directly with TextureManager, sidestepping the resource-pack pipeline
 * entirely.
 *
 * Texture is 64x64, not 64x32: PlayerCapeModel.createCapeLayer() bakes its
 * mesh via LayerDefinition.create(meshDefinition, 64, 64) — confirmed by
 * reading that source directly, not assumed. A first version built a 64x32
 * canvas (the size widely known from classic/legacy cape textures) and the
 * logo simply never appeared — wrong canvas size means the model's UV
 * fractions (computed against a declared 64-tall texture) sample the wrong
 * rows entirely once bound to an actual 32-tall image.
 *
 * The visible cape panel occupies a specific 10x16 sub-rectangle of that
 * 64x64 canvas, not the whole thing — worked out by reading
 * ModelPart.Cube's actual UV-unwrap math (addBox with texOffs(0,0), size
 * 10x16x1 depth produces NORTH face at pixel (1,1)-(11,17) and SOUTH face
 * at (12,1)-(22,17); the model is rotated 180 degrees around Y at
 * PartPose.offsetAndRotation(...), so which of those two ends up
 * camera-facing wasn't fully resolved from source alone — the logo is
 * drawn into both to guarantee it's on the one actually shown, harmless on
 * the one that isn't.
 *
 * Animated: background hue cycles cyan -> blue -> purple -> back (a
 * triangle wave), and the logo's glow halo pulses independently — both
 * re-uploaded to the GPU periodically rather than drawn once.
 *
 * Deliberately writes every pixel through setPixelABGR with hand-packed
 * ABGR ints, never plain setPixel/getPixel — NativeImage's setPixelABGR is
 * confirmed correct by the earlier working animated-gradient version (the
 * cape rendered with the right colors), but plain setPixel/getPixel's
 * actual byte order was never independently verified. The logo's own
 * pixels get decoded explicitly (decodeToArgb) rather than assumed for
 * that reason.
 */
public final class GlassClientCapeTexture {

    private GlassClientCapeTexture() {}

    private static final int CANVAS_SIZE = 64;
    public static final ResourceLocation LOCATION =
        ResourceLocation.fromNamespaceAndPath("glassclient", "textures/entity/cape.png");

    // Re-uploading a texture is not free — throttled well below frame rate
    // rather than regenerated on every render call (which can happen
    // multiple times per frame with several players visible).
    private static final long UPDATE_INTERVAL_MS = 66; // ~15 updates/sec
    private static final long HUE_CYCLE_MS = 4000;
    private static final long GLOW_PULSE_MS = 1400;
    // Cyan (180deg) through blue-purple (260deg), in HSB's 0..1 hue scale.
    private static final float HUE_MIN = 0.50f;
    private static final float HUE_MAX = 0.72f;

    // The two candidate visible-face rectangles, see class doc.
    private static final int[] FACE_ORIGIN_X = {1, 12};
    private static final int FACE_ORIGIN_Y = 1;
    private static final int FACE_W = 10;
    private static final int FACE_H = 16;

    private static final int LOGO_SIZE = 8;
    private static final int LOGO_OFFSET_X = (FACE_W - LOGO_SIZE) / 2;
    private static final int LOGO_OFFSET_Y = (FACE_H - LOGO_SIZE) / 2;
    private static final int GLOW_COLOR = 0xFFEFFBFF;

    private static DynamicTexture texture;
    private static NativeImage canvas;
    private static long lastUpdateMs = -1;

    // Logo, pre-downscaled to LOGO_SIZE once at load time, not every frame.
    // Each entry is a plain 0xAARRGGBB int.
    private static int[] logoArgb;
    private static boolean[] logoOpaque;
    private static boolean[] logoHalo;
    private static boolean logoLoadFailed = false;

    /** Safe to call every render frame — throttles its own real work internally. */
    public static void update() {
        long now = System.currentTimeMillis();
        if (canvas != null && now - lastUpdateMs < UPDATE_INTERVAL_MS) {
            return;
        }
        lastUpdateMs = now;

        if (canvas == null) {
            canvas = new NativeImage(CANVAS_SIZE, CANVAS_SIZE, false);
            texture = new DynamicTexture(() -> "glassclient_cape", canvas);
            Minecraft.getInstance().getTextureManager().register(LOCATION, texture);
        }
        if (logoArgb == null && !logoLoadFailed) {
            loadLogo();
        }

        float pulsePhase = (now % GLOW_PULSE_MS) / (float) GLOW_PULSE_MS;
        float pulse = 0.5f + 0.5f * (float) Math.sin(pulsePhase * Math.PI * 2); // smooth 0..1..0

        float huePhase = (now % HUE_CYCLE_MS) / (float) HUE_CYCLE_MS;
        float hueTriangle = huePhase < 0.5f ? huePhase * 2f : 2f - huePhase * 2f;
        float hue = HUE_MIN + (HUE_MAX - HUE_MIN) * hueTriangle;

        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                int argb = backgroundColorAt(y, hue);

                if (logoArgb != null) {
                    for (int faceX : FACE_ORIGIN_X) {
                        int lx = x - faceX - LOGO_OFFSET_X;
                        int ly = y - FACE_ORIGIN_Y - LOGO_OFFSET_Y;
                        if (lx >= 0 && lx < LOGO_SIZE && ly >= 0 && ly < LOGO_SIZE) {
                            int i = ly * LOGO_SIZE + lx;
                            if (logoOpaque[i]) {
                                argb = boostBrightness(logoArgb[i], pulse * 0.35f);
                            } else if (logoHalo[i]) {
                                argb = blend(argb, GLOW_COLOR, pulse * 0.55f);
                            }
                            break;
                        }
                    }
                }

                canvas.setPixelABGR(x, y, toAbgr(argb));
            }
        }

        texture.upload();
    }

    private static int backgroundColorAt(int y, float hue) {
        // Brighter near the collar, deeper toward the hem.
        float brightness = 1.0f - 0.35f * ((float) y / (CANVAS_SIZE - 1));
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.55f, brightness) & 0xFFFFFF);
    }

    private static void loadLogo() {
        try (InputStream in = GlassClientCapeTexture.class.getResourceAsStream("cape_logo.png")) {
            if (in == null) {
                logoLoadFailed = true;
                return;
            }
            NativeImage source = NativeImage.read(in);
            int srcSize = Math.min(source.getWidth(), source.getHeight());

            logoArgb = new int[LOGO_SIZE * LOGO_SIZE];
            logoOpaque = new boolean[LOGO_SIZE * LOGO_SIZE];
            for (int ly = 0; ly < LOGO_SIZE; ly++) {
                for (int lx = 0; lx < LOGO_SIZE; lx++) {
                    int sx = lx * srcSize / LOGO_SIZE;
                    int sy = ly * srcSize / LOGO_SIZE;
                    int i = ly * LOGO_SIZE + lx;
                    logoArgb[i] = decodeToArgb(source.getPixel(sx, sy));
                    logoOpaque[i] = ((logoArgb[i] >>> 24) & 0xFF) > 128;
                }
            }
            source.close();

            logoHalo = dilate(logoOpaque, LOGO_SIZE, LOGO_SIZE);
        } catch (IOException e) {
            logoLoadFailed = true;
        }
    }

    /**
     * NativeImage's plain getPixel format wasn't independently verified
     * (unlike setPixelABGR — see class doc), so this treats the packed int
     * as ABGR (same convention as setPixelABGR, the one format actually
     * confirmed correct in this codebase) and converts to plain ARGB.
     */
    private static int decodeToArgb(int abgr) {
        int a = (abgr >>> 24) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean[] dilate(boolean[] mask, int w, int h) {
        boolean[] result = new boolean[mask.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (mask[i]) {
                    continue; // halo is the ring OUTSIDE the solid logo, not over it
                }
                result[i] =
                    (x > 0 && mask[i - 1])
                        || (x < w - 1 && mask[i + 1])
                        || (y > 0 && mask[i - w])
                        || (y < h - 1 && mask[i + w]);
            }
        }
        return result;
    }

    private static int boostBrightness(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = clamp255((int) (((argb >>> 16) & 0xFF) * (1f + amount)));
        int g = clamp255((int) (((argb >>> 8) & 0xFF) * (1f + amount)));
        int b = clamp255((int) ((argb & 0xFF) * (1f + amount)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int backgroundArgb, int overlayArgb, float t) {
        int bgR = (backgroundArgb >>> 16) & 0xFF;
        int bgG = (backgroundArgb >>> 8) & 0xFF;
        int bgB = backgroundArgb & 0xFF;
        int fgR = (overlayArgb >>> 16) & 0xFF;
        int fgG = (overlayArgb >>> 8) & 0xFF;
        int fgB = overlayArgb & 0xFF;

        int r = Math.round(bgR + (fgR - bgR) * t);
        int g = Math.round(bgG + (fgG - bgG) * t);
        int b = Math.round(bgB + (fgB - bgB) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int toAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
