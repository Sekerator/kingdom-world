package io.github.sekerator.kingdomworld.core.balance;

import io.github.sekerator.kingdomworld.core.economy.EconomyRules;
import io.github.sekerator.kingdomworld.core.nobility.NobilityRules;
import io.github.sekerator.kingdomworld.core.trade.TradeRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A simple model of the money of one kingdom over game days, without the world: the poll tax of its subjects, what its
 * merchants sell abroad, the wages of its citizens and soldiers, mercenaries, works paid from the treasury, the shares of its
 * nobles, the wages of its spies and a tribute to a winner. It checks that the rules of the config give treasuries that neither
 * grow without end nor go bankrupt without a reason; the numbers of the world (yields, prices of players) only add to it.
 */
public final class KingdomBudget {
    private KingdomBudget() {
    }

    /**
     * A kind of kingdom.
     *
     * @param workers       citizens by profession (merchants and soldiers among them)
     * @param subjects      players who pay the poll tax
     * @param itemWorth     the average worth of what the merchants sell, in copper
     * @param mercenaries   what the bands of mercenaries cost a day
     * @param worksEvery    days between two works paid from the treasury, 0 for none
     * @param worksCost     what such works cost
     * @param nobleShare    the part of the income that goes to fiefs held by nobles, 0 to 1
     * @param spies         spies kept
     * @param tributeDays   days the kingdom pays a tribute from the first day, 0 for none
     */
    public record Profile(String name, Map<String, Integer> workers, int subjects, double itemWorth, long mercenaries, int worksEvery,
                          long worksCost, double nobleShare, int spies, int tributeDays, long funds) {
        public Profile {
            Objects.requireNonNull(name, "name");
            workers = Map.copyOf(workers);
        }

        int count(String profession) {
            return workers.getOrDefault(profession, 0);
        }
    }

    /**
     * The rules the model reads.
     *
     * @param soldierWage the wage of warriors and archers (the army adds it to the wages of the economy)
     * @param merchantWage the wage of merchants
     * @param tributePermille the share of the income a tribute takes, in permille
     */
    public record Rules(EconomyRules economy, TradeRules trade, NobilityRules nobility, long soldierWage, long merchantWage, int tributePermille) {
    }

    /**
     * One day of the treasury.
     */
    public record Day(int day, long income, long expense, long funds) {
    }

    /**
     * What a profile comes to after some days: the days, and the least and last funds.
     */
    public record Result(Profile profile, List<Day> days, long lowest, long last, long dailyIncome, long dailyExpense) {
        public boolean bankrupt() {
            return lowest < 0;
        }
    }

    public static Result run(Profile profile, Rules rules, int days) {
        long funds = profile.funds();
        long lowest = funds;
        long incomeSum = 0;
        long expenseSum = 0;
        List<Day> list = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            long taxes = profile.subjects() * rules.economy().defaultPollTax();
            long exports = Math.min(rules.trade().exportMaxIncomePerDay(),
                    Math.round(profile.count("merchant") * rules.trade().exportPerMerchant() * profile.itemWorth() * rules.trade().exportFactor()));
            long income = taxes + exports;
            long wages = 0;
            for (Map.Entry<String, Integer> entry : profile.workers().entrySet()) {
                String profession = entry.getKey();
                long wage = switch (profession) {
                    case "warrior", "archer" -> rules.soldierWage();
                    case "merchant" -> rules.merchantWage();
                    default -> rules.economy().defaultWage(profession);
                };
                wages += wage * entry.getValue();
            }
            long works = profile.worksEvery() > 0 && day % profile.worksEvery() == 0 ? profile.worksCost() : 0;
            long shares = Math.round(income * profile.nobleShare() * rules.nobility().shareBaron() / 100.0);
            long spies = profile.spies() * rules.nobility().spyWage();
            long tribute = day <= profile.tributeDays() ? income * rules.tributePermille() / 1_000 : 0;
            long expense = wages + profile.mercenaries() + works + shares + spies + tribute;
            funds += income - expense;
            lowest = Math.min(lowest, funds);
            incomeSum += income;
            expenseSum += expense;
            list.add(new Day(day, income, expense, funds));
        }
        return new Result(profile, list, lowest, funds, incomeSum / Math.max(1, days), expenseSum / Math.max(1, days));
    }

    /**
     * The usual kinds: a small and a large kingdom of players, and rulers of the world of three characters.
     */
    public static List<Profile> typical() {
        return List.of(
                new Profile("small kingdom of players", Map.of("farmer", 2, "lumberjack", 1, "miner", 1, "merchant", 1, "builder", 1), 1, 3.0,
                        0, 0, 0, 0.0, 0, 0, 200),
                new Profile("large kingdom of players", Map.of("farmer", 6, "lumberjack", 3, "miner", 4, "fisher", 2, "smelter", 2, "smith", 1,
                        "builder", 2, "merchant", 3, "warrior", 4, "archer", 2), 4, 3.0, 0, 7, 300, 0.3, 2, 0, 2_000),
                new Profile("merchant of the world", Map.of("farmer", 4, "lumberjack", 2, "miner", 2, "merchant", 4, "builder", 1, "warrior", 2), 0, 3.0,
                        0, 10, 200, 0.4, 1, 0, 1_500),
                new Profile("warrior of the world at war", Map.of("farmer", 4, "lumberjack", 2, "miner", 3, "merchant", 3, "smith", 1, "warrior", 6,
                        "archer", 2), 0, 3.0, 271, 0, 0, 0.4, 2, 5, 3_000),
                new Profile("builder of the world", Map.of("farmer", 4, "lumberjack", 3, "miner", 3, "merchant", 2, "builder", 3, "warrior", 2), 0, 3.0,
                        0, 5, 200, 0.4, 0, 0, 1_500));
    }
}
