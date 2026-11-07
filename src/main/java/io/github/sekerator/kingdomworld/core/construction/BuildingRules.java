package io.github.sekerator.kingdomworld.core.construction;

/**
 * The settings of the buildings of the register.
 *
 * @param roleThreshold   share of a building, in percent, that must stand for its role to work
 * @param autoRepairBelow share below which a building that repairs itself orders a repair
 * @param demolitionLoss  share of the materials of a demolished building that is lost
 * @param maxBlueprints   blueprints of its own a kingdom may keep
 */
public record BuildingRules(int roleThreshold, int autoRepairBelow, int demolitionLoss, int maxBlueprints) {
    public static final BuildingRules DEFAULT = new BuildingRules(70, 80, 25, 16);

    public BuildingRules {
        if (roleThreshold < 0 || roleThreshold > 100 || autoRepairBelow < 1 || autoRepairBelow > 100 || demolitionLoss < 0
                || demolitionLoss > 100 || maxBlueprints < 0) {
            throw new IllegalArgumentException("Building rules out of range");
        }
    }
}
