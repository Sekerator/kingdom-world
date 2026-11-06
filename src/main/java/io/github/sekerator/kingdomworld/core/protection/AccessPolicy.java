package io.github.sekerator.kingdomworld.core.protection;

import io.github.sekerator.kingdomworld.core.membership.MembershipRegistry;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decides whether an actor may do something in a chunk. All protection checks of the mod go through
 * {@link #isAllowed}; later mechanics (for example war, which lets enemies break blocks) add an
 * {@link Exemption} instead of new checks.
 *
 * <p>Order of the decision: unclaimed land is free; interactions are free when they are not protected;
 * operators with bypass and (if allowed) fake players may act; members act according to the rights of their
 * position; finally any exemption may allow the action. Everything else is refused.
 */
public final class AccessPolicy {

    /**
     * Allows actions that the normal rules refuse.
     */
    @FunctionalInterface
    public interface Exemption {
        boolean allows(AccessSubject subject, UUID ownerKingdom, ProtectedAction action, String dimension, long chunk);
    }

    /**
     * @param protectInteractions whether {@link ProtectedAction#INTERACT} is protected at all
     * @param allowFakePlayers    whether fake players of other mods may act everywhere
     */
    public record Settings(boolean protectInteractions, boolean allowFakePlayers) {
        public static final Settings DEFAULT = new Settings(true, true);
    }

    private final List<Exemption> exemptions = new CopyOnWriteArrayList<>();

    public void addExemption(Exemption exemption) {
        exemptions.add(Objects.requireNonNull(exemption, "exemption"));
    }

    public void removeExemption(Exemption exemption) {
        exemptions.remove(exemption);
    }

    /**
     * @param ownerKingdom owner of the chunk, or {@code null} for unclaimed land
     */
    public boolean isAllowed(MembershipRegistry membership, UUID ownerKingdom, AccessSubject subject,
                             ProtectedAction action, String dimension, long chunk, Settings settings) {
        if (ownerKingdom == null) {
            return true;
        }
        if (action == ProtectedAction.INTERACT && !settings.protectInteractions()) {
            return true;
        }
        switch (subject.kind()) {
            case BYPASS -> {
                return true;
            }
            case FAKE_PLAYER -> {
                if (settings.allowFakePlayers()) {
                    return true;
                }
            }
            case PLAYER -> {
                if (membership.hasPermission(subject.playerId(), ownerKingdom, action.permission())) {
                    return true;
                }
            }
            case OTHER -> {
            }
        }
        for (Exemption exemption : exemptions) {
            if (exemption.allows(subject, ownerKingdom, action, dimension, chunk)) {
                return true;
            }
        }
        return false;
    }
}
