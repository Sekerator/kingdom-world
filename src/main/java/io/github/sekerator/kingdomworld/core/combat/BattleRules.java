package io.github.sekerator.kingdomworld.core.combat;

/**
 * How dangerous battles are.
 *
 * @param injury         chance of a fighter to be wounded in an even fight that its side won
 * @param death          chance of a fighter to fall in such a fight
 * @param riskMultiplier the server's {@code taskRiskMultiplier}: 0 makes battles harmless
 */
public record BattleRules(double injury, double death, double riskMultiplier) {
    public static final BattleRules DEFAULT = new BattleRules(0.15, 0.03, 1.0);

    public BattleRules {
        if (!(injury >= 0.0 && injury <= 1.0) || !(death >= 0.0 && death <= 1.0) || !(riskMultiplier >= 0.0)
                || !Double.isFinite(riskMultiplier)) {
            throw new IllegalArgumentException("Battle rules out of range");
        }
    }
}
