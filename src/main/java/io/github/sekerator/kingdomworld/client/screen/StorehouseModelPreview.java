package io.github.sekerator.kingdomworld.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.common.storage.StorehouseBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * For the layout check only: the models of a double storehouse drawn by the game itself, a pair facing each way seen from
 * the front and from above, a pair seen from the back and a single storehouse, so that a look shows whether the halves meet
 * with the lock on the seam.
 */
public class StorehouseModelPreview extends Screen {
    public StorehouseModelPreview() {
        super(Component.empty());
    }

    public String checkName() {
        return "storehouse-double-model";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF6E8FA8);
        int size = Math.max(12, Math.min(width / 14, height / 6));
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < facings.length; i++) {
            // Seen from the front of each pair: the camera turns with the facing.
            pair(graphics, facings[i], width * (2 * i + 1) / 8, height / 3, size, false);
        }
        pair(graphics, Direction.NORTH, width / 4, height * 3 / 4, size, true);
        single(graphics, width * 5 / 8, height * 3 / 4, size);
    }

    private static BlockState state(Direction facing, ChestType type) {
        return ModRegistries.STOREHOUSE.get().defaultBlockState().setValue(StorehouseBlock.FACING, facing).setValue(StorehouseBlock.TYPE, type);
    }

    /**
     * Both halves of a pair facing one way, as they stand in the world: the left half on the clockwise side of the right one.
     */
    private static void pair(GuiGraphics graphics, Direction facing, int x, int y, int size, boolean back) {
        BlockState left = state(facing, ChestType.LEFT);
        BlockState right = state(facing, ChestType.RIGHT);
        Direction toRight = StorehouseBlock.connectedDirection(left);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 150);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(25));
        // Turn so that the front (or the back) of the pair faces the viewer, a little from the side: without a turn the
        // viewer sees the south side.
        pose.mulPose(Axis.YP.rotationDegrees(facing.toYRot() + (back ? 180 : 0) + 20));
        pose.translate(-0.5f - toRight.getStepX() * 0.5f, -0.5f, -0.5f - toRight.getStepZ() * 0.5f);
        draw(graphics, pose, left, 0, 0);
        draw(graphics, pose, right, toRight.getStepX(), toRight.getStepZ());
        pose.popPose();
    }

    private static void single(GuiGraphics graphics, int x, int y, int size) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 150);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(25));
        pose.mulPose(Axis.YP.rotationDegrees(160));
        pose.translate(-0.5f, -0.5f, -0.5f);
        draw(graphics, pose, ModRegistries.STOREHOUSE.get().defaultBlockState(), 0, 0);
        pose.popPose();
    }

    private static void draw(GuiGraphics graphics, PoseStack pose, BlockState state, int dx, int dz) {
        pose.pushPose();
        pose.translate(dx, 0, dz);
        Lighting.setupForFlatItems();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, graphics.bufferSource(), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        graphics.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
