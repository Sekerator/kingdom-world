package io.github.sekerator.kingdomworld.client.render;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.road.BanditEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * Draws the people of the roads with the player model: robbers in dark clothes with a cloth over the face, their leader in
 * red, merchants of caravans in blue; with the armor they wear.
 */
public class RoadPeopleRenderer<T extends Mob> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private static final ResourceLocation BANDIT = KingdomWorld.id("textures/entity/road/bandit.png");
    private static final ResourceLocation LEADER = KingdomWorld.id("textures/entity/road/bandit_leader.png");
    private static final ResourceLocation MERCHANT = KingdomWorld.id("textures/entity/road/caravan_merchant.png");

    public RoadPeopleRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        if (entity instanceof BanditEntity bandit) {
            return bandit.isLeader() ? LEADER : BANDIT;
        }
        return MERCHANT;
    }
}
