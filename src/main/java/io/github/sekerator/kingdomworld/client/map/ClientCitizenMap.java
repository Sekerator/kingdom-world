package io.github.sekerator.kingdomworld.client.map;

import io.github.sekerator.kingdomworld.common.network.packet.CitizenMapPackets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The citizens of the player's kingdom on the atlas, as the server sent them last: the packets of an answer are gathered, and the
 * atlas shows the answer once its last packet came. Cleared when the player leaves the server.
 */
public final class ClientCitizenMap {
    /**
     * What the atlas shows: the citizens of one whole answer.
     *
     * @param allowed whether the player may see them
     */
    public record Shown(boolean allowed, UUID kingdomId, String dimension, List<CitizenMapPackets.Dot> dots) {
        public Shown {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(dimension, "dimension");
            dots = List.copyOf(dots);
        }

        public static final Shown NONE = new Shown(false, new UUID(0, 0), "", List.of());
    }

    private static Shown shown = Shown.NONE;
    /** The first packet of the answer being gathered, {@code null} while none is. */
    private static CitizenMapPackets.View head;
    private static List<CitizenMapPackets.Dot> gathered = new ArrayList<>();
    private static int nextPage;

    private ClientCitizenMap() {
    }

    /**
     * A packet of an answer came: the first one starts the answer, the next ones add to it in their order, and the last one shows
     * it. A packet out of order drops the answer; the atlas asks again a few seconds later.
     */
    public static void receive(CitizenMapPackets.View packet) {
        if (packet == null) {
            return;
        }
        if (packet.page() == 0) {
            head = packet;
            gathered = new ArrayList<>(packet.dots());
            nextPage = 1;
        } else if (head != null && packet.page() == nextPage && packet.kingdomId().equals(head.kingdomId())
                && packet.dimension().equals(head.dimension())) {
            gathered.addAll(packet.dots());
            nextPage++;
        } else {
            head = null;
            gathered = new ArrayList<>();
            return;
        }
        if (packet.last()) {
            shown = new Shown(head.allowed(), head.kingdomId(), head.dimension(), gathered);
            head = null;
            gathered = new ArrayList<>();
        }
    }

    public static Shown view() {
        return shown;
    }

    /**
     * Forgets the citizens: the player left the server.
     */
    public static void clear() {
        shown = Shown.NONE;
        head = null;
        gathered = new ArrayList<>();
        nextPage = 0;
    }
}
