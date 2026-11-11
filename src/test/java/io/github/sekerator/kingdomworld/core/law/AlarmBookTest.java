package io.github.sekerator.kingdomworld.core.law;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlarmBookTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private final LawRules rules = new LawRules.Builder().build();
    private final UUID kingdom = UUID.randomUUID();
    private final UUID offender = UUID.randomUUID();

    @Test
    void aBlowRaisesTheAlarmAtOnceWithTheOffenderAndThePlace() {
        AlarmBook book = new AlarmBook();
        Alarm alarm = book.raise(kingdom, offender, "Robber", OVERWORLD, 10, 64, -5, 1_000, Alarm.Cause.BLOW);
        assertEquals(offender, alarm.offender());
        assertEquals(10, alarm.x());
        assertEquals(64, alarm.y());
        assertEquals(-5, alarm.z());
        assertEquals(1_000, alarm.started());
        assertTrue(alarm.active(1_000, rules));
        assertEquals(List.of(alarm), book.in(kingdom));
        assertTrue(book.in(UUID.randomUUID()).isEmpty(), "Another kingdom hears nothing");
    }

    @Test
    void theAlarmLastsFromTheLastCauseAndThenRunsOut() {
        AlarmBook book = new AlarmBook();
        book.raise(kingdom, offender, "Robber", OVERWORLD, 0, 64, 0, 0, Alarm.Cause.BLOW);
        Alarm alarm = book.raise(kingdom, offender, "Robber", OVERWORLD, 30, 64, 0, 600, Alarm.Cause.BLOW);
        assertEquals(0, alarm.started(), "A new blow feeds the alarm that lasts");
        assertEquals(30, alarm.x(), "The place follows the offender");
        assertEquals(1, book.in(kingdom).size());
        assertTrue(alarm.active(600 + rules.alarmTicks, rules));
        assertFalse(alarm.active(601 + rules.alarmTicks, rules));
        assertTrue(book.expire(600 + rules.alarmTicks, rules).isEmpty());
        assertEquals(List.of(alarm), book.expire(601 + rules.alarmTicks, rules));
        assertTrue(book.isEmpty());
        assertEquals(601 + rules.alarmTicks, alarm.duration(601 + rules.alarmTicks));
    }

    @Test
    void soldiersStrikeAtOnceWhileBlowsFallAndAlwaysAfterAKilling() {
        AlarmBook book = new AlarmBook();
        Alarm blows = book.raise(kingdom, offender, "Robber", OVERWORLD, 0, 64, 0, 100, Alarm.Cause.BLOW);
        assertTrue(blows.violent(100 + rules.ongoingTicks, rules));
        assertFalse(blows.violent(101 + rules.ongoingTicks, rules), "The blows stopped: the guards call out first");
        assertFalse(blows.killing());

        UUID murderer = UUID.randomUUID();
        Alarm killing = book.raise(kingdom, murderer, "Murderer", OVERWORLD, 0, 64, 0, 100, Alarm.Cause.KILLING);
        assertTrue(killing.killing());
        assertTrue(killing.violent(100 + rules.alarmTicks, rules), "After a killing nobody calls out while the alarm lasts");

        UUID thief = UUID.randomUUID();
        Alarm theft = book.raise(kingdom, thief, "Thief", OVERWORLD, 0, 64, 0, 100, Alarm.Cause.THEFT);
        assertFalse(theft.violent(100, rules), "A theft is a petty matter: the guards call out");
        assertTrue(theft.active(100, rules));
    }

    @Test
    void whatNobodyReportedIsHandedOverOnceWhenASoldierSeesTheOffender() {
        AlarmBook book = new AlarmBook();
        book.raise(kingdom, offender, "Robber", OVERWORLD, 0, 64, 0, 0, Alarm.Cause.KILLING);
        book.unseen(kingdom, offender, new Alarm.Deed(CrimeKind.MURDER, "@citizen:5:m", 0, "victim-1"));
        book.unseen(kingdom, offender, new Alarm.Deed(CrimeKind.ASSAULT, "@citizen:6:f", 0, "victim-2"));
        book.unseen(kingdom, offender, new Alarm.Deed(CrimeKind.ASSAULT, "@citizen:6:f", 0, "victim-2"));
        Alarm alarm = book.of(kingdom, offender).orElseThrow();
        assertEquals(2, alarm.unseen().size(), "Blows at one victim are one deed");
        assertFalse(alarm.seen());
        List<Alarm.Deed> deeds = book.seen(kingdom, offender);
        assertEquals(2, deeds.size());
        assertEquals(CrimeKind.MURDER, deeds.get(0).kind());
        assertTrue(alarm.seen());
        assertTrue(book.seen(kingdom, offender).isEmpty(), "A deed is written into the record once");
        assertTrue(book.seen(kingdom, UUID.randomUUID()).isEmpty());
    }

    @Test
    void theAlarmCountsTheSoldiersTheOffenderKilledAndWounded() {
        AlarmBook book = new AlarmBook();
        Alarm alarm = book.raise(kingdom, offender, "Robber", OVERWORLD, 0, 64, 0, 0, Alarm.Cause.BLOW);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        book.guardHurt(kingdom, offender, first, false);
        book.guardHurt(kingdom, offender, first, false);
        book.guardHurt(kingdom, offender, second, false);
        assertEquals(2, alarm.guardsWounded());
        assertEquals(0, alarm.guardsKilled());
        book.guardHurt(kingdom, offender, first, true);
        assertEquals(1, alarm.guardsWounded(), "A killed soldier is no longer counted as wounded");
        assertEquals(1, alarm.guardsKilled());
        book.guardHurt(kingdom, offender, first, false);
        assertEquals(1, alarm.guardsWounded());
    }

    @Test
    void anEndedAlarmIsGoneAndAKingdomKeepsOnlySoMany() {
        AlarmBook book = new AlarmBook();
        book.raise(kingdom, offender, "Robber", OVERWORLD, 0, 64, 0, 0, Alarm.Cause.BLOW);
        assertTrue(book.end(kingdom, offender).isPresent());
        assertTrue(book.of(kingdom, offender).isEmpty());
        assertTrue(book.end(kingdom, offender).isEmpty());
        UUID oldest = UUID.randomUUID();
        book.raise(kingdom, oldest, "Oldest", OVERWORLD, 0, 64, 0, 1, Alarm.Cause.BLOW);
        for (int i = 0; i < AlarmBook.MAX_PER_KINGDOM; i++) {
            book.raise(kingdom, UUID.randomUUID(), "Other " + i, OVERWORLD, 0, 64, 0, 10 + i, Alarm.Cause.BLOW);
        }
        assertEquals(AlarmBook.MAX_PER_KINGDOM, book.in(kingdom).size());
        assertTrue(book.of(kingdom, oldest).isEmpty(), "The alarm fed longest ago gives way");
        book.forgetKingdom(kingdom);
        assertTrue(book.isEmpty());
    }
}
