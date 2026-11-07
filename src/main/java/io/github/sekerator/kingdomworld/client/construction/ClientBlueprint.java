package io.github.sekerator.kingdomworld.client.construction;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sekerator.kingdomworld.common.construction.BlueprintItem;
import io.github.sekerator.kingdomworld.common.construction.TemplateBlocks;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.packet.ConstructionPackets;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.construction.Facing;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blueprint in the player's hand: the blocks of its template (asked from the server once), where the building would
 * stand (in front of the block the player looks at, its entrance towards the player), how the player turned and mirrored
 * it, and what the server said about that place. A click with the use key lays the construction out; the server decides.
 * Client only.
 */
public final class ClientBlueprint {
    /** Turns the building a quarter; R by default. */
    public static final KeyMapping ROTATE_KEY = new KeyMapping("key.kingdomworld.blueprint_rotate", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.kingdomworld");
    /** Mirrors the building; M by default. */
    public static final KeyMapping MIRROR_KEY = new KeyMapping("key.kingdomworld.blueprint_mirror", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.kingdomworld");
    /** How far the player looks for the place of a building, in blocks. */
    private static final double PICK_RANGE = 48.0;
    /** Ticks between two checks of a place by the server at most. */
    private static final int CHECK_TICKS = 5;

    /**
     * The blocks of a template as the server sent them.
     */
    public record Template(String id, int revision, long fingerprint, Component name, boolean available, int sizeX, int sizeY, int sizeZ,
                           BlockPos anchor, Facing entrance, List<BlockPos> positions, List<BlockState> states) {
    }

    /**
     * What the server said about a place.
     */
    public record Check(String template, BlockPos pos, int rotation, boolean mirror, boolean ok, Component message, Set<BlockPos> conflicts) {
    }

    private static final Map<String, Template> TEMPLATES = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    private static int turn;
    private static boolean mirror;
    @Nullable
    private static BlockPos anchor;
    private static Placement placement = Placement.NONE;
    @Nullable
    private static Check check;
    private static String sentKey = "";
    private static int checkCooldown;

    private ClientBlueprint() {
    }

    public static void clear() {
        TEMPLATES.clear();
        REQUESTED.clear();
        check = null;
        anchor = null;
        sentKey = "";
        turn = 0;
        mirror = false;
    }

    // ---------------------------------------------------------------- from the server

    public static void accept(ConstructionPackets.TemplateData data) {
        List<BlockPos> positions = new ArrayList<>(data.states().length);
        List<BlockState> states = new ArrayList<>(data.states().length);
        for (int i = 0; i < data.states().length; i++) {
            positions.add(new BlockPos(data.positions()[i * 3], data.positions()[i * 3 + 1], data.positions()[i * 3 + 2]));
            states.add(Block.stateById(data.states()[i]));
        }
        Facing entrance = Facing.values()[Math.max(0, Math.min(3, data.entrance()))];
        TEMPLATES.put(data.template(), new Template(data.template(), data.revision(), data.fingerprint(), data.name(), data.available(),
                data.sizeX(), data.sizeY(), data.sizeZ(), data.anchor(), entrance, positions, states));
        REQUESTED.remove(data.template());
        sentKey = "";
    }

    public static void accept(ConstructionPackets.CheckResult result) {
        check = new Check(result.template(), result.pos(), result.rotation(), result.mirror(), result.ok(), result.message(),
                new HashSet<>(result.conflicts()));
    }

    // ---------------------------------------------------------------- the state

    /**
     * The template of the blueprint in the main hand, or empty.
     */
    public static String heldTemplate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return "";
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        return stack.is(ModRegistries.BLUEPRINT.get()) ? BlueprintItem.templateId(stack) : "";
    }

    /**
     * The wood and stone of the blueprint in the main hand, the wood of the place decided by the biome at the anchor.
     */
    public static io.github.sekerator.kingdomworld.core.construction.Materials materials(net.minecraft.world.level.Level level, BlockPos at) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return io.github.sekerator.kingdomworld.core.construction.Materials.NONE;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!stack.is(ModRegistries.BLUEPRINT.get())) {
            return io.github.sekerator.kingdomworld.core.construction.Materials.NONE;
        }
        return io.github.sekerator.kingdomworld.common.construction.TemplateMaterials.resolve(BlueprintItem.materials(stack), level, at);
    }

    @Nullable
    public static Template template(String id) {
        return TEMPLATES.get(id);
    }

    @Nullable
    public static BlockPos anchor() {
        return anchor;
    }

    public static Placement placement() {
        return placement;
    }

    /**
     * The server's answer about the place shown now, or {@code null} while there is none.
     */
    @Nullable
    public static Check check() {
        if (check == null || anchor == null || !check.template().equals(heldTemplate()) || !check.pos().equals(anchor)
                || check.rotation() != placement.rotation() || check.mirror() != placement.mirror()) {
            return null;
        }
        return check;
    }

    /**
     * Called every client tick: keys, the place under the crosshair and the check by the server.
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        String held = heldTemplate();
        while (ROTATE_KEY.consumeClick()) {
            if (!held.isEmpty() && minecraft.screen == null) {
                turn = (turn + 1) % 4;
            }
        }
        while (MIRROR_KEY.consumeClick()) {
            if (!held.isEmpty() && minecraft.screen == null) {
                mirror = !mirror;
            }
        }
        if (held.isEmpty() || minecraft.player == null || minecraft.level == null) {
            anchor = null;
            return;
        }
        Template template = TEMPLATES.get(held);
        if (template == null) {
            if (REQUESTED.add(held) && minecraft.getConnection() != null) {
                KingdomWorldNetwork.sendToServer(new ConstructionPackets.TemplateRequest(held));
            }
            anchor = null;
            return;
        }
        HitResult hit = minecraft.player.pick(PICK_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            anchor = null;
            return;
        }
        anchor = blockHit.getBlockPos().relative(blockHit.getDirection());
        placement = placementFacing(template.entrance(), TemplateBlocks.facing(minecraft.player.getDirection()).opposite(), turn, mirror);
        if (checkCooldown > 0) {
            checkCooldown--;
        }
        String key = held + "@" + anchor.asLong() + "/" + placement.rotation() + "/" + placement.mirror();
        if (!key.equals(sentKey) && checkCooldown == 0 && minecraft.getConnection() != null) {
            sentKey = key;
            checkCooldown = CHECK_TICKS;
            KingdomWorldNetwork.sendToServer(new ConstructionPackets.Place(held, anchor, placement.rotation(), placement.mirror(), false));
        }
    }

    /**
     * The placement that turns the entrance of a template towards a direction, turned further by quarter turns.
     */
    public static Placement placementFacing(Facing entrance, Facing towards, int extraTurns, boolean mirrored) {
        for (int rotation = 0; rotation < 4; rotation++) {
            Placement candidate = new Placement(rotation, mirrored);
            if (candidate.apply(entrance) == towards) {
                return new Placement(rotation + extraTurns, mirrored);
            }
        }
        return new Placement(extraTurns, mirrored);
    }

    /**
     * A click with the use key while holding a blueprint lays the construction out where it is shown, instead of using
     * the item.
     */
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        String held = heldTemplate();
        if (held.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (anchor != null && Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new ConstructionPackets.Place(held, anchor, placement.rotation(), placement.mirror(), true));
            sentKey = "";
        }
    }
}
