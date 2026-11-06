package io.github.sekerator.kingdomworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a citizen as a person with the player model: wide arms for men, slim arms for women, one of several looks
 * per gender, and a sash in the color of the kingdom. The name shows above the head only up close.
 */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, PlayerModel<CitizenEntity>> {
    /** Names show within this distance (squared blocks). */
    private static final double NAME_DISTANCE_SQR = 8.0 * 8.0;
    private static final ResourceLocation[] MALE = textures("male");
    private static final ResourceLocation[] FEMALE = textures("female");
    private static final ResourceLocation SASH = KingdomWorld.id("textures/entity/citizen/sash.png");

    private final PlayerModel<CitizenEntity> wide;
    private final PlayerModel<CitizenEntity> slim;

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = model;
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new SashLayer(this));
    }

    private static ResourceLocation[] textures(String gender) {
        ResourceLocation[] result = new ResourceLocation[CitizenLife.SKINS];
        for (int i = 0; i < result.length; i++) {
            result[i] = KingdomWorld.id("textures/entity/citizen/" + gender + "_" + i + ".png");
        }
        return result;
    }

    @Override
    public void render(CitizenEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = entity.isFemale() ? slim : wide;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CitizenEntity entity) {
        ResourceLocation[] looks = entity.isFemale() ? FEMALE : MALE;
        return looks[Math.floorMod(entity.skin(), looks.length)];
    }

    @Override
    protected boolean shouldShowName(CitizenEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity.hasCustomName() && Minecraft.renderNames() && !entity.isInvisible()
                && minecraft.cameraEntity != null && entity.distanceToSqr(minecraft.cameraEntity) < NAME_DISTANCE_SQR;
    }

    /**
     * The sash: the clothing layer of the model drawn with a white sash texture tinted in the kingdom's color.
     */
    private static final class SashLayer extends RenderLayer<CitizenEntity, PlayerModel<CitizenEntity>> {
        SashLayer(RenderLayerParent<CitizenEntity, PlayerModel<CitizenEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CitizenEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float headYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            int color = entity.kingdomColor();
            float red = ((color >> 16) & 0xFF) / 255.0f;
            float green = ((color >> 8) & 0xFF) / 255.0f;
            float blue = (color & 0xFF) / 255.0f;
            renderColoredCutoutModel(getParentModel(), SASH, pose, buffers, light, entity, red, green, blue);
        }
    }
}
