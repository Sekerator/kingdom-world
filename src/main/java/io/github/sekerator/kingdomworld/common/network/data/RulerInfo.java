package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.npc.RulerDecision;
import io.github.sekerator.kingdomworld.core.npc.RulerMemory;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the overview of a kingdom of the world shows a player of its ruler: its last decrees, what its kingdom needs and saves
 * for, and how it feels about the player (from the player's reputation under its law and their deals).
 *
 * @param attitude   how the ruler feels about the viewer, from -100 to 100
 * @param reputation the reputation of the viewer in the kingdom
 * @param dealings   how the deals of the viewer's kingdom with the ruler went
 * @param councillor the name of the honorary councillor of the ruler, or an empty text
 * @param yours      whether the viewer is that councillor
 */
public record RulerInfo(List<Decree> decrees, List<RulerMemory.Need> needs, int attitude, int reputation, int dealings, String councillor,
                        boolean yours) {
    public static final int MAX_DECREES = 6;
    public static final int MAX_NEEDS = RulerMemory.MAX_NEEDS;
    private static final int MAX_TEXT = 64;

    /**
     * A decree with the day it was made.
     */
    public record Decree(long day, RulerDecision decision) {
        public Decree {
            Objects.requireNonNull(decision, "decision");
        }
    }

    public RulerInfo {
        decrees = List.copyOf(decrees.size() > MAX_DECREES ? decrees.subList(0, MAX_DECREES) : decrees);
        needs = List.copyOf(needs.size() > MAX_NEEDS ? needs.subList(0, MAX_NEEDS) : needs);
        councillor = cut(Objects.requireNonNull(councillor, "councillor"));
    }

    public RulerInfo(List<Decree> decrees, List<RulerMemory.Need> needs, int attitude, int reputation, int dealings) {
        this(decrees, needs, attitude, reputation, dealings, "", false);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(decrees.size());
        for (Decree decree : decrees) {
            writeDecree(buf, decree);
        }
        buf.writeVarInt(needs.size());
        for (RulerMemory.Need need : needs) {
            buf.writeEnum(need.kind());
            buf.writeUtf(cut(need.subject()), MAX_TEXT);
            buf.writeLong(need.amount());
        }
        buf.writeInt(attitude);
        buf.writeInt(reputation);
        buf.writeInt(dealings);
        buf.writeUtf(councillor, MAX_TEXT);
        buf.writeBoolean(yours);
    }

    /**
     * Writes a decree with its day; the chronicle of the citizens' tab sends decrees the same way.
     */
    public static void writeDecree(FriendlyByteBuf buf, Decree decree) {
        RulerDecision decision = decree.decision();
        buf.writeLong(decree.day());
        buf.writeEnum(decision.kind());
        buf.writeUtf(cut(decision.subject()), MAX_TEXT);
        buf.writeLong(decision.amount());
        buf.writeLong(decision.extra());
        buf.writeUtf(cut(decision.target()), MAX_TEXT);
        buf.writeUtf(cut(decision.targetName()), MAX_TEXT);
    }

    public static Decree readDecree(FriendlyByteBuf buf) {
        long day = buf.readLong();
        RulerDecision.Kind kind = KingdomView.readEnum(buf, RulerDecision.Kind.class);
        return new Decree(day, new RulerDecision(kind, buf.readUtf(MAX_TEXT), buf.readLong(), buf.readLong(), buf.readUtf(MAX_TEXT),
                buf.readUtf(MAX_TEXT)));
    }

    private static String cut(String text) {
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text;
    }

    public static RulerInfo decode(FriendlyByteBuf buf) {
        int decreeCount = buf.readVarInt();
        if (decreeCount < 0 || decreeCount > MAX_DECREES) {
            throw new DecoderException("Too many decrees: " + decreeCount);
        }
        List<Decree> decrees = new ArrayList<>(decreeCount);
        for (int i = 0; i < decreeCount; i++) {
            decrees.add(readDecree(buf));
        }
        int needCount = buf.readVarInt();
        if (needCount < 0 || needCount > MAX_NEEDS) {
            throw new DecoderException("Too many needs: " + needCount);
        }
        List<RulerMemory.Need> needs = new ArrayList<>(needCount);
        for (int i = 0; i < needCount; i++) {
            needs.add(new RulerMemory.Need(KingdomView.readEnum(buf, RulerMemory.NeedKind.class), buf.readUtf(MAX_TEXT), buf.readLong()));
        }
        return new RulerInfo(decrees, needs, buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(MAX_TEXT), buf.readBoolean());
    }

    /**
     * Writes an optional value: a flag, then the value.
     */
    public static void write(FriendlyByteBuf buf, @Nullable RulerInfo info) {
        buf.writeBoolean(info != null);
        if (info != null) {
            info.encode(buf);
        }
    }

    @Nullable
    public static RulerInfo read(FriendlyByteBuf buf) {
        return buf.readBoolean() ? decode(buf) : null;
    }
}
