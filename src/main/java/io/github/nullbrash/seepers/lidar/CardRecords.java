package io.github.nullbrash.seepers.lidar;

import java.nio.ByteBuffer;

/**
 * One memory card point record, 11 bytes: position inside its 16³ section in 1/1024 block (3 shorts), face, touch
 * material, colour (3 bytes, 0 = the material's). The server stores and sends them as they are; only the client reads them.
 */
public final class CardRecords {
    public static final int BYTES = 11;

    public static void put(ByteBuffer out, short x, short y, short z, int face, int material, int rgb) {
        out.putShort(x).putShort(y).putShort(z).put((byte) face).put((byte) material)
                .put((byte) (rgb >> 16)).put((byte) (rgb >> 8)).put((byte) rgb);
    }

    public interface Reader {
        void point(short x, short y, short z, int face, int material, int rgb);
    }

    public static void read(byte[] records, Reader reader) {
        ByteBuffer in = ByteBuffer.wrap(records);
        while (in.remaining() >= BYTES) {
            short x = in.getShort();
            short y = in.getShort();
            short z = in.getShort();
            int face = in.get() & 0xFF;
            int material = in.get() & 0xFF;
            int rgb = (in.get() & 0xFF) << 16 | (in.get() & 0xFF) << 8 | in.get() & 0xFF;
            reader.point(x, y, z, face, material, rgb);
        }
    }

    private CardRecords() {}
}
