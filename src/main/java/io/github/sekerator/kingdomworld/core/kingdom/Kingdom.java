package io.github.sekerator.kingdomworld.core.kingdom;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A kingdom as known to the domain core. Immutable; changes produce a new instance.
 *
 * @param id              stable identifier, never reused for another kingdom
 * @param name            display name in normalized form, see {@link KingdomNames}
 * @param color           map color as {@code 0xRRGGBB}
 * @param rulerId         the current ruler, or {@code null} while the throne is empty
 * @param createdGameTime game time in ticks at which the kingdom was founded
 * @param style           the architectural style its new buildings follow, or empty while none was chosen
 */
public record Kingdom(UUID id, String name, int color, UUID rulerId, long createdGameTime, String style) {
    public static final int MAX_COLOR = 0xFFFFFF;

    public Kingdom {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        style = style == null ? "" : style;
        if (!name.equals(KingdomNames.normalize(name))) {
            throw new IllegalArgumentException("Kingdom name is not normalized: '" + name + "'");
        }
        Optional<NameProblem> problem = KingdomNames.validate(name);
        if (problem.isPresent()) {
            throw new IllegalArgumentException("Invalid kingdom name '" + name + "': " + problem.get());
        }
        if (color < 0 || color > MAX_COLOR) {
            throw new IllegalArgumentException("Kingdom color out of range: " + color);
        }
        if (createdGameTime < 0) {
            throw new IllegalArgumentException("Negative creation time: " + createdGameTime);
        }
    }

    /**
     * A kingdom without a chosen style.
     */
    public Kingdom(UUID id, String name, int color, UUID rulerId, long createdGameTime) {
        this(id, name, color, rulerId, createdGameTime, "");
    }

    public Optional<UUID> ruler() {
        return Optional.ofNullable(rulerId);
    }

    public Kingdom withRuler(UUID newRulerId) {
        return new Kingdom(id, name, color, newRulerId, createdGameTime, style);
    }

    public Kingdom withColor(int newColor) {
        return new Kingdom(id, name, newColor, rulerId, createdGameTime, style);
    }

    public Kingdom withStyle(String newStyle) {
        return new Kingdom(id, name, color, rulerId, createdGameTime, newStyle);
    }
}
