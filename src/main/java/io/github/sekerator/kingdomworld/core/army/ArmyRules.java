package io.github.sekerator.kingdomworld.core.army;

import io.github.sekerator.kingdomworld.core.combat.BattleRules;

/**
 * Settings of the army on a server.
 *
 * @param maxSoldiers          soldiers one kingdom keeps at most
 * @param maxSquads            squads one kingdom keeps at most
 * @param maxSquadSize         soldiers in one squad at most
 * @param trainingPerMinute    experience a soldier gains per minute of training at a barracks
 * @param trainingCap          experience training takes a soldier to at most; battles go beyond
 * @param barracksCapacity     soldiers one barracks trains at a time
 * @param battle               how dangerous battles are
 * @param battleExperience     experience a soldier gains for a battle, more for a won one
 * @param offersPerDay         bands of mercenaries offered to a kingdom each day
 * @param minBand              fewest mercenaries in a band
 * @param maxBand              most mercenaries in a band
 * @param pricePerPower        daily price of mercenaries per point of their strength
 * @param advanceDays          days of pay a kingdom pays in advance when it hires a band
 * @param maxMercenaries       mercenaries one kingdom keeps at most
 * @param skirmishChance       chance per step of the simulation that monsters fall upon a squad that holds, guards or
 *                             patrols at night far from players
 * @param monsterStrength      strength of one monster of such a skirmish
 * @param caravanThreat        strength of what lies in wait for a caravan on the road
 * @param caravanThreatUnits   head count of it
 * @param alarmRadius          blocks around an attacked citizen or player in which soldiers come to help
 */
public record ArmyRules(int maxSoldiers, int maxSquads, int maxSquadSize, int trainingPerMinute, long trainingCap, int barracksCapacity,
                        BattleRules battle, int battleExperience, int offersPerDay, int minBand, int maxBand, double pricePerPower,
                        int advanceDays, int maxMercenaries, double skirmishChance, double monsterStrength, double caravanThreat,
                        int caravanThreatUnits, int alarmRadius) {
    public static final ArmyRules DEFAULT = new Builder().build();
    /** What a kingdom of the world may keep at least, whatever the limits of players say: its garrison is larger. */
    public static final int WORLD_SOLDIERS = 160;
    public static final int WORLD_SQUADS = 48;
    public static final int WORLD_SQUAD_SIZE = ArmySquad.MAX_MEMBERS;

    /**
     * The same rules with limits of soldiers, squads and the size of a squad no lower than given.
     */
    public ArmyRules withLimits(int soldiers, int squads, int squadSize) {
        return new ArmyRules(Math.max(maxSoldiers, soldiers), Math.max(maxSquads, squads),
                Math.max(maxSquadSize, Math.min(ArmySquad.MAX_MEMBERS, squadSize)), trainingPerMinute, trainingCap, barracksCapacity, battle,
                battleExperience, offersPerDay, minBand, maxBand, pricePerPower, advanceDays, maxMercenaries, skirmishChance, monsterStrength,
                caravanThreat, caravanThreatUnits, alarmRadius);
    }

    public ArmyRules {
        if (maxSoldiers < 0 || maxSquads < 0 || maxSquadSize < 1 || maxSquadSize > ArmySquad.MAX_MEMBERS || trainingPerMinute < 0
                || trainingCap < 0 || barracksCapacity < 0 || battleExperience < 0 || offersPerDay < 0 || minBand < 1 || maxBand < minBand
                || maxBand > MercenaryBand.MAX_FIGHTERS || !(pricePerPower > 0) || !Double.isFinite(pricePerPower) || advanceDays < 1
                || maxMercenaries < 0 || !(skirmishChance >= 0 && skirmishChance <= 1) || !(monsterStrength > 0)
                || !Double.isFinite(monsterStrength) || !(caravanThreat >= 0) || !Double.isFinite(caravanThreat) || caravanThreatUnits < 1
                || alarmRadius < 0) {
            throw new IllegalArgumentException("Army rules out of range");
        }
        if (battle == null) {
            throw new IllegalArgumentException("No battle rules");
        }
    }

    /**
     * The daily price of a band of this strength.
     */
    public long price(double power) {
        return Math.max(1L, (long) Math.ceil(Math.max(0.0, power) * pricePerPower));
    }

    /**
     * Rules built step by step, from the defaults.
     */
    public static final class Builder {
        public int maxSoldiers = 64;
        public int maxSquads = 16;
        public int maxSquadSize = 12;
        public int trainingPerMinute = 2;
        public long trainingCap = SoldierRank.SERGEANT.experience();
        public int barracksCapacity = 8;
        public double battleInjury = 0.15;
        public double battleDeath = 0.03;
        public double riskMultiplier = 1.0;
        public int battleExperience = 20;
        public int offersPerDay = 3;
        public int minBand = 2;
        public int maxBand = 5;
        public double pricePerPower = 0.4;
        public int advanceDays = 2;
        public int maxMercenaries = 20;
        public double skirmishChance = 0.05;
        public double monsterStrength = 25.0;
        public double caravanThreat = 120.0;
        public int caravanThreatUnits = 5;
        public int alarmRadius = 32;

        public ArmyRules build() {
            return new ArmyRules(maxSoldiers, maxSquads, maxSquadSize, trainingPerMinute, trainingCap, barracksCapacity,
                    new BattleRules(battleInjury, battleDeath, riskMultiplier), battleExperience, offersPerDay, minBand, maxBand, pricePerPower,
                    advanceDays, maxMercenaries, skirmishChance, monsterStrength, caravanThreat, caravanThreatUnits, alarmRadius);
        }
    }
}
