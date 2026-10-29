package io.github.nullbrash.seepers.lidar;

import io.github.nullbrash.seepers.config.ServerConfig;
import io.github.nullbrash.seepers.item.ModuleItem;
import io.github.nullbrash.seepers.registry.SeepersComponents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The wiring inside the lidar (CLAUDE.md 5.3, plan phase 9): a board of cells with pairs of contacts — battery to
 * scanner, one pair per module in, the dynamo's on its own long circuit — and capacitors for modules II-III in the way.
 * The board follows from the modules and their slots alone (the same set, the same board), built from non-crossing
 * paths so it always has a solution. Wires join each pair without crossing; the shorter they are against the shortest
 * possible, the bigger the bonus: less battery drain, and with the dynamo a stronger crank. The server checks a wiring
 * itself; the bonus holds while the modules stay as they were wired.
 */
public final class Wiring {
    public static final int COLUMNS = 16;
    public static final int ROWS = 7;
    /** Colour index of the battery-to-scanner pair; the others carry their module kind's ordinal. */
    public static final int BASE = -1;
    private static final int ATTEMPTS = 64;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    /** A pair of contacts to join, cell indexes a and b; kind: BASE or a module kind's ordinal. */
    public record Pair(int kind, int a, int b) {}

    /** minimal: the sum of each pair's shortest path around the capacitors, the best a wiring can do. */
    public record Board(List<Pair> pairs, BitSet capacitors, int minimal, boolean dynamo) {
        public int pairAt(int cell) {
            for (int i = 0; i < pairs.size(); i++) if (pairs.get(i).a() == cell || pairs.get(i).b() == cell) return i;
            return -1;
        }
    }

    /** The modules as wired (memory card and battery are not part of it): their ids by slot. */
    public static int signature(ItemStack lidar) {
        NonNullList<ItemStack> modules = LidarModules.contents(lidar);
        int hash = 17;
        for (int i = 0; i < modules.size(); i++) {
            if (i == LidarModules.Kind.MEMORY.ordinal() || !(modules.get(i).getItem() instanceof ModuleItem)) continue;
            hash = hash * 31 + i;
            hash = hash * 31 + BuiltInRegistries.ITEM.getKey(modules.get(i).getItem()).hashCode();
        }
        return hash;
    }

