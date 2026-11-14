package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.common.citizen.CitizenHighlights;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.packet.WorkforcePackets;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.Whereabouts;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The pointer to a citizen the player looks for: a line above the hotbar with an arrow, the name, the distance and the side of the
 * world, following the body while the client has it and pointing at the place the server gave otherwise (the last place known of
 * a citizen far from players). It ends when the player comes within {@value #ARRIVED} blocks, looks for another, calls it off, or
 * after five minutes. The body itself is outlined for this player alone for half a minute.
 */
public final class CitizenFinder {
    /** How far above the bottom of the screen the line is drawn: above the line of the wanted. */
    public static final int OFFSET = 96;
    /** The player is there within this many blocks. */
    public static final int ARRIVED = 5;
    /** How long the pointer lasts, in milliseconds. */
    public static final long MILLIS = 5 * 60 * 1000L;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    @Nullable
    private static WorkforcePackets.Found target;
    private static long since;

    private CitizenFinder() {
    }

    /**
     * The server told where a citizen looked for is.
     */
    public static void found(WorkforcePackets.Found found) {
        target = found;
        since = net.minecraft.Util.getMillis();
        if (found.entity() >= 0) {
            CitizenHighlights.show(found.entity(), since);
        } else {
            CitizenHighlights.clear();
        }
    }

    public static void cancel() {
        target = null;
        CitizenHighlights.clear();
    }

    /**
     * Whether the pointer leads to this citizen now.
     */
    public static boolean tracking(@Nullable UUID citizen) {
        return target != null && citizen != null && target.citizen().equals(citizen) && net.minecraft.Util.getMillis() - since <= MILLIS;
    }

    /**
     * Where the citizen looked for is now: its body while the client has it, the place the server gave otherwise.
     */
    @Nullable
    private static WorldPos where(Minecraft minecraft) {
        if (target == null || minecraft.level == null) {
            return null;
        }
        String dimension = minecraft.level.dimension().location().toString();
        if (target.entity() >= 0 && dimension.equals(target.dimension())) {
            Entity body = minecraft.level.getEntity(target.entity());
            if (body != null && body.isAlive()) {
                return new WorldPos(dimension, body.getBlockX(), body.getBlockY(), body.getBlockZ());
            }
        }
        return new WorldPos(target.dimension(), target.pos().getX(), target.pos().getY(), target.pos().getZ());
    }

    /**
     * The arrow that points at a place from where the player looks: up is straight ahead.
     */
    static String arrow(float yaw, int dx, int dz) {
        // The yaw of the game: 0 looks south (+z), 90 west (-x); the side of the place clockwise from north.
        double toPlace = Math.toDegrees(Math.atan2(dx, -dz));
        double facing = yaw + 180.0;
        double relative = ((toPlace - facing) % 360 + 360) % 360;
        return ARROWS[(int) Math.round(relative / 45.0) % 8];
    }

    /**
     * The line of the pointer.
     */
    static Component line(String arrow, int seed, boolean female, int distance, @Nullable Whereabouts.Compass side, boolean live) {
        Component name = CitizenTexts.name(seed, female ? Gender.FEMALE : Gender.MALE);
        // The last place known takes more words, so its side of the world is told short.
        Component way = side == null ? Component.translatable("kingdomworld.finder.other_world")
                : live ? Component.translatable("kingdomworld.finder.way", distance, Component.translatable("kingdomworld.compass." + side.id()))
                : Component.translatable("kingdomworld.finder.way_short", distance, Component.translatable("kingdomworld.compass.short." + side.id()));
        return Component.translatable(live ? "kingdomworld.finder.line" : "kingdomworld.finder.line_last", arrow, name, way);
    }

    /**
     * Draws the line from the game's overlay, and ends the pointer when the player is there or the time is up.
     */
    static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (target == null || player == null) {
            return;
        }
        if (net.minecraft.Util.getMillis() - since > MILLIS) {
            cancel();
            return;
        }
        WorldPos at = where(minecraft);
        if (at == null) {
            return;
        }
        WorldPos here = new WorldPos(minecraft.level.dimension().location().toString(), player.getBlockX(), player.getBlockY(), player.getBlockZ());
        int distance = Whereabouts.distance(here, at);
        if (distance != Whereabouts.FAR && distance <= ARRIVED) {
            cancel();
            return;
        }
        if (minecraft.options.hideGui) {
            return;
        }
        Component text;
        if (distance == Whereabouts.FAR) {
            text = line("•", target.seed(), target.female(), 0, null, target.live());
        } else {
            int dx = at.x() - here.x();
            int dz = at.z() - here.z();
            text = line(arrow(player.getYRot(), dx, dz), target.seed(), target.female(), distance, Whereabouts.compass(dx, dz), target.live());
        }
        render(graphics, minecraft.font, text, width, height);
    }

    static void render(GuiGraphics graphics, Font font, Component text, int width, int height) {
        int textWidth = font.width(text);
        int x = (width - textWidth) / 2;
        int y = height - OFFSET;
        graphics.fill(x - 3, y - 2, x + textWidth + 3, y + 10, 0x90000000);
        graphics.drawString(font, text, x, y, 0xFFFFE070);
    }

    static void reset() {
        target = null;
        CitizenHighlights.clear();
    }
}
