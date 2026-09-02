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
 */
public final class GlassClientCapeTexture {

    private GlassClientCapeTexture() {}

    private static final int WIDTH = 64;
    private static final int HEIGHT = 32;
    public static final ResourceLocation LOCATION =
        ResourceLocation.fromNamespaceAndPath("glassclient", "textures/entity/cape.png");

    private static boolean registered = false;

    /** Idempotent — safe to call every render frame, only does real work once. */
    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;

        NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
        // Icy-blue gradient matching GlassClient's own launcher UI palette
        // (--accent #8ed9f5 down to --accent-2 #4f7fe0), top to bottom.
        for (int y = 0; y < HEIGHT; y++) {
            float t = (float) y / (HEIGHT - 1);
            int r = lerp(0x8e, 0x4f, t);
            int g = lerp(0xd9, 0x7f, t);
            int b = lerp(0xf5, 0xe0, t);
            int argb = (0xFF << 24) | (r << 16) | (g << 8) | b;
            for (int x = 0; x < WIDTH; x++) {
                image.setPixelABGR(x, y, toAbgr(argb));
            }
        }

        DynamicTexture texture = new DynamicTexture(() -> "glassclient_cape", image);
        Minecraft.getInstance().getTextureManager().register(LOCATION, texture);
    }

    private static int lerp(int from, int to, float t) {
        return from + Math.round((to - from) * t);
    }

    private static int toAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
