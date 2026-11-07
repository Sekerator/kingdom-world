package io.github.sekerator.kingdomworld.core.law;

import io.github.sekerator.kingdomworld.core.membership.Role;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decides whether a deed on the land of a kingdom is a crime there. Never for the king and the officials of the kingdom
 * (the nobles of a later stage join them through {@link Nobility}), for the owner of the thing, for whoever holds the right
 * for the deed, or for an enemy in war (wars add an {@link Exemption}). Subjects of the kingdom answer for their deeds like
 * anybody else.
 */
public final class LawPolicy {

    /**
     * Lets deeds go that the law would punish, for example those of an enemy in war.
     */
    @FunctionalInterface
    public interface Exemption {
        boolean excuses(UUID kingdomId, UUID player, CrimeKind kind);
    }

    /**
     * Who is a noble of a kingdom; nobles answer to the king alone. Nobody until the stage of nobility.
     */
    @FunctionalInterface
    public interface Nobility {
        Nobility NONE = (kingdomId, player) -> false;

        boolean isNoble(UUID kingdomId, UUID player);
    }

    /**
     * Who did the deed, as the law of the kingdom sees them.
     *
     * @param role     the position in this kingdom, empty for an outsider
     * @param hasRight whether the position holds the right for the deed (containers, building)
     * @param owner    whether the thing belongs to the player
     */
    public record Offender(UUID player, Optional<Role> role, boolean hasRight, boolean owner) {
        public Offender {
            Objects.requireNonNull(player, "player");
            role = role == null ? Optional.empty() : role;
        }

        public static Offender outsider(UUID player) {
            return new Offender(player, Optional.empty(), false, false);
        }
    }

    private final List<Exemption> exemptions = new CopyOnWriteArrayList<>();
    private volatile Nobility nobility = Nobility.NONE;

    public void addExemption(Exemption exemption) {
        exemptions.add(Objects.requireNonNull(exemption, "exemption"));
    }

    public void removeExemption(Exemption exemption) {
        exemptions.remove(exemption);
    }

    public void setNobility(Nobility value) {
        nobility = value == null ? Nobility.NONE : value;
    }

    /**
     * Whether the deed is a crime in the kingdom.
     */
    public boolean isCrime(UUID kingdomId, Offender offender, CrimeKind kind) {
        if (offender.role().isPresent() && offender.role().get() != Role.SUBJECT) {
            return false;
        }
        if (nobility.isNoble(kingdomId, offender.player())) {
            return false;
        }
        if (offender.owner()) {
            return false;
        }
        if (offender.hasRight() && kind == CrimeKind.THEFT) {
            return false;
        }
        for (Exemption exemption : exemptions) {
            if (exemption.excuses(kingdomId, offender.player(), kind)) {
                return false;
            }
        }
        return true;
    }
}
