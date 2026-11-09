package io.github.sekerator.kingdomworld.core.combat;

/**
 * Where a blow comes from, seen by a squad already in a battle: from the front it faces (its first enemy), from a side, or
 * from the rear. A blow at the side or the rear of a squad tied in a battle is stronger.
 */
public enum Flank {
    FRONT,
    SIDE,
    REAR;

    /**
     * How much stronger a blow from the side or the rear is.
     *
     * @param side share added to the strength of a blow at a side
     * @param rear share added to the strength of a blow at the rear
     */
    public record Rules(double side, double rear) {
        public static final Rules DEFAULT = new Rules(0.20, 0.35);

        public Rules {
            if (!(side >= 0.0 && side <= 2.0) || !(rear >= 0.0 && rear <= 2.0)) {
                throw new IllegalArgumentException("Flank rules out of range");
            }
        }
    }

    /**
     * Where a blow from {@code (fromX, fromZ)} comes, for a squad at {@code (atX, atZ)} that faces along
     * {@code (faceX, faceZ)}: the front within 60 degrees of its facing, the rear beyond 120, a side between.
     */
    public static Flank of(double atX, double atZ, double faceX, double faceZ, double fromX, double fromZ) {
        double toX = fromX - atX;
        double toZ = fromZ - atZ;
        double face = Math.hypot(faceX, faceZ);
        double to = Math.hypot(toX, toZ);
        if (face < 1e-6 || to < 1e-6) {
            return FRONT;
        }
        double cos = (faceX * toX + faceZ * toZ) / (face * to);
        if (cos >= 0.5) {
            return FRONT;
        }
        return cos <= -0.5 ? REAR : SIDE;
    }

    /**
     * The strength of a blow from here, by the strength of a blow at the front.
     */
    public double edge(Rules rules) {
        return switch (this) {
            case FRONT -> 1.0;
            case SIDE -> 1.0 + rules.side();
            case REAR -> 1.0 + rules.rear();
        };
    }
}
