package io.github.nullbrash.seepers.registry;

import io.github.nullbrash.seepers.Seepers;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Own sound events and files (synthesized for the mod; generator and other takes in docs/sounds). */
public final class SeepersSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Seepers.MOD_ID);

    /** A tremor: one impersonal deep rumble that swells into the shake and dies away (own synthesized sound). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TREMOR_RUMBLE = SOUNDS.register("tremor.rumble",
            () -> SoundEvent.createVariableRangeEvent(Seepers.id("tremor.rumble")));

    /** The lidar's beam, looped at the listener while scanning: one of three, chosen in the client config (own synthesized sounds). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LIDAR_CLICKS = SOUNDS.register("lidar.clicks",
            () -> SoundEvent.createVariableRangeEvent(Seepers.id("lidar.clicks")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIDAR_CLICKS_DRY = SOUNDS.register("lidar.clicks_dry",
            () -> SoundEvent.createVariableRangeEvent(Seepers.id("lidar.clicks_dry")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIDAR_HUM = SOUNDS.register("lidar.hum",
            () -> SoundEvent.createVariableRangeEvent(Seepers.id("lidar.hum")));
    /** A lidar scan starting: a short soft chirp, heard only by the one scanning (own synthesized sound). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LIDAR_SCAN = SOUNDS.register("lidar.scan",
            () -> SoundEvent.createVariableRangeEvent(Seepers.id("lidar.scan")));

    /** The lidar refusing to scan on a flat or missing battery: a soft low double beep (own synthesized sound). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LIDAR_EMPTY = SOUNDS.register("lidar.empty",
            () -> SoundEvent.createVariableRangeEvent(Seepers.id("lidar.empty")));

    /** The lidar's hand dynamo cranking: loud, heard by everyone near (own synthesized sound). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LIDAR_DYNAMO = SOUNDS.register("lidar.dynamo",
            () -> SoundEvent.createVariableRangeEvent(Seepers.id("lidar.dynamo")));

    private SeepersSounds() {}
}
