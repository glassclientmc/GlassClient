package dev.glassclient.cosmetic;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Loads the real vanilla hunger icon texture (Minecraft's own shipped
 * resource, read via ResourceManager at runtime — not bundled by us, the
 * same way any resource pack reads it) and builds a 1px-dilated edge-ring
 * mask from its actual alpha shape, so the saturation outline
 * (SaturationOverlayMixin) can hug the real drumstick silhouette instead
 * of drawing a generic bounding-box rectangle — same dilate-mask technique
 * already used for the cosmetic cape's logo glow halo
 * (GlassClientCapeTexture), reused here against a real game asset instead
 * of a bundled one.
 */
public final class GlassClientHungerIconMask {

    private GlassClientHungerIconMask() {}

    // GUI sprites (used via GuiGraphics.blitSprite, as the vanilla food
    // icons are) live under textures/gui/sprites/..., not directly under
    // textures/... — confirmed by listing the actual client jar's real
    // asset paths after an initial guess (textures/hud/food_full.png)
    // resolved to nothing (Optional.empty(), confirmed via debug logging).
    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/food_full.png");

    private static boolean[] ringMask;
    private static int width;
    private static int height;
    private static boolean loadFailed = false;

    /** Lazily loads and caches on first use — the texture never changes at runtime. */
    public static void ensureLoaded() {
        if (ringMask != null || loadFailed) {
            return;
        }

        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(TEXTURE);
        if (resource.isEmpty()) {
            loadFailed = true;
            return;
        }

        try (InputStream in = resource.get().open()) {
            NativeImage image = NativeImage.read(in);
            width = image.getWidth();
            height = image.getHeight();

            boolean[] opaque = new boolean[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int abgr = image.getPixel(x, y);
                    int alpha = (abgr >>> 24) & 0xFF;
                    opaque[y * width + x] = alpha > 32;
                }
            }
            image.close();

            ringMask = dilate(opaque, width, height);
        } catch (IOException e) {
            loadFailed = true;
        }
    }

    private static boolean[] dilate(boolean[] mask, int w, int h) {
        boolean[] result = new boolean[mask.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (mask[i]) {
                    continue; // ring is the edge OUTSIDE the solid shape
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

    public static boolean isLoaded() {
        return ringMask != null;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    public static boolean isRing(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return false;
        }
        return ringMask[y * width + x];
    }
}
