package dev.glassclient.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds GlassClient's cape texture in memory rather than shipping a PNG
 * resource — this mod isn't a Fabric/Forge mod and never registers a
 * resource pack with Minecraft's ResourceManager, so a bundled texture file
 * wouldn't actually be discoverable via a normal ResourceLocation lookup.
 * Registering a manually-built DynamicTexture directly with the
 * TextureManager sidesteps needing that whole pack-injection pipeline.
 * Standard 64x32 cape texture layout — same dimensions vanilla's own cape
 * textures use, so PlayerCapeModel's UVs line up correctly.
 *
 * Animated: the hue slowly cycles cyan -> blue -> purple -> back (a
 * triangle wave, not a hard jump at the loop point) and re-uploads the
 * texture periodically rather than once — same idea as an animated cape
 * texture in a resource pack, just generated instead of drawn frame-by-
 * frame by hand.
 */
public final class GlassClientCapeTexture {

    private GlassClientCapeTexture() {}

    private static final int WIDTH = 64;
    private static final int HEIGHT = 32;
    public static final ResourceLocation LOCATION =
        ResourceLocation.fromNamespaceAndPath("glassclient", "textures/entity/cape.png");

    // Re-uploading a texture is not free — throttled well below frame rate
    // rather than regenerating on every render call (which can happen
    // multiple times per frame with several players visible).
    private static final long UPDATE_INTERVAL_MS = 66; // ~15 updates/sec
    private static final long CYCLE_PERIOD_MS = 4000;
    // Cyan (180deg) through blue-purple (260deg), in HSB's 0..1 hue scale.
    private static final float HUE_MIN = 0.50f;
    private static final float HUE_MAX = 0.72f;

    private static DynamicTexture texture;
    private static NativeImage image;
    private static long lastUpdateMs = -1;

    /** Safe to call every render frame — throttles its own real work internally. */
    public static void update() {
        long now = System.currentTimeMillis();
        if (texture != null && now - lastUpdateMs < UPDATE_INTERVAL_MS) {
            return;
        }
        lastUpdateMs = now;

        if (texture == null) {
            image = new NativeImage(WIDTH, HEIGHT, false);
            texture = new DynamicTexture(() -> "glassclient_cape", image);
            Minecraft.getInstance().getTextureManager().register(LOCATION, texture);
        }

        float phase = (now % CYCLE_PERIOD_MS) / (float) CYCLE_PERIOD_MS;
        float triangle = phase < 0.5f ? phase * 2f : 2f - phase * 2f;
        float hue = HUE_MIN + (HUE_MAX - HUE_MIN) * triangle;

        for (int y = 0; y < HEIGHT; y++) {
            // Brighter near the collar, deeper toward the hem — same
            // vertical-gradient depth the original static version had,
            // layered on top of the time-based hue shift.
            float brightness = 1.0f - 0.35f * ((float) y / (HEIGHT - 1));
            int argb = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.55f, brightness) & 0xFFFFFF);
            int abgr = toAbgr(argb);
            for (int x = 0; x < WIDTH; x++) {
                image.setPixelABGR(x, y, abgr);
            }
        }

        texture.upload();
    }

    private static int toAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
