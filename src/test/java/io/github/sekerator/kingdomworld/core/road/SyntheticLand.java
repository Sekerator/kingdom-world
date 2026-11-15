package io.github.sekerator.kingdomworld.core.road;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Land made up for the tests of the planner: every column from a function, owners from another, everything read at once.
 */
final class SyntheticLand implements LegPlanner.Land {
    static final UUID US = new UUID(7, 7);
    private final BiFunction<Integer, Integer, LandColumn> columns;
    private final BiFunction<Integer, Integer, UUID> owners;

    SyntheticLand(BiFunction<Integer, Integer, LandColumn> columns) {
        this(columns, (x, z) -> null);
    }

    SyntheticLand(BiFunction<Integer, Integer, LandColumn> columns, BiFunction<Integer, Integer, UUID> owners) {
        this.columns = columns;
        this.owners = owners;
    }

    /**
     * Plain land at a height with three blocks of soil.
     */
    static LandColumn land(int ground) {
        return LandColumn.land(ground, 3);
    }

    @Override
    public LandColumn at(int x, int z) {
        return columns.apply(x, z);
    }

    @Override
    public boolean read(Set<Long> chunks) {
        return true;
    }

    @Override
    public UUID owner(int x, int z) {
        return owners.apply(x >> 4 << 4, z >> 4 << 4);
    }

    @Override
    public CoarseRoute.Field field(int cx0, int cz0, int cx1, int cz1) {
        CoarseRoute.Field field = new CoarseRoute.Field(cx0, cz0, cx1 - cx0 + 1, cz1 - cz0 + 1);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                double sum = 0;
                double low = Double.POSITIVE_INFINITY;
                double high = Double.NEGATIVE_INFINITY;
                int wet = 0;
                double depth = 0;
                int built = 0;
                boolean closed = true;
                int n = 0;
                for (int dx = 0; dx < 16; dx += 4) {
                    for (int dz = 0; dz < 16; dz += 4) {
                        LandColumn column = at(cx * 16 + dx + 1, cz * 16 + dz + 1);
                        int top = column.top();
                        sum += top;
                        low = Math.min(low, top);
                        high = Math.max(high, top);
                        if (column.water()) {
                            wet++;
                            depth += column.waterTop() - column.ground();
                        }
                        built += column.has(LandColumn.BUILT) ? 1 : 0;
                        closed &= column.has(LandColumn.VOID) || column.has(LandColumn.BORDER);
                        n++;
                    }
                }
                UUID owner = owner(cx * 16, cz * 16);
                field.set(cx, cz, sum / n, low, high, wet / (double) n, wet == 0 ? 0 : depth / wet, built / (double) n,
                        owner != null && !owner.equals(US), closed);
            }
        }
        return field;
    }

    @Override
    public boolean guessed(int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public long roadAt(int x, int z) {
        return -1;
    }

    @Override
    public int minY() {
        return -64;
    }

    @Override
    public int maxY() {
        return 320;
    }

    /**
     * A planner of a gravel road on this land.
     */
    LegPlanner planner(RoadClass roadClass, RouteStyle style) {
        return new LegPlanner(this, roadClass, style, RoadRules.DEFAULT, EarthRules.DEFAULT, US, () -> false);
    }

    /**
     * A leg from the ground of one point to the ground of another.
     */
    LegPlanner.Spec spec(int fromX, int fromZ, int toX, int toZ) {
        LandColumn from = at(fromX, fromZ);
        LandColumn to = at(toX, toZ);
        return new LegPlanner.Spec(java.util.List.of(new int[]{fromX, fromZ, from.top()}),
                java.util.List.of(new EngineeredSearch.Goal(toX, toZ, to.water() ? EngineeredSearch.ANY : to.top())), fromX, fromZ, toX, toZ);
    }
}
