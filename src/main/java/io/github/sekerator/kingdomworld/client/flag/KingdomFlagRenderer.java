package io.github.sekerator.kingdomworld.client.flag;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.sekerator.kingdomworld.common.block.KingdomFlagBlock;
import io.github.sekerator.kingdomworld.common.block.KingdomFlagBlockEntity;
import io.github.sekerator.kingdomworld.common.block.KingdomWallFlagBlock;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;

/**
 * Draws the flag of a kingdom in the world as the game draws a banner, standing or on a wall, waving with the time and its
 * place, with the flag its kingdom flies now.
 */
public class KingdomFlagRenderer implements BlockEntityRenderer<KingdomFlagBlockEntity> {
    private final ModelPart flag;
    private final ModelPart pole;
    private final ModelPart bar;

    public KingdomFlagRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.BANNER);
        flag = root.getChild("flag");
        pole = root.getChild("pole");
        bar = root.getChild("bar");
    }

    @Override
    public void render(KingdomFlagBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        long time = entity.getLevel() == null ? 0L : entity.getLevel().getGameTime();
        BlockState state = entity.getBlockState();
        pose.pushPose();
        if (state.getBlock() instanceof KingdomWallFlagBlock) {
            pose.translate(0.5f, -0.16666667f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(KingdomWallFlagBlock.FACING).toYRot()));
            pose.translate(0.0f, -0.3125f, -0.4375f);
            pole.visible = false;
        } else {
            pose.translate(0.5f, 0.5f, 0.5f);
            int rotation = state.hasProperty(KingdomFlagBlock.ROTATION) ? state.getValue(KingdomFlagBlock.ROTATION) : 0;
            pose.mulPose(Axis.YP.rotationDegrees(-RotationSegment.convertToDegrees(rotation)));
            pole.visible = true;
        }
        pose.pushPose();
        pose.scale(0.6666667f, -0.6666667f, -0.6666667f);
        VertexConsumer wood = ModelBakery.BANNER_BASE.buffer(buffers, RenderType::entitySolid);
        pole.render(pose, wood, light, overlay);
        bar.render(pose, wood, light, overlay);
        BlockPos pos = entity.getBlockPos();
        float wave = ((float) Math.floorMod(pos.getX() * 7L + pos.getY() * 9L + pos.getZ() * 13L + time, 100L) + partialTick) / 100.0f;
        flag.xRot = (-0.0125f + 0.01f * Mth.cos((float) (Math.PI * 2) * wave)) * (float) Math.PI;
        flag.y = -32.0f;
        BannerRenderer.renderPatterns(pose, buffers, light, overlay, flag, ModelBakery.BANNER_BASE, true, ClientFlags.layers(entity.kingdom()));
        pose.popPose();
        pose.popPose();
    }
}
