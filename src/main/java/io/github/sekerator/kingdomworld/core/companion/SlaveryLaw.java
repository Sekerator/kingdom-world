package io.github.sekerator.kingdomworld.core.companion;

import java.util.UUID;

/**
 * Whether the law of a kingdom allows slaves. Every kingdom allows them for now (a king can only close the market on the
 * land of the kingdom); the stage of laws and guards replaces this to forbid slavery, and the server then sets the slaves of
 * the subjects of such a kingdom free.
 */
@FunctionalInterface
public interface SlaveryLaw {
    SlaveryLaw ALLOWED = kingdomId -> true;

    boolean allowsSlavery(UUID kingdomId);
}
