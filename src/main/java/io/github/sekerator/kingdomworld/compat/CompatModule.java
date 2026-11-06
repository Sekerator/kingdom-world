package io.github.sekerator.kingdomworld.compat;

/**
 * Optional integration with another mod.
 *
 * <p>Each module lives in its own subpackage of {@code compat} and is the only place allowed to reference
 * classes of the target mod. {@link CompatModules} instantiates a module only after confirming that the
 * target mod is loaded, so a missing mod never causes class loading errors.
 */
public interface CompatModule {

    /**
     * Called once during common setup, on the main thread.
     */
    void init();
}
