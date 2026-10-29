package io.github.nullbrash.seepers.client.lidar;

import io.github.nullbrash.seepers.lidar.Wiring;
import io.github.nullbrash.seepers.network.payload.WiringPayload;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The board under the lidar's panel (plan phase 9): pairs of contacts to join with wires that do not cross. Press on a
 * contact (or a wire's loose end) and drag cell by cell to its partner; back over the wire to pull it back; right-click
 * a wire to take it off. Joined all — the wiring goes to the server, which checks it and keeps the bonus.
 */
final class LidarWiring extends AbstractWidget {
    static final int CELL = 16;
    private static final int BOARD = 0xFF15301E;
    private static final int GRID = 0xFF1E4029;
    private static final int TRACE = 0xFF2B5A3A;

    private final Wiring.Board board;
    private final IntUnaryOperator color;
    private final boolean offhand;
    private final List<IntArrayList> wires = new ArrayList<>();
    private int dragging = -1;
    private boolean sent;

    /** color: a pair's kind (Wiring.BASE or a module kind) to its colour. */
    LidarWiring(int x, int y, Wiring.Board board, IntUnaryOperator color, boolean offhand) {
        super(x, y, Wiring.COLUMNS * CELL, Wiring.ROWS * CELL, Component.translatable("seepers.lidar.screen.wiring"));
        this.board = board;
        this.color = color;
        this.offhand = offhand;
        for (int i = 0; i < board.pairs().size(); i++) wires.add(new IntArrayList());
    }

    Wiring.Board board() {
        return board;
    }

    int joined() {
        int count = 0;
        for (int i = 0; i < wires.size(); i++) if (complete(i)) count++;
        return count;
    }

    int length() {
        int length = 0;
        for (IntArrayList wire : wires) length += Math.max(0, wire.size() - 1);
        return length;
    }

    boolean sent() {
        return sent;
    }

    private boolean complete(int pair) {
        IntArrayList wire = wires.get(pair);
        if (wire.size() < 2) return false;
        Wiring.Pair p = board.pairs().get(pair);
        int a = wire.getInt(0);
        int b = wire.getInt(wire.size() - 1);
        return (a == p.a() && b == p.b()) || (a == p.b() && b == p.a());
    }

    private int cellAt(double mouseX, double mouseY) {
        int cx = (int) Math.floor((mouseX - getX()) / CELL);
        int cy = (int) Math.floor((mouseY - getY()) / CELL);
        return cx < 0 || cy < 0 || cx >= Wiring.COLUMNS || cy >= Wiring.ROWS ? -1 : cy * Wiring.COLUMNS + cx;
    }

    /** The wire through a cell, or -1. */
    private int wireAt(int cell) {
        for (int i = 0; i < wires.size(); i++) if (wires.get(i).contains(cell)) return i;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        int cell = cellAt(mouseX, mouseY);
        if (cell < 0) return false;
        if (button == 1) {
            int wire = wireAt(cell);
            if (wire >= 0) {
                wires.get(wire).clear();
                sent = false;
                sound(0.8F);
            }
            return true;
        }
        if (button != 0) return false;
        int pair = board.pairAt(cell);
        if (pair >= 0) {
            // A contact: a fresh wire from it.
            wires.get(pair).clear();
            wires.get(pair).add(cell);
            dragging = pair;
            sent = false;
            sound(1.4F);
            return true;
        }
        int wire = wireAt(cell);
        if (wire >= 0 && !complete(wire)) {
            // A loose wire: go on from where it was pressed.
            IntArrayList path = wires.get(wire);
            path.size(path.indexOf(cell) + 1);
            dragging = wire;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging < 0 || button != 0) return false;
        int cell = cellAt(mouseX, mouseY);
        if (cell < 0) return true;
        IntArrayList path = wires.get(dragging);
        if (path.isEmpty()) return true;
        int last = path.getInt(path.size() - 1);
        if (cell == last) return true;
        int back = path.indexOf(cell);
        if (back >= 0) {
            // Back over its own wire: pulled back to there.
            path.size(back + 1);
            return true;
        }
        if (complete(dragging) || !Wiring.adjacent(last, cell) || board.capacitors().get(cell) || wireAt(cell) >= 0) return true;
        int contact = board.pairAt(cell);
        if (contact >= 0 && contact != dragging) return true;
        path.add(cell);
        if (contact == dragging) {
            sound(1.8F);
            dragging = -1;
            submit();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = -1;
        return false;
    }

    /** All joined: the server checks the wiring and keeps its bonus. */
    private void submit() {
        if (joined() < wires.size()) return;
        List<int[]> paths = new ArrayList<>();
        for (IntArrayList wire : wires) paths.add(wire.toIntArray());
        if (Wiring.check(board, paths) < 0) return;
        PacketDistributor.sendToServer(new WiringPayload(offhand, paths));
        sent = true;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F, 0.4F));
    }

    private static void sound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_ATTACH, pitch, 0.3F));
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sounds) {
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x0 = getX();
        int y0 = getY();
        graphics.fill(x0 - 3, y0 - 3, x0 + width + 3, y0 + height + 3, 0xFF0C1013);
        graphics.fill(x0, y0, x0 + width, y0 + height, BOARD);
        // Etched traces and solder pads: a board, not a grid.
        for (int cy = 0; cy < Wiring.ROWS; cy++) {
            for (int cx = 0; cx < Wiring.COLUMNS; cx++) {
                int px = x0 + cx * CELL + CELL / 2;
                int py = y0 + cy * CELL + CELL / 2;
                graphics.fill(px - 1, py - 1, px + 1, py + 1, GRID);
                if (cx < Wiring.COLUMNS - 1 && (cx + cy * 3) % 7 == 0) graphics.fill(px, py, px + CELL, py + 1, TRACE);
            }
        }
        for (int cell = board.capacitors().nextSetBit(0); cell >= 0; cell = board.capacitors().nextSetBit(cell + 1)) {
            int px = x0 + cell % Wiring.COLUMNS * CELL;
            int py = y0 + cell / Wiring.COLUMNS * CELL;
            graphics.fill(px + 3, py + 2, px + 13, py + 14, 0xFF0C1013);
            graphics.fill(px + 4, py + 3, px + 12, py + 13, 0xFF2D4F8E);
            graphics.fill(px + 4, py + 3, px + 6, py + 13, 0xFF6A8CC8);
            graphics.fill(px + 4, py + 5, px + 12, py + 6, 0xFFB8C8E0);
        }
        for (int i = 0; i < wires.size(); i++) {
            int argb = 0xFF000000 | color.applyAsInt(board.pairs().get(i).kind());
            IntArrayList wire = wires.get(i);
            for (int k = 1; k < wire.size(); k++) segment(graphics, wire.getInt(k - 1), wire.getInt(k), argb);
        }
        for (int i = 0; i < board.pairs().size(); i++) {
            Wiring.Pair pair = board.pairs().get(i);
            int argb = 0xFF000000 | color.applyAsInt(pair.kind());
            boolean done = complete(i);
            contact(graphics, pair.a(), argb, done);
            contact(graphics, pair.b(), argb, done);
        }
        int hover = isMouseOver(mouseX, mouseY) ? cellAt(mouseX, mouseY) : -1;
        if (hover >= 0) {
            int px = x0 + hover % Wiring.COLUMNS * CELL;
            int py = y0 + hover / Wiring.COLUMNS * CELL;
            graphics.fill(px, py, px + CELL, py + 1, 0x60FFFFFF);
            graphics.fill(px, py + CELL - 1, px + CELL, py + CELL, 0x60FFFFFF);
        }
    }

    /** A wire between two neighbouring cells: a thick strand from centre to centre. */
    private void segment(GuiGraphics graphics, int from, int to, int argb) {
        int ax = getX() + from % Wiring.COLUMNS * CELL + CELL / 2;
        int ay = getY() + from / Wiring.COLUMNS * CELL + CELL / 2;
        int bx = getX() + to % Wiring.COLUMNS * CELL + CELL / 2;
        int by = getY() + to / Wiring.COLUMNS * CELL + CELL / 2;
        graphics.fill(Math.min(ax, bx) - 2, Math.min(ay, by) - 2, Math.max(ax, bx) + 3, Math.max(ay, by) + 3, 0xFF0C1013);
        graphics.fill(Math.min(ax, bx) - 1, Math.min(ay, by) - 1, Math.max(ax, bx) + 2, Math.max(ay, by) + 2, argb);
    }

    /** A contact: a pad in its pair's colour, ringed bright once joined. */
    private void contact(GuiGraphics graphics, int cell, int argb, boolean done) {
        int px = getX() + cell % Wiring.COLUMNS * CELL;
        int py = getY() + cell / Wiring.COLUMNS * CELL;
        graphics.fill(px + 2, py + 2, px + 14, py + 14, done ? 0xFFE8E0B0 : 0xFF0C1013);
        graphics.fill(px + 3, py + 3, px + 13, py + 13, argb);
        graphics.fill(px + 6, py + 6, px + 10, py + 10, 0xFFB08A38);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
