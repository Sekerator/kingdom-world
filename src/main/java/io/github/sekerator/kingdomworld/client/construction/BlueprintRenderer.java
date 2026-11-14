package io.github.sekerator.kingdomworld.client.construction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sekerator.kingdomworld.common.construction.TemplateBlocks;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.economy.Plurals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the building of a blueprint where it would stand: its blocks half transparent, the box it takes, and in red the
 * blocks in the way and the places the server refuses. Above it the hints: the keys and what the server said about the
 * place. Client only.
 */
public final class BlueprintRenderer {
    /** Most blocks drawn of a building; larger ones show their box only. */
    public static final int MAX_DRAWN = 6_000;
    private static final float ALPHA = 0.45f;

    private BlueprintRenderer() {
    }

    // ---------------------------------------------------------------- in the world

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        renderRuler(event, minecraft);
        renderStakes(event, minecraft);
        String held = ClientBlueprint.heldTemplate();
        BlockPos anchor = ClientBlueprint.anchor();
        ClientBlueprint.Template template = held.isEmpty() ? null : ClientBlueprint.template(held);
        if (template == null || anchor == null || minecraft.level == null) {
            return;
        }
        // The outline shows the wood and stone of the blueprint; the wood of the place by the biome under it.
        io.github.sekerator.kingdomworld.core.construction.Materials materials = ClientBlueprint.materials(minecraft.level, anchor);
        Placement placement = ClientBlueprint.placement();
        ClientBlueprint.Check check = ClientBlueprint.check();
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        MultiBufferSource ghost = type -> new Faded(buffers.getBuffer(Sheets.translucentCullBlockSheet()), ALPHA);
        List<BlockPos> obstacles = new ArrayList<>();
        int drawn = 0;
        for (int i = 0; i < template.positions().size(); i++) {
            BlockPos at = place(template, anchor, placement, template.positions().get(i));
            BlockState state = TemplateBlocks.transform(
                    io.github.sekerator.kingdomworld.common.construction.TemplateMaterials.substitute(template.states().get(i), materials), placement);
            BlockState there = minecraft.level.getBlockState(at);
            boolean built = TemplateBlocks.sameBuilt(there, state);
            if (!there.isAir() && !there.canBeReplaced() && !built) {
                obstacles.add(at);
            }
            if (drawn < MAX_DRAWN && state.getRenderShape() == RenderShape.MODEL && !built) {
                pose.pushPose();
                pose.translate(at.getX() - camera.x, at.getY() - camera.y, at.getZ() - camera.z);
                dispatcher.renderSingleBlock(state, pose, ghost, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
                drawn++;
            }
        }
        buffers.endBatch(Sheets.translucentCullBlockSheet());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        var box = placement.box(template.sizeX(), template.sizeY(), template.sizeZ(), template.anchor().getX(), template.anchor().getY(),
                template.anchor().getZ(), anchor.getX(), anchor.getY(), anchor.getZ());
        boolean refused = check != null && !check.ok();
        float red = refused ? 1.0f : 0.4f;
        float green = refused ? 0.3f : 1.0f;
        LevelRenderer.renderLineBox(pose, lines, new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1)
                .move(-camera.x, -camera.y, -camera.z), red, green, 0.4f, 1.0f);
        int marked = 0;
        for (BlockPos at : obstacles) {
            if (marked++ > 512) {
                break;
            }
            LevelRenderer.renderLineBox(pose, lines, new AABB(at).inflate(0.01).move(-camera.x, -camera.y, -camera.z), 1.0f, 0.2f, 0.2f, 1.0f);
        }
        if (check != null) {
            for (BlockPos at : check.conflicts()) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(at).inflate(0.03).move(-camera.x, -camera.y, -camera.z), 1.0f, 0.0f, 0.0f, 1.0f);
            }
        }
        buffers.endBatch(RenderType.lines());
        lastObstacles = obstacles.size();
    }

    private static int lastObstacles;

    /**
     * The box between the corners of the architect's ruler in hand, yellow while only one corner is marked.
     */
    private static void renderRuler(RenderLevelStageEvent event, Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        net.minecraft.world.item.ItemStack held = minecraft.player.getMainHandItem();
        if (!held.is(io.github.sekerator.kingdomworld.common.registry.ModRegistries.ARCHITECT_RULER.get())) {
            return;
        }
        var corners = io.github.sekerator.kingdomworld.common.construction.ArchitectRulerItem.corners(held);
        if (corners.first() == null) {
            return;
        }
        BlockPos first = corners.first();
        BlockPos second = corners.second() != null ? corners.second() : first;
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        AABB box = new AABB(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()) + 1, Math.max(first.getY(), second.getY()) + 1, Math.max(first.getZ(), second.getZ()) + 1);
        float green = corners.complete() ? 1.0f : 0.85f;
        LevelRenderer.renderLineBox(event.getPoseStack(), lines, box.move(-camera.x, -camera.y, -camera.z), corners.complete() ? 0.3f : 1.0f,
                green, 0.3f, 1.0f);
        buffers.endBatch(RenderType.lines());
    }

    /**
     * The rectangle between the survey stakes in hand, yellow while only one corner is marked, and once the server looked at it
     * the height of the work as a plane over it and the places it leaves as they are in red.
     */
    private static void renderStakes(RenderLevelStageEvent event, Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        net.minecraft.world.item.ItemStack held = minecraft.player.getMainHandItem();
        if (!held.is(io.github.sekerator.kingdomworld.common.registry.ModRegistries.SURVEY_STAKES.get())) {
            return;
        }
        var corners = io.github.sekerator.kingdomworld.common.construction.ArchitectRulerItem.corners(held);
        if (corners.first() == null) {
            return;
        }
        BlockPos first = corners.first();
        BlockPos second = corners.second() != null ? corners.second() : first;
        var answer = corners.complete() ? ClientEarthwork.last(first, second) : null;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        int minX = Math.min(first.getX(), second.getX());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX()) + 1;
        int maxZ = Math.max(first.getZ(), second.getZ()) + 1;
        int low = Math.min(first.getY(), second.getY());
        int high = Math.max(first.getY(), second.getY()) + 1;
        if (answer != null) {
            low = Math.min(low, answer.target());
            high = Math.max(high, answer.target() + 1);
        }
        LevelRenderer.renderLineBox(pose, lines, new AABB(minX, low, minZ, maxX, high, maxZ).move(-camera.x, -camera.y, -camera.z),
                corners.complete() ? 0.9f : 1.0f, corners.complete() ? 0.7f : 0.85f, 0.2f, 1.0f);
        if (answer != null) {
            double plane = answer.target() + 1.02;
            LevelRenderer.renderLineBox(pose, lines, new AABB(minX, plane, minZ, maxX, plane, maxZ).move(-camera.x, -camera.y, -camera.z),
                    0.3f, 0.8f, 1.0f, 1.0f);
            for (BlockPos kept : answer.places()) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(kept).inflate(0.03).move(-camera.x, -camera.y, -camera.z), 1.0f, 0.1f, 0.1f, 1.0f);
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    private static BlockPos place(ClientBlueprint.Template template, BlockPos anchor, Placement placement, BlockPos pos) {
        int[] p = placement.apply(pos.getX() - template.anchor().getX(), pos.getY() - template.anchor().getY(), pos.getZ() - template.anchor().getZ());
        return anchor.offset(p[0], p[1], p[2]);
    }

    /**
     * Passes vertices on with their alpha lowered.
     */
    private record Faded(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            delegate.color(red, green, blue, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int a) {
            delegate.defaultColor(red, green, blue, (int) (a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }

    // ---------------------------------------------------------------- the hints

    /**
     * A line of the hints with its color.
     */
    public record Line(Component text, int color) {
    }

    /**
     * The hints for the blueprint in hand, or none without one.
     */
    public static List<Line> lines() {
        String held = ClientBlueprint.heldTemplate();
        if (held.isEmpty()) {
            return rulerLines();
        }
        ClientBlueprint.Template template = ClientBlueprint.template(held);
        Minecraft minecraft = Minecraft.getInstance();
        Component status;
        int color;
        if (template == null) {
            status = Component.translatable("kingdomworld.blueprint.hud.loading");
            color = 0xFFB8B8B8;
        } else if (!template.available()) {
            status = Component.translatable("kingdomworld.blueprint.hud.unavailable");
            color = 0xFFFF8080;
        } else if (ClientBlueprint.anchor() == null) {
            status = Component.translatable("kingdomworld.blueprint.hud.no_target");
            color = 0xFFB8B8B8;
        } else if (ClientBlueprint.check() == null) {
            status = Component.translatable("kingdomworld.blueprint.hud.checking");
            color = 0xFFB8B8B8;
        } else {
            ClientBlueprint.Check check = ClientBlueprint.check();
            status = check.message();
            color = check.ok() ? 0xFFA0E0A0 : 0xFFFF8080;
        }
        return lines(template == null ? Component.literal(held) : template.name(), ClientBlueprint.ROTATE_KEY.getTranslatedKeyMessage(),
                ClientBlueprint.MIRROR_KEY.getTranslatedKeyMessage(), minecraft.options.keyUse.getTranslatedKeyMessage(), status, color,
                template != null && ClientBlueprint.anchor() != null ? lastObstacles : 0);
    }

    /**
     * The hints for given texts; the layout check draws them with the longest texts.
     */
    public static List<Line> lines(Component name, Component rotate, Component mirror, Component use, Component status, int statusColor,
                                   int obstacles) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Component.translatable("kingdomworld.blueprint.hud.title", name), 0xFFF0D070));
        lines.add(new Line(Component.translatable("kingdomworld.blueprint.hud.keys", rotate, mirror, use), 0xFFE8E8E8));
        lines.add(new Line(status, statusColor));
        if (obstacles > 0) {
            lines.add(new Line(Component.translatable("kingdomworld.blueprint.hud.obstacles", blocks(obstacles)), 0xFFFFB070));
        }
        return lines;
    }

    /**
     * The hints for the architect's ruler in hand, or none without one.
     */
    public static List<Line> rulerLines() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return List.of();
        }
        net.minecraft.world.item.ItemStack held = minecraft.player.getMainHandItem();
        if (!held.is(io.github.sekerator.kingdomworld.common.registry.ModRegistries.ARCHITECT_RULER.get())) {
            return List.of();
        }
        var corners = io.github.sekerator.kingdomworld.common.construction.ArchitectRulerItem.corners(held);
        return rulerLines(minecraft.options.keyUse.getTranslatedKeyMessage(), minecraft.options.keyShift.getTranslatedKeyMessage(),
                corners.first() == null ? 0 : corners.complete() ? 2 : 1, corners.size());
    }

    /**
     * The hints of the ruler for given keys and corners; the layout check draws them with the longest texts.
     *
     * @param marked corners marked, 0 to 2
     */
    public static List<Line> rulerLines(Component use, Component sneak, int marked, int[] size) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Component.translatable("kingdomworld.ruler.hud.title"), 0xFFF0D070));
        lines.add(new Line(Component.translatable(marked == 0 ? "kingdomworld.ruler.hud.first" : marked == 1 ? "kingdomworld.ruler.hud.second"
                : "kingdomworld.ruler.hud.again", use), 0xFFE8E8E8));
        if (marked == 2) {
            lines.add(new Line(Component.translatable("kingdomworld.ruler.hud.size", size[0], size[1], size[2]), 0xFFA0E0A0));
            lines.add(new Line(Component.translatable("kingdomworld.ruler.hud.save", sneak, use), 0xFFE8E8E8));
        }
        return lines;
    }

    /**
     * A number of blocks: «5 блоков», "5 blocks".
     */
    public static Component blocks(long count) {
        return Component.translatable("kingdomworld.count.blocks." + Plurals.form(count).suffix(), count);
    }

    /**
     * Where each wrapped line of the hints goes on a screen of the given width: a box at the top, centered.
     *
     * @return the lines with their positions, the box first as {x, y, width, height}
     */
    public static Layout layout(Font font, int screenWidth, List<Line> lines) {
        int widest = 0;
        for (Line line : lines) {
            widest = Math.max(widest, font.width(line.text()));
        }
        int boxWidth = Math.min(screenWidth - 16, widest + 8);
        List<Placed> placed = new ArrayList<>();
        int y = 6;
        for (Line line : lines) {
            for (FormattedCharSequence part : font.split(line.text(), boxWidth - 8)) {
                placed.add(new Placed(line, part, y));
                y += 10;
            }
        }
        int x = (screenWidth - boxWidth) / 2;
        return new Layout(x, 2, boxWidth, y - 2, placed);
    }

    public record Placed(Line line, FormattedCharSequence text, int y) {
    }

    public record Layout(int x, int y, int width, int height, List<Placed> lines) {
    }

    /**
     * Draws the hints above the game.
     */
    public static void drawHud(GuiGraphics graphics, Font font, int screenWidth) {
        List<Line> lines = lines();
        if (lines.isEmpty() || Minecraft.getInstance().screen != null) {
            return;
        }
        Layout layout = layout(font, screenWidth, lines);
        graphics.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), 0xA0000000);
        for (Placed line : layout.lines()) {
            graphics.drawString(font, line.text(), layout.x() + 4, line.y(), line.line().color());
        }
    }

    /**
     * Item counts for messages, kept here so that both texts use the same forms.
     */
    public static Component items(long count) {
        return TradeTexts.items(count);
    }
}
