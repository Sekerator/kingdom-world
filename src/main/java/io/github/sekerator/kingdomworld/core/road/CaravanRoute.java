package io.github.sekerator.kingdomworld.core.road;

import java.util.Objects;

/**
 * The way of a caravan on the roads, kept apart from the caravan itself.
 *
 * @param departTime game time the caravan left
 * @param checked    pieces of the way the caravan passed whose robbers were already reckoned with
 */
public record CaravanRoute(long caravanId, String dimension, long departTime, TravelPlan plan, int checked) {

    public CaravanRoute {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(plan, "plan");
        checked = Math.max(0, Math.min(plan.pieces().size(), checked));
    }

    public CaravanRoute withChecked(int value) {
        return new CaravanRoute(caravanId, dimension, departTime, plan, value);
    }

    public long arriveTime() {
        return departTime + plan.ticks();
    }

    /**
     * Where the caravan is at a game time.
     */
    public double[] position(long gameTime) {
        return plan.position(gameTime - departTime);
    }

    /**
     * Pieces of the way the caravan has passed by a game time.
     */
    public int passed(long gameTime) {
        long tick = gameTime - departTime;
        int count = 0;
        for (int i = 1; i < plan.points().size(); i++) {
            if (plan.points().get(i).tick() <= tick) {
                count = i;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * The middle of a piece of the way.
     */
    public int[] middle(int piece) {
        TravelPlan.Point a = plan.points().get(piece);
        TravelPlan.Point b = plan.points().get(piece + 1);
        return new int[]{(a.x() + b.x()) / 2, (a.z() + b.z()) / 2};
    }
}
