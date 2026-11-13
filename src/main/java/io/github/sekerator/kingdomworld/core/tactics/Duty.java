package io.github.sekerator.kingdomworld.core.tactics;

import java.util.Objects;
import java.util.UUID;

/**
 * The part of one fighter in the plan of its squad: its role, its target and the place to stand.
 *
 * @param target the enemy to strike or shoot, or {@code null} (falling back, healing, lying in wait)
 * @param stand  where to stand, or {@code null} to keep to the target (striking in the ring once there)
 */
public record Duty(UUID fighter, Role role, UUID target, Spot stand) {
    public Duty {
        Objects.requireNonNull(fighter, "fighter");
        Objects.requireNonNull(role, "role");
    }
}