    public static Board board(ItemStack lidar) {
        NonNullList<ItemStack> modules = LidarModules.contents(lidar);
        List<Integer> kinds = new ArrayList<>();
        boolean dynamo = false;
        int capacitors = 0;
        for (int i = 0; i < modules.size(); i++) {
            if (!(modules.get(i).getItem() instanceof ModuleItem module)) continue;
            if (module.kind() == LidarModules.Kind.DYNAMO) dynamo = true;
            else kinds.add(module.kind().ordinal());
            capacitors += (module.level() - 1) * 2;
        }
        // The dynamo's long circuit first, then the base pair, then the modules.
        List<Integer> order = new ArrayList<>();
        if (dynamo) order.add(LidarModules.Kind.DYNAMO.ordinal());
        order.add(BASE);
        order.addAll(kinds);
        RandomSource random = RandomSource.create(signature(lidar));
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Board board = build(order, capacitors, dynamo, random);
            if (board != null) return board;
        }
        // Too crowded to lay out: the base pair alone, so there is always something to wire.
        Board fallback = build(List.of(BASE), 0, false, random);
        return fallback != null ? fallback : new Board(List.of(new Pair(BASE, 0, COLUMNS - 1)), new BitSet(), COLUMNS - 1, false);
    }

    private static @Nullable Board build(List<Integer> order, int capacitorCount, boolean dynamo, RandomSource random) {
        int cells = COLUMNS * ROWS;
        BitSet used = new BitSet(cells);
        List<Pair> pairs = new ArrayList<>();
        for (int kind : order) {
            boolean longCircuit = kind == LidarModules.Kind.DYNAMO.ordinal();
            int target = longCircuit ? 16 + random.nextInt(6) : 4 + random.nextInt(7);
            int[] path = null;
            for (int tries = 0; tries < 24 && path == null; tries++) path = walk(used, target, random);
            if (path == null) return null;
            for (int cell : path) used.set(cell);
            pairs.add(new Pair(kind, path[0], path[path.length - 1]));
        }
        BitSet capacitors = new BitSet(cells);
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < cells; i++) if (!used.get(i)) free.add(i);
        for (int i = 0; i < capacitorCount && !free.isEmpty(); i++) capacitors.set(free.remove(random.nextInt(free.size())));
        int minimal = 0;
        BitSet contacts = new BitSet(cells);
        for (Pair pair : pairs) {
            contacts.set(pair.a());
            contacts.set(pair.b());
        }
        for (Pair pair : pairs) {
            int shortest = shortest(pair, capacitors, contacts);
            if (shortest < 0) return null;
            minimal += shortest;
        }
        return new Board(List.copyOf(pairs), capacitors, minimal, dynamo);
    }

    /** A random self-avoiding walk of about target steps through free cells; null if it gets stuck early. */
    private static int @Nullable [] walk(BitSet used, int target, RandomSource random) {
        int cells = COLUMNS * ROWS;
        int start = random.nextInt(cells);
        if (used.get(start)) return null;
        List<Integer> path = new ArrayList<>();
        BitSet mine = new BitSet(cells);
        path.add(start);
        mine.set(start);
        int direction = random.nextInt(4);
        while (path.size() <= target) {
            int at = path.get(path.size() - 1);
            int[] options = new int[4];
            int count = 0;
            for (int d = 0; d < 4; d++) {
                int next = step(at, d);
                if (next >= 0 && !used.get(next) && !mine.get(next)) options[count++] = d;
            }
            if (count == 0) break;
            // Mostly straight on, sometimes a turn: wires read as wires, not as scribbles.
            int pick = options[random.nextInt(count)];
            for (int i = 0; i < count; i++) if (options[i] == direction && random.nextFloat() < 0.6F) pick = direction;
            direction = pick;
            int next = step(at, pick);
            path.add(next);
            mine.set(next);
        }
        if (path.size() < 4) return null;
        return path.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The cell one step from cell in direction d, or -1 off the board. */
    public static int step(int cell, int d) {
        int x = cell % COLUMNS + DX[d];
        int y = cell / COLUMNS + DY[d];
        return x < 0 || y < 0 || x >= COLUMNS || y >= ROWS ? -1 : y * COLUMNS + x;
    }

    public static boolean adjacent(int a, int b) {
        return Math.abs(a % COLUMNS - b % COLUMNS) + Math.abs(a / COLUMNS - b / COLUMNS) == 1;
    }

    /** Steps of the shortest path between a pair's contacts around capacitors and other contacts; -1 if none. */
    private static int shortest(Pair pair, BitSet capacitors, BitSet contacts) {
        int cells = COLUMNS * ROWS;
        int[] distance = new int[cells];
        java.util.Arrays.fill(distance, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        distance[pair.a()] = 0;
        queue.add(pair.a());
        while (!queue.isEmpty()) {
            int at = queue.poll();
            if (at == pair.b()) return distance[at];
            for (int d = 0; d < 4; d++) {
                int next = step(at, d);
                if (next < 0 || distance[next] >= 0 || capacitors.get(next)) continue;
                if (contacts.get(next) && next != pair.b()) continue;
                distance[next] = distance[at] + 1;
                queue.add(next);
            }
        }
        return -1;
    }

    /**
     * Checks a wiring: one path per pair (cell indexes, from one contact to the other in steps between neighbours),
     * none through capacitors or other contacts, no cell shared. Returns its total length, or -1 if it is not a solution.
     */
    public static int check(Board board, List<int[]> paths) {
        if (paths.size() != board.pairs().size()) return -1;
        int cells = COLUMNS * ROWS;
        BitSet taken = new BitSet(cells);
        BitSet contacts = new BitSet(cells);
        for (Pair pair : board.pairs()) {
            contacts.set(pair.a());
            contacts.set(pair.b());
        }
        int length = 0;
        for (int i = 0; i < paths.size(); i++) {
            int[] path = paths.get(i);
            Pair pair = board.pairs().get(i);
            if (path.length < 2) return -1;
            boolean forward = path[0] == pair.a() && path[path.length - 1] == pair.b();
            boolean backward = path[0] == pair.b() && path[path.length - 1] == pair.a();
            if (!forward && !backward) return -1;
            for (int k = 0; k < path.length; k++) {
                int cell = path[k];
                if (cell < 0 || cell >= cells || taken.get(cell) || board.capacitors().get(cell)) return -1;
                if (k > 0 && !adjacent(path[k - 1], cell)) return -1;
                if (k > 0 && k < path.length - 1 && contacts.get(cell)) return -1;
                taken.set(cell);
            }
            length += path.length - 1;
        }
        return length;
    }

    /** How good a wiring of this length is: 0 at twice the shortest (or longer), 1 at the shortest. */
    private static float quality(Board board, int length) {
        float efficiency = board.minimal() / (float) Math.max(length, board.minimal());
        return Mth.clamp((efficiency - 0.5F) / 0.5F, 0, 1);
    }

    public static float drainCut(Board board, int length) {
        return (float) Mth.lerp(quality(board, length), ServerConfig.get(ServerConfig.LIDAR_WIRING_DRAIN_MIN), ServerConfig.get(ServerConfig.LIDAR_WIRING_DRAIN_MAX));
    }

    public static float chargeBoost(Board board, int length) {
        if (!board.dynamo()) return 0;
        return (float) Mth.lerp(quality(board, length), ServerConfig.get(ServerConfig.LIDAR_WIRING_CHARGE_MIN), ServerConfig.get(ServerConfig.LIDAR_WIRING_CHARGE_MAX));
    }

    /** The lidar's wiring bonus, if its modules are still as they were wired: drain cut, crank boost. */
    public static float[] bonus(ItemStack lidar) {
        WiringData data = lidar.get(SeepersComponents.WIRING.get());
        if (data == null || data.signature() != signature(lidar)) return new float[] {0, 0};
        return new float[] {data.drainCut(), data.chargeBoost()};
    }

    /** The player's wiring of the lidar in that hand: kept if it solves the board (and beats or replaces the last). */
    public static void handle(io.github.nullbrash.seepers.network.payload.WiringPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        ItemStack lidar = player.getItemInHand(payload.offhand() ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!(lidar.getItem() instanceof io.github.nullbrash.seepers.item.LidarItem)) return;
        Board board = board(lidar);
        int length = check(board, payload.paths());
        if (length < 0) return;
        lidar.set(SeepersComponents.WIRING.get(), new WiringData(signature(lidar), drainCut(board, length), chargeBoost(board, length), length, board.minimal()));
    }

    private Wiring() {}
}
