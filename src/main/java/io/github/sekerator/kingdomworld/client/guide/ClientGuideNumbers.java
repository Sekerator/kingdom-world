package io.github.sekerator.kingdomworld.client.guide;

import io.github.sekerator.kingdomworld.common.guide.GuideNumbers;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.GuidePackets;
import io.github.sekerator.kingdomworld.core.guide.GuideInline;
import io.github.sekerator.kingdomworld.core.guide.GuideNumbersFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The numbers of the configuration the guide shows on the client: the values of the server once it has sent them, the
 * defaults until then and where no server is at hand. They are asked for once a connection, when the guide first opens, and
 * asked again when the guide opens later and no answer came.
 */
public final class ClientGuideNumbers {
    /** How long an answer is waited for before the guide asks again. */
    static final long RETRY_MILLIS = 15_000L;
    private static final Map<String, String> VALUES = new HashMap<>();
    private static boolean answered;
    private static long askedAt = -1;

    private ClientGuideNumbers() {
    }

    /**
     * Asks the server for the values of {@code paths} unless it answered on this connection already or was asked a moment ago.
     */
    public static void request(Collection<String> paths) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = Util.getMillis();
        if (answered || minecraft.getConnection() == null || paths.isEmpty() || (askedAt >= 0 && now - askedAt < RETRY_MILLIS)) {
            return;
        }
        askedAt = now;
        List<String> asked = new ArrayList<>();
        for (String path : paths) {
            if (asked.size() < GuideNumbers.MAX_PATHS && path.length() <= GuideNumbers.MAX_PATH) {
                asked.add(path);
            }
        }
        KingdomWorldNetwork.sendToServer(new GuidePackets.NumbersRequest(asked));
    }

    /**
     * The values the server sent.
     */
    public static void received(Map<String, String> values) {
        VALUES.putAll(values);
        answered = true;
    }

    /**
     * Forgets the values of the server, when the player leaves it.
     */
    public static void clear() {
        VALUES.clear();
        answered = false;
        askedAt = -1;
    }

    /**
     * The raw value at {@code path}: the server's, or the default.
     */
    public static String raw(String path) {
        String value = VALUES.get(path);
        return value != null ? value : GuideNumbers.defaultValue(path).orElse(null);
    }

    /**
     * The number a token of a page shows.
     *
     * @param comma whether the language of the guide writes decimals with a comma
     */
    public static String text(GuideInline.Number number, boolean comma) {
        return GuideNumbersFormat.format(raw(number.path()), number, comma, net.minecraft.client.resources.language.I18n.get("kingdomworld.guide.no_limit"));
    }
}
