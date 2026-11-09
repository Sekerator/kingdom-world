package io.github.sekerator.kingdomworld.core.npc;

import java.util.Locale;
import java.util.Optional;

/**
 * How the buildings of the kingdoms of the world are raised. The kingdoms themselves stand as data from the start (land, ruler,
 * treasury, laws); their blocks need loaded chunks and are put in either in the background, one kingdom at a time with a small
 * budget, or only when a player comes near.
 *
 * @param mode                    when the buildings are raised
 * @param backgroundBlocksPerTick blocks a background raising places, clears or levels in a tick at most
 * @param backgroundChunksPerTick chunks a background raising asks for or looks through in a tick at most
 * @param pauseTickMillis         a background raising waits while the average tick of the server is longer than this
 * @param claimsPerTick           chunks of land a kingdom founded as data claims in a tick at most
 */
public record NpcBuildRules(Mode mode, int backgroundBlocksPerTick, int backgroundChunksPerTick, int pauseTickMillis, int claimsPerTick) {
    public static final NpcBuildRules DEFAULT = new NpcBuildRules(Mode.BACKGROUND, 300, 1, 35, 16);

    /**
     * When the buildings of a kingdom of the world are raised.
     */
    public enum Mode {
        /** One kingdom after another while players are on the server and it keeps up; a kingdom a player comes near goes first. */
        BACKGROUND,
        /** Only when a player comes near, as before: the kingdom already stands on the map and lives until then. */
        APPROACH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Mode> byId(String id) {
            for (Mode mode : values()) {
                if (mode.id().equalsIgnoreCase(id == null ? "" : id.trim())) {
                    return Optional.of(mode);
                }
            }
            return Optional.empty();
        }
    }

    public NpcBuildRules {
        if (mode == null) {
            throw new IllegalArgumentException("A mode is needed");
        }
        if (backgroundBlocksPerTick < 16 || backgroundBlocksPerTick > 100_000 || backgroundChunksPerTick < 1 || backgroundChunksPerTick > 64
                || pauseTickMillis < 1 || pauseTickMillis > 1000 || claimsPerTick < 1 || claimsPerTick > 4096) {
            throw new IllegalArgumentException("Rules of raising the kingdoms of the world out of range");
        }
    }

    public boolean background() {
        return mode == Mode.BACKGROUND;
    }

    public NpcBuildRules withMode(Mode value) {
        return new NpcBuildRules(value, backgroundBlocksPerTick, backgroundChunksPerTick, pauseTickMillis, claimsPerTick);
    }
}
