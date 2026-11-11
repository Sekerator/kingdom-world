package io.github.sekerator.kingdomworld.core.army;

import java.util.Objects;
import java.util.UUID;

/**
 * A citizen in the army, as the army knows it: the branch it serves in, its post there, experience of war, kills and
 * battles. The rank follows the experience. The citizen record stays the authority on life, health and gear; a soldier is
 * a citizen with the profession of a warrior or an archer.
 *
 * @param experience experience of war, from training and battles
 * @param recruited  game time of recruitment
 * @param branch     the city guard or the royal army
 * @param post       fighter, vice-captain or captain of the branch
 */
public record Soldier(UUID citizenId, UUID kingdomId, long experience, int kills, int battles, long recruited, Branch branch, Post post) {
    /** Most experience a soldier gathers. */
    public static final long MAX_EXPERIENCE = 1_000_000_000L;

    public Soldier {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(post, "post");
        if (experience < 0 || experience > MAX_EXPERIENCE || kills < 0 || battles < 0) {
            throw new IllegalArgumentException("Soldier out of range: " + citizenId);
        }
    }

    /**
     * A fighter of the guard, as every soldier is until somebody decides otherwise.
     */
    public Soldier(UUID citizenId, UUID kingdomId, long experience, int kills, int battles, long recruited) {
        this(citizenId, kingdomId, experience, kills, battles, recruited, Branch.GUARD, Post.FIGHTER);
    }

    public static Soldier recruit(UUID citizenId, UUID kingdomId, long now) {
        return new Soldier(citizenId, kingdomId, 0, 0, 0, now);
    }

    public SoldierRank rank() {
        return SoldierRank.of(experience);
    }

    public Soldier withBranch(Branch value) {
        return new Soldier(citizenId, kingdomId, experience, kills, battles, recruited, value, post);
    }

    public Soldier withPost(Post value) {
        return new Soldier(citizenId, kingdomId, experience, kills, battles, recruited, branch, value);
    }

    /**
     * Experience, kills and battles added.
     */
    public Soldier earned(long moreExperience, int moreKills, int moreBattles) {
        long next = Math.max(0, Math.min(MAX_EXPERIENCE, experience + Math.max(0, moreExperience)));
        return new Soldier(citizenId, kingdomId, next, (int) Math.min(Integer.MAX_VALUE, (long) kills + Math.max(0, moreKills)),
                (int) Math.min(Integer.MAX_VALUE, (long) battles + Math.max(0, moreBattles)), recruited, branch, post);
    }

    /**
     * Experience from training, which takes a soldier only up to a cap.
     */
    public Soldier trained(long amount, long cap) {
        if (amount <= 0 || experience >= cap) {
            return this;
        }
        return new Soldier(citizenId, kingdomId, Math.min(cap, experience + amount), kills, battles, recruited, branch, post);
    }
}
