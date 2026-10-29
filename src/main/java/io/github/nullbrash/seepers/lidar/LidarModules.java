package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.ModuleItem;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The lidar's upgrades (CLAUDE.md 5.3): one slot per kind, the module's level is the kind's level. Kept in the lidar
 * as component {@code seepers:modules} (slot i = kind i). Upgrades widen and deepen the scan, never its range; wider
 * and denser scans drain the battery faster. Numbers are in the server config.
 */
public final class LidarModules {
    public enum Kind {
        // Appended only: a lidar keeps its modules by slot index. MEMORY's slot holds the memory card (no module).
        VIEW(3), DENSITY(3), MEMORY(3), ANALYSIS(3), FILTER(3), WATER(2), PATTERN(1), ECHO(1), DYNAMO(1), REFRESH(1), BEAMS(3);

        public final int maxLevel;

        Kind(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Scan patterns the pattern module chooses from; without it the lidar draws the rosette. */
    public enum Pattern {
        ROSETTE, SPIRAL, LINES, RANDOM;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int SLOTS = Kind.values().length;

    /** The beam's field: a cone around the view (coneHalf degrees) or a centred part of the screen (screenShare). */
    public enum Field { CONE, SCREEN }

    /**
     * A choice of scan area the player sets on the lidar's screen, within what its view module gives: cones every 10°
     * up to the module's width, then view II's and III's parts of the screen. tier: which view level's drain it costs.
     */
    public record Area(Field field, double value, int tier) {}

    /**
     * What a lidar does with its modules. view: the view module's level (II and III also have the special scans:
     * 180 and 360). pointsPerSecond is one beam's; beams: how many run at once (the beams module). card: the memory
     * card's level (0 = none), pointLimit its capacity.
     */
    public record Stats(int view, Field field, double coneHalf, double screenShare, int pointsPerSecond, int pointLimit, int card,
                        double cost, Pattern pattern, boolean dynamo, boolean refresh, int beams) {
        /** Points per second of all beams together. */
        public int rate() {
            return pointsPerSecond * beams;
        }
    }

    /** The scan areas a view level offers, narrowest first; the default is the widest. */
    public static List<Area> areas(int view) {
        List<Area> areas = new ArrayList<>();
        double base = ServerConfig.get(ServerConfig.LIDAR_CONE_DEGREES);
        double wide = ServerConfig.get(ServerConfig.LIDAR_VIEW_CONE_DEGREES);
        double max = view == 0 ? base : wide;
        for (double degrees = 10; degrees < max - 0.01; degrees += 10) areas.add(new Area(Field.CONE, degrees, degrees <= base ? 0 : 1));
        areas.add(new Area(Field.CONE, max, max <= base ? 0 : 1));
        if (view >= 2) areas.add(new Area(Field.SCREEN, ServerConfig.get(ServerConfig.LIDAR_VIEW_SCREEN_2), 2));
        if (view >= 3) areas.add(new Area(Field.SCREEN, ServerConfig.get(ServerConfig.LIDAR_VIEW_SCREEN_3), 2));
        return areas;
    }

    /** The lidar's chosen area index within areas(view), clamped; absent = the widest. */
    public static int areaIndex(ItemStack lidar) {
        int count = areas(level(lidar, Kind.VIEW)).size();
        Integer chosen = lidar.get(SeepersComponents.AREA.get());
        return chosen == null ? count - 1 : Math.max(0, Math.min(count - 1, chosen));
    }

    public static NonNullList<ItemStack> contents(ItemStack lidar) {
        NonNullList<ItemStack> slots = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        lidar.getOrDefault(SeepersComponents.MODULES.get(), ItemContainerContents.EMPTY).copyInto(slots);
        return slots;
    }

    public static void store(ItemStack lidar, NonNullList<ItemStack> slots) {
        if (slots.stream().allMatch(ItemStack::isEmpty)) lidar.remove(SeepersComponents.MODULES.get());
        else lidar.set(SeepersComponents.MODULES.get(), ItemContainerContents.fromItems(slots));
    }

    public static int level(ItemStack lidar, Kind kind) {
        ItemContainerContents modules = lidar.get(SeepersComponents.MODULES.get());
        if (modules == null || modules.getSlots() <= kind.ordinal()) return 0;
        ItemStack module = modules.getStackInSlot(kind.ordinal());
        return module.getItem() instanceof ModuleItem item && item.kind() == kind ? item.level() : 0;
    }

    /** The memory card in the lidar (the memory slot), or empty. */
    public static ItemStack card(ItemStack lidar) {
        ItemContainerContents modules = lidar.get(SeepersComponents.MODULES.get());
        if (modules == null || modules.getSlots() <= Kind.MEMORY.ordinal()) return ItemStack.EMPTY;
        ItemStack card = modules.getStackInSlot(Kind.MEMORY.ordinal());
        return card.getItem() instanceof io.github.nullbrash.seepers.item.MemoryCardItem ? card : ItemStack.EMPTY;
    }

    /** Puts a (changed) memory card into the lidar's card slot. */
    public static void setCard(ItemStack lidar, ItemStack card) {
        NonNullList<ItemStack> slots = contents(lidar);
        slots.set(Kind.MEMORY.ordinal(), card);
        store(lidar, slots);
    }

    public static Pattern pattern(ItemStack lidar) {
        if (level(lidar, Kind.PATTERN) == 0) return Pattern.ROSETTE;
        int id = lidar.getOrDefault(SeepersComponents.PATTERN.get(), 0);
        return Pattern.values()[Math.floorMod(id, Pattern.values().length)];
    }

    /**
     * The drain of this lidar's scan. The ordinary scan costs by its chosen area; the special scans: view II's 180 its
     * own, the 360 and the repair pass view III's.
     */
    public static double cost(ItemStack lidar, boolean special) {
        Stats stats = stats(lidar);
        if (!special) return stats.cost();
        double scanCost = stats.view() == 2 ? ServerConfig.get(ServerConfig.LIDAR_WIDE_COST) : ServerConfig.get(ServerConfig.LIDAR_VIEW_COST_3);
        return stats.cost() / tierCost(areas(stats.view()).get(areaIndex(lidar)).tier()) * scanCost;
    }

    private static double tierCost(int tier) {
        return switch (tier) {
            case 0 -> 1;
            case 1 -> ServerConfig.get(ServerConfig.LIDAR_VIEW_COST_1);
            default -> ServerConfig.get(ServerConfig.LIDAR_VIEW_COST_2);
        };
    }

    public static Stats stats(ItemStack lidar) {
        int view = level(lidar, Kind.VIEW);
        int density = level(lidar, Kind.DENSITY);
        ItemStack card = card(lidar);
        int memory = card.getItem() instanceof io.github.nullbrash.seepers.item.MemoryCardItem item ? item.level() : 0;
        Area area = areas(view).get(areaIndex(lidar));
        int points = switch (density) {
            case 0 -> ServerConfig.get(ServerConfig.LIDAR_POINTS_PER_SECOND);
            case 1 -> ServerConfig.get(ServerConfig.LIDAR_DENSITY_1);
            case 2 -> ServerConfig.get(ServerConfig.LIDAR_DENSITY_2);
            default -> ServerConfig.get(ServerConfig.LIDAR_DENSITY_3);
        };
        int limit = memory == 0 ? 0 : io.github.nullbrash.seepers.item.MemoryCardItem.capacity(memory);
        double densityCost = switch (density) {
            case 0 -> 1;
            case 1 -> ServerConfig.get(ServerConfig.LIDAR_DENSITY_COST_1);
            case 2 -> ServerConfig.get(ServerConfig.LIDAR_DENSITY_COST_2);
            default -> ServerConfig.get(ServerConfig.LIDAR_DENSITY_COST_3);
        };
        // Beams meet each other along the pattern: I from its start and end, II and III twice and four times that.
        int beamsLevel = level(lidar, Kind.BEAMS);
        int beams = beamsLevel == 0 ? 1 : 1 << beamsLevel;
        double beamsCost = switch (beamsLevel) {
            case 0 -> 1;
            case 1 -> ServerConfig.get(ServerConfig.LIDAR_BEAMS_COST_1);
            case 2 -> ServerConfig.get(ServerConfig.LIDAR_BEAMS_COST_2);
            default -> ServerConfig.get(ServerConfig.LIDAR_BEAMS_COST_3);
        };
        boolean cone = area.field() == Field.CONE;
        return new Stats(view, area.field(), cone ? area.value() / 2 : 0, cone ? 0 : area.value(), points, limit, memory,
                tierCost(area.tier()) * densityCost * beamsCost, pattern(lidar),
                level(lidar, Kind.DYNAMO) > 0, level(lidar, Kind.REFRESH) > 0, beams);
    }

    private LidarModules() {}
}
