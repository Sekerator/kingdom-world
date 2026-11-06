package io.github.sekerator.kingdomworld.common.citizen;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.citizen.CauseOfDeath;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Texts about citizens. A name is a translated component that carries both spellings: English (and every
 * language without its own translation) shows the Latin one, Russian the Cyrillic one, so each player reads the
 * name in their own alphabet. Words that agree with the citizen (state, profession, cause of death, messages)
 * have a second key ending in {@value #FEMALE} for women.
 */
public final class CitizenTexts {
    private static final String PROFESSION = "kingdomworld.profession.";
    private static final String STATE = "kingdomworld.citizen.state.";
    private static final String DEATH = "kingdomworld.death.";
    /** Suffix of the keys for women. */
    public static final String FEMALE = ".female";

    private CitizenTexts() {
    }

    public static MutableComponent name(int seed, Gender gender) {
        return Component.translatable("kingdomworld.citizen.name",
                CitizenNames.render(seed, gender, CitizenNames.Alphabet.LATIN),
                CitizenNames.render(seed, gender, CitizenNames.Alphabet.CYRILLIC));
    }

    public static MutableComponent name(Citizen citizen) {
        return name(citizen.nameSeed(), citizen.gender());
    }

    /**
     * A key, or its form for women.
     */
    public static String gendered(String key, Gender gender) {
        return gender == Gender.FEMALE ? key + FEMALE : key;
    }

    public static Component profession(String id, Gender gender) {
        return Component.translatable(gendered(Professions.REGISTRY.isKnown(id) ? PROFESSION + id : PROFESSION + "unknown", gender));
    }

    /**
     * The name of a profession as a job, for filters and settings.
     */
    public static Component profession(String id) {
        return profession(id, Gender.MALE);
    }

    public static Component state(CitizenState state, Gender gender) {
        return Component.translatable(gendered(STATE + state.id(), gender));
    }

    public static Component cause(CauseOfDeath cause, String killer, Gender gender) {
        if (cause == CauseOfDeath.PLAYER && killer != null && !killer.isEmpty()) {
            return Component.translatable(gendered(DEATH + "player_named", gender), killer);
        }
        return Component.translatable(gendered(DEATH + cause.id(), gender));
    }

    public static Component place(WorldPos pos) {
        return Component.translatable("kingdomworld.citizen.place", pos.x(), pos.y(), pos.z());
    }

    /**
     * Tells a player who clicked a citizen who it is, above the hotbar.
     */
    static void describeTo(ServerPlayer player, CitizenEntity body) {
        Optional<Citizen> citizen = Optional.ofNullable(body.citizenId())
                .flatMap(id -> KingdomWorldApi.state(player.server).citizens().get(id));
        if (citizen.isEmpty()) {
            return;
        }
        Citizen record = citizen.get();
        player.displayClientMessage(Component.translatable("kingdomworld.citizen.describe", name(record),
                profession(record.professionId(), record.gender()), state(record.state(), record.gender())), true);
    }
}
