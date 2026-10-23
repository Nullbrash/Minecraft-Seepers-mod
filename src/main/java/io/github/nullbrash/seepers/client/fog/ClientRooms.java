package io.github.nullbrash.seepers.client.fog;

import io.github.nullbrash.seepers.room.Room;
import io.github.nullbrash.seepers.room.RoomRegistry;
import io.github.nullbrash.seepers.room.RoomSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client copy of the room logic for rendering: same rules as the server, own registry. The client gets no
 * block-change events, so the camera's room is re-filled periodically and open-space results expire.
 */
public final class ClientRooms {
    private static final int FORGET_OPEN_EVERY = 5;
    private static int ticks;
    private static int periods;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) return;
        if (++ticks % Math.max(1, RoomSettings.current().recheckTicks()) != 0) return;
        RoomRegistry registry = RoomRegistry.of(level);
        if (++periods % FORGET_OPEN_EVERY == 0) registry.forgetOpenCells();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        BlockPos pos = BlockPos.containing(camera.getPosition());
        Room room = registry.roomAt(pos);
        if (room != null) {
            registry.recheck(level, room);
        }
        registry.discover(level, pos);
    }

    private ClientRooms() {}
}
