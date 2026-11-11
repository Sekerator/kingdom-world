package io.github.sekerator.kingdomworld.core.law;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The alarms of every kingdom, one for each offender it chases. Alarms are short and are not saved: after a restart the
 * records of the wanted are all the guards know. Pure logic; the server raises alarms from blows and sends the soldiers.
 */
public final class AlarmBook {
    /** Most alarms one kingdom keeps at a time; a newer offender takes the place of the alarm fed longest ago. */
    public static final int MAX_PER_KINGDOM = 32;

    private final Map<UUID, Map<UUID, Alarm>> alarms = new LinkedHashMap<>();

    /**
     * Raises the alarm of a kingdom for an offender, or feeds the one that lasts: the place moves to where the offender is
     * now and the alarm lasts {@link LawRules#alarmTicks} from this moment again.
     */
    public Alarm raise(UUID kingdomId, UUID offender, String name, String dimension, int x, int y, int z, long now, Alarm.Cause cause) {
        Map<UUID, Alarm> of = alarms.computeIfAbsent(kingdomId, k -> new LinkedHashMap<>());
        Alarm alarm = of.get(offender);
        if (alarm == null) {
            if (of.size() >= MAX_PER_KINGDOM) {
                of.values().stream().min(java.util.Comparator.comparingLong(Alarm::lastCause)).ifPresent(oldest -> of.remove(oldest.offender()));
            }
            alarm = new Alarm(kingdomId, offender, name, dimension, x, y, z, now);
            of.put(offender, alarm);
        }
        alarm.feed(cause, name, dimension, x, y, z, now);
        return alarm;
    }

    public Optional<Alarm> of(UUID kingdomId, UUID offender) {
        Map<UUID, Alarm> of = alarms.get(kingdomId);
        return of == null ? Optional.empty() : Optional.ofNullable(of.get(offender));
    }

    /**
     * The alarms of a kingdom, the one raised first first.
     */
    public List<Alarm> in(UUID kingdomId) {
        Map<UUID, Alarm> of = alarms.get(kingdomId);
        return of == null ? List.of() : List.copyOf(of.values());
    }

    public List<Alarm> all() {
        List<Alarm> result = new ArrayList<>();
        alarms.values().forEach(of -> result.addAll(of.values()));
        return result;
    }

    public boolean isEmpty() {
        return alarms.isEmpty();
    }

    /**
     * The offender moved while the alarm lasts: the soldiers know where to.
     */
    public void moved(UUID kingdomId, UUID offender, String dimension, int x, int y, int z) {
        of(kingdomId, offender).ifPresent(alarm -> alarm.moveTo(dimension, x, y, z));
    }

    /**
     * A crime of the offender nobody has told the guards of: kept with the alarm until a soldier sees the offender.
     */
    public void unseen(UUID kingdomId, UUID offender, Alarm.Deed deed) {
        of(kingdomId, offender).ifPresent(alarm -> alarm.addUnseen(deed));
    }

    /**
     * A soldier sees the offender: the deeds kept with the alarm are handed over to be written into the record.
     */
    public List<Alarm.Deed> seen(UUID kingdomId, UUID offender) {
        Optional<Alarm> alarm = of(kingdomId, offender);
        if (alarm.isEmpty()) {
            return List.of();
        }
        alarm.get().markSeen();
        return alarm.get().takeUnseen();
    }

    /**
     * The offender wounded or killed a soldier of the kingdom while the alarm lasts.
     */
    public void guardHurt(UUID kingdomId, UUID offender, UUID soldier, boolean killed) {
        of(kingdomId, offender).ifPresent(alarm -> {
            if (killed) {
                alarm.guardKilled(soldier);
            } else {
                alarm.guardWounded(soldier);
            }
        });
    }

    /**
     * The alarm is over: the offender paid, died, was pardoned or left the land.
     */
    public Optional<Alarm> end(UUID kingdomId, UUID offender) {
        Map<UUID, Alarm> of = alarms.get(kingdomId);
        if (of == null) {
            return Optional.empty();
        }
        Alarm removed = of.remove(offender);
        if (of.isEmpty()) {
            alarms.remove(kingdomId);
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Ends the alarms nothing fed for {@link LawRules#alarmTicks}.
     *
     * @return the alarms that ended
     */
    public List<Alarm> expire(long now, LawRules rules) {
        List<Alarm> ended = new ArrayList<>();
        for (Map<UUID, Alarm> of : alarms.values()) {
            of.values().removeIf(alarm -> {
                if (alarm.active(now, rules)) {
                    return false;
                }
                ended.add(alarm);
                return true;
            });
        }
        alarms.values().removeIf(Map::isEmpty);
        return ended;
    }

    public void forgetKingdom(UUID kingdomId) {
        alarms.remove(kingdomId);
    }

    public void clear() {
        alarms.clear();
    }
}
