package io.github.nullbrash.seepers.client;

import io.github.nullbrash.seepers.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** A light camera shake (collapses, tremors): fades out over its time; scaled or switched off in the client config. */
public final class CameraShake {
    private static float strength;
    private static int total;
    private static int left;

    public static void start(float degrees, int ticks) {
        // A stronger or longer shake replaces a weaker one; a weaker one never cuts a strong shake short.
        if (left > 0 && degrees * ticks <= strength * left) return;
        strength = degrees;
        total = Math.max(1, ticks);
        left = total;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (left > 0) left--;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (left <= 0 || Minecraft.getInstance().isPaused()) return;
        double scale = ClientConfig.CAMERA_SHAKE.get();
        if (scale <= 0) return;
        float time = (float) (total - left + event.getPartialTick());
        float amount = (float) (strength * scale * ((float) left / total));
        event.setYaw(event.getYaw() + amount * Mth.sin(time * 2.3F));
        event.setPitch(event.getPitch() + amount * 0.7F * Mth.sin(time * 3.1F + 1.0F));
        event.setRoll(event.getRoll() + amount * 0.5F * Mth.sin(time * 1.7F + 2.0F));
    }

    private CameraShake() {}
}
