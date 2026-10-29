package io.github.nullbrash.seepers.client.lidar;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The lidar device's hardware controls, drawn in pixels like the rest of its body: a detented knob, a rotary switch with
 * its positions marked around it, square latching buttons with a diode, a button under a flip cover, the battery lid's
 * screw. Each clicks like the thing it is. Knob and switch turn the way they are clicked: the right half to the right,
 * the left half to the left.
 */
final class LidarControls {
    static final int METAL = 0xFF56616B;
    static final int METAL_LIGHT = 0xFF7C8893;
    static final int DARK = 0xFF0C1013;

    /** A filled disc, row by row. */
    static void disc(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            graphics.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
        }
    }

    /** A line of square pixels from the centre outwards. */
    static void ray(GuiGraphics graphics, double cx, double cy, double angle, double from, double to, int color) {
        for (double d = from; d <= to; d += 0.5) {
            int x = (int) Math.round(cx + Math.cos(angle) * d);
            int y = (int) Math.round(cy + Math.sin(angle) * d);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** A round screw head with its slot, as all the body's screws look. */
    static void screwHead(GuiGraphics graphics, int cx, int cy, boolean hovered) {
        disc(graphics, cx, cy + 1, 3, DARK);
        disc(graphics, cx, cy, 3, hovered ? METAL_LIGHT : METAL);
        ray(graphics, cx, cy, Math.PI / 4, -2, 2, DARK);
    }

    private static void sound(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    /** Base for the controls: no vanilla button sound (each makes its own), the name as narration. */
    private abstract static class Control extends AbstractWidget {
        Control(int x, int y, int width, int height, Component name) {
            super(x, y, width, height, name);
            setTooltip(Tooltip.create(name));
        }

        @Override
        public void playDownSound(SoundManager sounds) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        /** -1 for a click on the left half, 1 on the right. */
        int side(double mouseX) {
            return mouseX < getX() + width / 2.0 ? -1 : 1;
        }
    }

    /** A knob that turns in steps between its ends. The pointer sweeps from 7 to 5 o'clock. */
    static final class Knob extends Control {
        private final IntSupplier position;
        private final IntSupplier steps;
        private final IntConsumer turn;

        Knob(int x, int y, int size, Component name, IntSupplier position, IntSupplier steps, IntConsumer turn) {
            super(x, y, size, size, name);
            this.position = position;
            this.steps = steps;
            this.turn = turn;
        }

        private void step(int by) {
            int next = position.getAsInt() + by;
            if (next < 0 || next >= steps.getAsInt()) return;
            turn.accept(next);
            sound(SoundEvents.LEVER_CLICK, by > 0 ? 1.3F : 1.1F, 0.35F);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            step(side(mouseX));
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
            step(scrollY > 0 ? 1 : -1);
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2;
            int cy = getY() + height / 2;
            int r = width / 2 - 1;
            int count = Math.max(1, steps.getAsInt());
            for (int i = 0; i < count; i++) {
                double a = Math.toRadians(135 + 270.0 * (count == 1 ? 0.5 : i / (double) (count - 1)));
                ray(graphics, cx, cy, a, r + 1, r + 2, i == position.getAsInt() ? 0xFF000000 | LidarHud.COLOR : 0xFF3A4650);
            }
            disc(graphics, cx, cy + 1, r, DARK);
            disc(graphics, cx, cy, r, isHoveredOrFocused() ? METAL_LIGHT : METAL);
            disc(graphics, cx, cy, r - 2, 0xFF3A444D);
            double a = Math.toRadians(135 + 270.0 * (count == 1 ? 0.5 : position.getAsInt() / (double) (count - 1)));
            ray(graphics, cx, cy, a, 1, r - 1, 0xFF000000 | LidarHud.COLOR);
        }
    }

    /** A rotary switch over a few positions, each marked by its icon around it; it stops at its end positions. */
    static final class RotarySwitch extends Control {
        private final IntSupplier position;
        private final int positions;
        private final IntConsumer pick;

        RotarySwitch(int x, int y, int size, Component name, int positions, IntSupplier position, IntConsumer pick) {
            super(x, y, size, size, name);
            this.position = position;
            this.positions = positions;
            this.pick = pick;
        }

        /** Angle of a position: spread over the whole upper half, from 9 to 3 o'clock. */
        double angle(int index) {
            return Math.toRadians(180 + 180.0 * index / Math.max(1, positions - 1));
        }

        private void set(int index) {
            if (index < 0 || index >= positions) return;
            pick.accept(index);
            sound(SoundEvents.LEVER_CLICK, 0.8F, 0.35F);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            set(position.getAsInt() + side(mouseX));
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
            set(position.getAsInt() + (scrollY > 0 ? 1 : -1));
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2;
            int cy = getY() + height / 2;
            int r = width / 2 - 1;
            disc(graphics, cx, cy + 1, r, DARK);
            disc(graphics, cx, cy, r, isHoveredOrFocused() ? METAL_LIGHT : METAL);
            // A bar across the knob: the switch's handle, pointing at its position.
            double a = angle(position.getAsInt());
            for (int w = -1; w <= 1; w++) {
                double nx = -Math.sin(a) * w;
                double ny = Math.cos(a) * w;
                ray(graphics, cx + nx, cy + ny, a, -r + 2, r - 1, w == 0 ? 0xFF2A3238 : 0xFF3A444D);
            }
            ray(graphics, cx, cy, a, r - 3, r - 1, 0xFF000000 | LidarHud.COLOR);
        }
    }

    /** A square button's cap, raised or pressed in, with a diode in its corner lit when pressed. */
    static void cap(GuiGraphics graphics, int x, int y, int size, boolean pressed, boolean hovered, boolean diode) {
        graphics.fill(x, y, x + size, y + size, DARK);
        int inset = pressed ? 2 : 1;
        int face = hovered ? METAL_LIGHT : METAL;
        graphics.fill(x + inset, y + inset, x + size - 1, y + size - 1, pressed ? 0xFF46515A : face);
        if (!pressed) {
            graphics.fill(x + 1, y + 1, x + size - 1, y + 2, 0xFFA9B4BE);
            graphics.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, 0xFF3A444D);
        }
        if (diode) graphics.fill(x + size - 4, y + 2, x + size - 2, y + 4, pressed ? 0xFFE8C040 : 0xFF2A3238);
    }

    /** A square latching button: one press holds it in (on, diode lit), the next lets it out. */
    static final class LatchButton extends Control {
        private final BooleanSupplier on;
        private final Runnable press;

        LatchButton(int x, int y, Component name, BooleanSupplier on, Runnable press) {
            super(x, y, 14, 14, name);
            this.on = on;
            this.press = press;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            press.run();
            sound(SoundEvents.STONE_BUTTON_CLICK_ON, on.getAsBoolean() ? 1.4F : 1.7F, 0.3F);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            cap(graphics, getX(), getY(), width, on.getAsBoolean(), isHoveredOrFocused(), true);
        }
    }

    /**
     * A square button under a red flip cover, for what cannot be undone: a click lifts the cover; with it up, a click on
     * the button fires it and a click on the lifted cover (the top strip) puts it back down. The cover stays as left.
     */
    static final class GuardedButton extends Control {
        private static final int COVER_STRIP = 5;
        private final Runnable fire;
        private final Component closedName;
        private final Component openName;
        private boolean open;

        GuardedButton(int x, int y, Component closedName, Component openName, Runnable fire) {
            super(x, y, 16, 20, closedName);
            this.fire = fire;
            this.closedName = closedName;
            this.openName = openName;
        }

        private void setOpen(boolean value) {
            open = value;
            setTooltip(Tooltip.create(open ? openName : closedName));
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (!open) {
                setOpen(true);
                sound(SoundEvents.IRON_TRAPDOOR_OPEN, 1.8F, 0.3F);
            } else if (mouseY < getY() + COVER_STRIP) {
                setOpen(false);
                sound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.8F, 0.3F);
            } else {
                fire.run();
                sound(SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F, 0.35F);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX() + 1;
            int y = getY() + 5;
            boolean overCover = isHoveredOrFocused() && mouseY < getY() + COVER_STRIP;
            if (!open) {
                // The cover down over the button.
                graphics.fill(x - 1, y - 1, x + 15, y + 15, 0xFF7A1E1A);
                graphics.fill(x, y, x + 14, y + 14, isHoveredOrFocused() ? 0xFFC0392B : 0xFFA02A22);
                graphics.fill(x, y, x + 14, y + 1, 0xFFD9584A);
                return;
            }
            cap(graphics, x, y, 14, false, isHoveredOrFocused() && !overCover, false);
            graphics.fill(x + 4, y + 4, x + 10, y + 10, 0xFF8A2A22);
            // Lifted: its edge stands above (click it to put the cover down).
            graphics.fill(x - 1, getY(), x + 15, getY() + 4, 0xFF7A1E1A);
            graphics.fill(x, getY(), x + 14, getY() + 1, overCover ? 0xFFF07060 : 0xFFD9584A);
        }
    }

    /**
     * The battery lid's screw: in its hole on the lid, a click takes it out (and the lid off); taken out it lies beside
     * the open bay, where a click screws it back (and the lid on).
     */
    static final class Screw extends Control {
        private final Runnable turn;
        private final BooleanSupplier out;
        private final int inX;
        private final int inY;
        private final int outX;
        private final int outY;
        private final Component inName;
        private final Component outName;

        Screw(int inX, int inY, int outX, int outY, Component inName, Component outName, BooleanSupplier out, Runnable turn) {
            super(inX, inY, 8, 8, inName);
            this.turn = turn;
            this.out = out;
            this.inX = inX;
            this.inY = inY;
            this.outX = outX;
            this.outY = outY;
            this.inName = inName;
            this.outName = outName;
            place();
        }

        /** Where it is now: in the lid or lying out. */
        void place() {
            boolean taken = out.getAsBoolean();
            setPosition(taken ? outX : inX, taken ? outY : inY);
            setTooltip(Tooltip.create(taken ? outName : inName));
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            boolean wasOut = out.getAsBoolean();
            turn.run();
            place();
            sound(wasOut ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN, 1.6F, 0.35F);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + 4;
            int cy = getY() + 4;
            int head = isHoveredOrFocused() ? METAL_LIGHT : METAL;
            if (out.getAsBoolean()) {
                // Lying on its side: head and a short threaded shank.
                graphics.fill(cx - 3, cy - 1, cx + 4, cy + 2, DARK);
                graphics.fill(cx - 3, cy - 2, cx, cy + 2, head);
                for (int i = 0; i < 4; i += 2) graphics.fill(cx + i, cy - 1, cx + i + 1, cy + 1, METAL);
                return;
            }
            screwHead(graphics, cx, cy, isHoveredOrFocused());
        }
    }

    private LidarControls() {}
}
