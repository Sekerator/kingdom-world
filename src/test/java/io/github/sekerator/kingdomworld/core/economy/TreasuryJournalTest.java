package io.github.sekerator.kingdomworld.core.economy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryJournalTest {

    @Test
    void periodsSumTheirDays() {
        TreasuryJournal journal = new TreasuryJournal();
        journal.record(10, LedgerCategory.TAXES, 30, 0);
        journal.record(10, LedgerCategory.TAXES, 20, 0);
        journal.record(10, LedgerCategory.WAGES, 0, 40);
        journal.record(9, LedgerCategory.MINTING, 500, 0);
        journal.record(5, LedgerCategory.ADMIN, 7, 3);
        journal.record(3, LedgerCategory.ADMIN, 1_000, 0);

        Map<LedgerCategory, TreasuryJournal.Totals> today = journal.totals(10, TreasuryJournal.Period.TODAY);
        assertEquals(new TreasuryJournal.Totals(50, 0), today.get(LedgerCategory.TAXES));
        assertEquals(new TreasuryJournal.Totals(0, 40), today.get(LedgerCategory.WAGES));
        assertFalse(today.containsKey(LedgerCategory.MINTING));
        assertEquals(new TreasuryJournal.Totals(500, 0), journal.totals(10, TreasuryJournal.Period.YESTERDAY).get(LedgerCategory.MINTING));
        // Seven days: 4 to 10; day 3 is outside.
        assertEquals(new TreasuryJournal.Totals(7, 3), journal.totals(10, TreasuryJournal.Period.WEEK).get(LedgerCategory.ADMIN));
        assertEquals(new TreasuryJournal.Totals(557, 43), journal.sum(10, TreasuryJournal.Period.WEEK));
    }

    @Test
    void oldDaysAreForgotten() {
        TreasuryJournal journal = new TreasuryJournal();
        journal.record(1, LedgerCategory.TAXES, 5, 0);
        journal.record(8, LedgerCategory.TAXES, 5, 0);
        assertTrue(journal.days().containsKey(1L), "eight days are kept");
        journal.record(9, LedgerCategory.TAXES, 5, 0);
        assertFalse(journal.days().containsKey(1L));
        assertEquals(2, journal.days().size());
        journal.record(9, LedgerCategory.TAXES, 0, 0);
        assertEquals(2, journal.days().size(), "nothing is written for nothing");
    }

    @Test
    void totalsStopAtTheLargestAmount() {
        TreasuryJournal journal = new TreasuryJournal();
        journal.record(1, LedgerCategory.ADMIN, Money.MAX, 0);
        journal.record(1, LedgerCategory.ADMIN, Money.MAX, 0);
        assertEquals(Money.MAX, journal.sum(1, TreasuryJournal.Period.TODAY).income());
    }
}
