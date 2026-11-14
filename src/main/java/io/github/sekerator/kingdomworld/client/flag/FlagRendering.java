package io.github.sekerator.kingdomworld.client.flag;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.datafixers.util.Pair;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomFlag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Holder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.List;

/**
 * Draws flags in screens the way the loom draws its banner: the cloth of a banner without its pole, flat and lit evenly.
 *
 * <p>At scale {@code s} the cloth is {@code 5s/6} pixels wide and {@code 5s/3} high: scale 6 gives 5x10, 12 gives 10x20, 18 gives
 * 15x30, 24 gives 20x40 and 48 gives 40x80.
 */
public final class FlagRendering {
    private static final int FULL_BRIGHT = 15728880;
    private static ModelPart flag;

    private FlagRendering() {
    }

    /**
     * Takes the cloth of the banner from the models of the game again; screens call it when they lay themselves out, so that a
     * reload of the resources is followed.
     */
    public static void bake() {
        flag = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
    }

    /**
     * The width of a flag drawn at a scale.
     */
    public static int width(int scale) {
        return scale * 5 / 6;
    }

    /**
     * The height of a flag drawn at a scale.
     */
    public static int height(int scale) {
        return scale * 5 / 3;
    }

    /**
     * Draws a flag with its top left corner at {@code x}, {@code y}.
     */
    public static void drawGui(GuiGraphics graphics, KingdomFlag kingdomFlag, int x, int y, int scale) {
        drawGui(graphics, ClientFlags.layers(kingdomFlag), x, y, scale);
    }

    /**
     * Draws the layers of a banner, the field first, with the top left corner of the cloth at {@code x}, {@code y}.
     */
    public static void drawGui(GuiGraphics graphics, List<Pair<Holder<BannerPattern>, DyeColor>> layers, int x, int y, int scale) {
        if (flag == null) {
            bake();
        }
        Lighting.setupForFlatItems();
        graphics.pose().pushPose();
        // The recipe of the loom, moved so that the cloth starts at x, y: it lies from s/12 right of the origin and 11s/6 above it.
        graphics.pose().translate(x - scale / 12.0f, y + scale * 11 / 6.0f, 0.0f);
        graphics.pose().scale(scale, -scale, 1.0f);
        graphics.pose().translate(0.5f, 0.5f, 0.5f);
        graphics.pose().scale(2 / 3.0f, -2 / 3.0f, -2 / 3.0f);
        flag.xRot = 0.0f;
        flag.y = -32.0f;
        BannerRenderer.renderPatterns(graphics.pose(), graphics.bufferSource(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, flag,
                ModelBakery.BANNER_BASE, true, layers);
        graphics.pose().popPose();
        graphics.flush();
        Lighting.setupFor3DItems();
    }
}
