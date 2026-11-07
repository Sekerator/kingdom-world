package io.github.sekerator.kingdomworld.core.economy;

/**
 * The daily poll tax of the subjects. What a subject cannot pay becomes a debt to the treasury, which is collected
 * together with the next taxes.
 */
public final class Taxes {

    /**
     * The result of collecting from one subject.
     *
     * @param paid what was taken from the subject's account
     * @param debt what the subject still owes afterwards
     */
    public record Collection(long paid, long debt) {
    }

    private Taxes() {
    }

    /**
     * Collects the tax of a day and the old debt from an account.
     *
     * @param balance what the subject has in the kingdom's currency
     * @param debt    what the subject owed before
     * @param tax     the tax of the day
     */
    public static Collection collect(long balance, long debt, long tax) {
        long owed = Money.addCapped(debt, tax);
        long paid = Math.min(Math.max(0, balance), owed);
        return new Collection(paid, owed - paid);
    }
}
