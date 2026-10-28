package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.registry.SeepersSounds;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * The lidar dynamo's crank, heard by everyone near (CLAUDE.md 5.3): one seamless loop per cranking player, following
 * them, started and stopped by the server. A clip per half second would gap between plays.
 */
public final class DynamoSounds {
    /** Sounds carry 16 blocks per unit of volume: about 24 blocks. */
    private static final float VOLUME = 1.5F;
    private static final Int2ObjectOpenHashMap<Crank> PLAYING = new Int2ObjectOpenHashMap<>();

    public static void set(int entityId, boolean cranking) {
        Crank crank = PLAYING.get(entityId);
        if (cranking) {
            if (crank != null && !crank.isStopped()) {
                crank.on = true;
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
            if (entity == null) return;
            crank = new Crank(entity);
            PLAYING.put(entityId, crank);
            minecraft.getSoundManager().play(crank);
        } else if (crank != null) {
            crank.on = false;
        }
    }

    public static void clear() {
        PLAYING.values().forEach(crank -> crank.on = false);
        PLAYING.clear();
    }

    private static final class Crank extends AbstractTickableSoundInstance {
        private final Entity entity;
        boolean on = true;

        Crank(Entity entity) {
            super(SeepersSounds.LIDAR_DYNAMO.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.entity = entity;
            looping = true;
            delay = 0;
            volume = 0.01F;
            x = entity.getX();
            y = entity.getY();
            z = entity.getZ();
        }

        @Override
        public void tick() {
            if (entity.isRemoved()) {
                stop();
                return;
            }
            x = entity.getX();
            y = entity.getY();
            z = entity.getZ();
            volume = on ? Math.min(VOLUME, volume + VOLUME / 3) : volume - VOLUME / 3;
            if (volume <= 0) stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private DynamoSounds() {}
}
