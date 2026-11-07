package io.github.sekerator.kingdomworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.companion.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a companion as a person, like a citizen: the player model with the looks of citizens, the gear it carries, and a
 * sash that tells a slave (a dark, worn one) from a free companion (a green one). The name shows above the head up close.
 */
public class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity, PlayerModel<CompanionEntity>> {
    private static final double NAME_DISTANCE_SQR = 8.0 * 8.0;
    private static final ResourceLocation[] MALE = textures("male");
    private static final ResourceLocation[] FEMALE = textures("female");
    private static final ResourceLocation SASH = KingdomWorld.id("textures/entity/citizen/sash.png");
    private static final int SLAVE_SASH = 0x5A4632;
    private static final int FREE_SASH = 0x2E8B57;

    private final PlayerModel<CompanionEntity> wide;
    private final PlayerModel<CompanionEntity> slim;

    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = model;
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
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
    public void render(CompanionEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = entity.isFemale() ? slim : wide;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity entity) {
        ResourceLocation[] looks = entity.isFemale() ? FEMALE : MALE;
        return looks[Math.floorMod(entity.skin(), looks.length)];
    }

    @Override
    protected boolean shouldShowName(CompanionEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity.hasCustomName() && Minecraft.renderNames() && !entity.isInvisible()
                && minecraft.cameraEntity != null && entity.distanceToSqr(minecraft.cameraEntity) < NAME_DISTANCE_SQR;
    }

    private static final class SashLayer extends RenderLayer<CompanionEntity, PlayerModel<CompanionEntity>> {
        SashLayer(RenderLayerParent<CompanionEntity, PlayerModel<CompanionEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CompanionEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float headYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            int color = entity.isSlave() ? SLAVE_SASH : FREE_SASH;
            renderColoredCutoutModel(getParentModel(), SASH, pose, buffers, light, entity, ((color >> 16) & 0xFF) / 255.0f,
                    ((color >> 8) & 0xFF) / 255.0f, (color & 0xFF) / 255.0f);
        }
    }
}
