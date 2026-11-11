package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcLevel;
import io.github.sekerator.kingdomworld.core.npc.RulerCharacter;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What players see of a kingdom of the world on the overview of the throne and on the atlas: its names in both alphabets,
 * its ruler with a character and traits, its size and its towns, and its people and soldiers: those it is expected to have
 * while its capital is being built, the real ones once it stands.
 */
public record NpcKingdomInfo(String nameLatin, String nameCyrillic, String rulerLatin, String rulerCyrillic, Gender rulerGender,
                             RulerCharacter character, List<RulerCharacter.Trait> traits, NpcLevel level, int towns, boolean raising,
                             int people, int soldiers) {
    /** The most people or soldiers a kingdom of the world is shown with. */
    public static final int MAX_COUNT = 100_000;
    private static final int MAX_NAME = 64;

    public NpcKingdomInfo(String nameLatin, String nameCyrillic, String rulerLatin, String rulerCyrillic, Gender rulerGender,
                          RulerCharacter character, List<RulerCharacter.Trait> traits, NpcLevel level, int towns) {
        this(nameLatin, nameCyrillic, rulerLatin, rulerCyrillic, rulerGender, character, traits, level, towns, false);
    }

    public NpcKingdomInfo(String nameLatin, String nameCyrillic, String rulerLatin, String rulerCyrillic, Gender rulerGender,
                          RulerCharacter character, List<RulerCharacter.Trait> traits, NpcLevel level, int towns, boolean raising) {
        this(nameLatin, nameCyrillic, rulerLatin, rulerCyrillic, rulerGender, character, traits, level, towns, raising, 0, 0);
    }

    public NpcKingdomInfo {
        Objects.requireNonNull(nameLatin, "nameLatin");
        Objects.requireNonNull(nameCyrillic, "nameCyrillic");
        Objects.requireNonNull(rulerLatin, "rulerLatin");
        Objects.requireNonNull(rulerCyrillic, "rulerCyrillic");
        Objects.requireNonNull(rulerGender, "rulerGender");
        Objects.requireNonNull(character, "character");
        Objects.requireNonNull(level, "level");
        traits = List.copyOf(traits);
        if (traits.size() > 2 || towns < 0 || people < 0 || people > MAX_COUNT || soldiers < 0 || soldiers > MAX_COUNT) {
            throw new IllegalArgumentException("Kingdom of the world out of range");
        }
    }

    public static NpcKingdomInfo of(NpcKingdom kingdom) {
        return of(kingdom, false);
    }

    /**
     * @param raising whether the kingdom stands as data and its capital is still being built in the world
     */
    public static NpcKingdomInfo of(NpcKingdom kingdom, boolean raising) {
        return of(kingdom, raising, 0, 0);
    }

    /**
     * @param raising  whether the kingdom stands as data and its capital is still being built in the world
     * @param people   the citizens it is expected to have while raising, else the ones it has; kept within {@link #MAX_COUNT}
     * @param soldiers the soldiers it is expected to have while raising, else the ones it has; kept within {@link #MAX_COUNT}
     */
    public static NpcKingdomInfo of(NpcKingdom kingdom, boolean raising, int people, int soldiers) {
        return new NpcKingdomInfo(kingdom.name().latin(), kingdom.name().cyrillic(), kingdom.rulerName(CitizenNames.Alphabet.LATIN),
                kingdom.rulerName(CitizenNames.Alphabet.CYRILLIC), kingdom.rulerGender(), kingdom.character(), kingdom.traits(), kingdom.level(),
                kingdom.towns().size(), raising, bounded(people), bounded(soldiers));
    }

    private static int bounded(int count) {
        return Math.max(0, Math.min(MAX_COUNT, count));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nameLatin, KingdomNames.MAX_LENGTH);
        buf.writeUtf(nameCyrillic, MAX_NAME);
        buf.writeUtf(rulerLatin, MAX_NAME);
        buf.writeUtf(rulerCyrillic, MAX_NAME);
        buf.writeEnum(rulerGender);
        buf.writeEnum(character);
        buf.writeVarInt(traits.size());
        traits.forEach(buf::writeEnum);
        buf.writeEnum(level);
        buf.writeVarInt(towns);
        buf.writeBoolean(raising);
        buf.writeVarInt(people);
        buf.writeVarInt(soldiers);
    }

    public static NpcKingdomInfo decode(FriendlyByteBuf buf) {
        String nameLatin = buf.readUtf(KingdomNames.MAX_LENGTH);
        String nameCyrillic = buf.readUtf(MAX_NAME);
        String rulerLatin = buf.readUtf(MAX_NAME);
        String rulerCyrillic = buf.readUtf(MAX_NAME);
        Gender gender = KingdomView.readEnum(buf, Gender.class);
        RulerCharacter character = KingdomView.readEnum(buf, RulerCharacter.class);
        int count = buf.readVarInt();
        if (count < 0 || count > 2) {
            throw new DecoderException("Traits out of range: " + count);
        }
        List<RulerCharacter.Trait> traits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            traits.add(KingdomView.readEnum(buf, RulerCharacter.Trait.class));
        }
        NpcLevel level = KingdomView.readEnum(buf, NpcLevel.class);
        int towns = buf.readVarInt();
        if (towns < 0 || towns > 64) {
            throw new DecoderException("Towns out of range: " + towns);
        }
        boolean raising = buf.readBoolean();
        int people = buf.readVarInt();
        int soldiers = buf.readVarInt();
        if (people < 0 || people > MAX_COUNT || soldiers < 0 || soldiers > MAX_COUNT) {
            throw new DecoderException("People out of range: " + people + ", " + soldiers);
        }
        return new NpcKingdomInfo(nameLatin, nameCyrillic, rulerLatin, rulerCyrillic, gender, character, traits, level, towns, raising,
                people, soldiers);
    }

    /**
     * Writes an optional value: a flag, then the value.
     */
    public static void write(FriendlyByteBuf buf, NpcKingdomInfo info) {
        buf.writeBoolean(info != null);
        if (info != null) {
            info.encode(buf);
        }
    }

    public static NpcKingdomInfo read(FriendlyByteBuf buf) {
        return buf.readBoolean() ? decode(buf) : null;
    }
}
