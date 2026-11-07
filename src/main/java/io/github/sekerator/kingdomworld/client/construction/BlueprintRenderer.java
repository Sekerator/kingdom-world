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
        String held = ClientBlueprint.heldTemplate();
        BlockPos anchor = ClientBlueprint.anchor();
        ClientBlueprint.Template template = held.isEmpty() ? null : ClientBlueprint.template(held);
        if (template == null || anchor == null || minecraft.level == null) {
            return;
        }
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
            BlockState state = TemplateBlocks.transform(template.states().get(i), placement);
            BlockState there = minecraft.level.getBlockState(at);
            if (!there.isAir() && !there.canBeReplaced() && !there.equals(state)) {
                obstacles.add(at);
            }
            if (drawn < MAX_DRAWN && state.getRenderShape() == RenderShape.MODEL && !there.equals(state)) {
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
            return List.of();
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
