package io.github.sekerator.kingdomworld.common.citizen.work;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.citizen.CitizenSettings;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The farmer: works around a composter in the kingdom's land. Near players it harvests ripe crops, replants them
 * and carries the harvest to a storehouse ({@link FarmerWorkGoal}). Far from players it brings in a harvest by
 * formula from the plots counted the last time its farm was seen.
 */
public final class FarmerBehavior implements ProfessionBehavior {
    /** Rows above and below the composter that belong to the farm. */
    static final int FARM_DEPTH = 2;

    @Override
    public boolean isWorkplace(BlockState state) {
        return state.is(Blocks.COMPOSTER);
    }

    @Override
    public Goal createWork(CitizenEntity body) {
        return new FarmerWorkGoal(body);
    }

    @Override
    public void workAbstractly(MinecraftServer server, Citizen citizen, int intervalTicks) {
        if (citizen.workplace() == null || citizen.workCapacity() <= 0 || citizen.workProduct().isEmpty()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(citizen.workProduct());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            return;
        }
        double progress = citizen.workProgress() + CitizenNeeds.farmOutput(citizen.workCapacity(), intervalTicks);
        int whole = (int) Math.min(Math.floor(progress), 64.0 * 9);
        citizen.setWorkProgress(progress - whole);
        int left = whole;
        while (left > 0) {
            int count = Math.min(left, item.getMaxStackSize(new ItemStack(item)));
            // What the storehouses cannot take rots in the fields.
            KingdomStorage.insert(server, citizen.kingdomId(), new ItemStack(item, count), citizen.workplace());
            left -= count;
        }
        citizen.addExperience(whole);
    }

    @Override
    public void survey(ServerLevel level, Citizen citizen) {
        WorldPos workplace = citizen.workplace();
        if (workplace == null || !workplace.dimension().equals(Dimensions.id(level))) {
            return;
        }
        Survey survey = survey(level, citizen.kingdomId(), new BlockPos(workplace.x(), workplace.y(), workplace.z()),
                CitizenSettings.rules().farmRadius());
        if (survey.plots() > 0) {
            citizen.rememberWork(survey.plots(), survey.product());
        }
    }

    /**
     * What a farm looks like: how many crops grow there and what they mostly give.
     *
     * @param product item id of the main product, empty if unknown
     */
    record Survey(int plots, String product) {
    }

    /**
     * Counts the crops around a composter on the kingdom's land and finds out what they give.
     */
    static Survey survey(ServerLevel level, UUID kingdomId, BlockPos center, int radius) {
        String dimension = Dimensions.id(level);
        Map<Item, Integer> products = new HashMap<>();
        int plots = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!kingdomId.equals(KingdomWorldApi.state(level.getServer()).territory()
                        .ownerOf(dimension, ChunkKey.of(x >> 4, z >> 4)))) {
                    continue;
                }
                for (int dy = -FARM_DEPTH; dy <= FARM_DEPTH; dy++) {
                    pos.set(x, center.getY() + dy, z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!FarmCrops.isCrop(state)) {
                        continue;
                    }
                    plots++;
                    BlockState ripe = state.getBlock() instanceof CropBlock crop ? crop.getStateForAge(crop.getMaxAge()) : state;
                    // A sample of the crops is enough to know what the farm gives.
                    if (plots <= 16) {
                        List<ItemStack> drops = FarmCrops.drops(level, pos.immutable(), ripe, null);
                        Item product = FarmCrops.product(drops, ripe, level, pos.immutable());
                        if (product != null) {
                            products.merge(product, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        Item best = null;
        int bestCount = 0;
        for (Map.Entry<Item, Integer> entry : products.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        ResourceLocation id = best == null ? null : ForgeRegistries.ITEMS.getKey(best);
        return new Survey(plots, id == null ? "" : id.toString());
    }
}
