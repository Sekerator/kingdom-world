package io.github.sekerator.kingdomworld.common.production;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.PostWork;
import io.github.sekerator.kingdomworld.core.citizen.ProductionLedger;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which gathering profession brings an item, for orders that miss it ("raw iron is missing, miners bring it"). The
 * kingdom's own record of production is asked first, so that whatever its workers gathered lately (also items of
 * mods) is known; otherwise the item's tags tell: ore and stone come from miners, logs from lumberjacks, fish from
 * fishers, crops from farmers, meat, wool and leather from herders.
 */
public final class Gatherers {
    private static final List<String> GATHERING = List.of(Professions.MINER.id(), Professions.LUMBERJACK.id(), Professions.FISHER.id(),
            Professions.HERDER.id(), Professions.FARMER.id());

    private Gatherers() {
    }

    /**
     * The profession that gathers the item, {@code ""} if none does.
     */
    public static String of(MinecraftServer server, UUID kingdomId, String itemId) {
        long day = PostWork.gameDay(server);
        ProductionLedger ledger = KingdomWorldApi.state(server).citizens().ledger();
        for (ProductionLedger.Day record : List.of(ledger.today(kingdomId, day), ledger.yesterday(kingdomId, day))) {
            for (String profession : GATHERING) {
                if (record.counts().getOrDefault(profession, Map.of()).getOrDefault(itemId, 0L) > 0) {
                    return profession;
                }
            }
        }
        Item item = Recipes.item(itemId);
        return item == null ? "" : byTags(new ItemStack(item));
    }

    static String byTags(ItemStack stack) {
        if (stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.STONE) || stack.is(Tags.Items.COBBLESTONE)
                || stack.is(Tags.Items.GRAVEL) || stack.is(Tags.Items.SAND) || stack.is(ItemTags.COALS) || stack.is(Tags.Items.GEMS)
                || stack.is(Tags.Items.DUSTS_REDSTONE) || stack.is(Items.FLINT) || stack.is(Items.CLAY_BALL)
                || stack.is(Items.DIRT)) {
            return Professions.MINER.id();
        }
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.SAPLINGS) || stack.is(Items.APPLE)) {
            return Professions.LUMBERJACK.id();
        }
        if (stack.is(ItemTags.FISHES) || stack.is(Items.INK_SAC) || stack.is(Items.NAUTILUS_SHELL)) {
            return Professions.FISHER.id();
        }
        if (stack.is(Tags.Items.LEATHER) || stack.is(ItemTags.WOOL) || stack.is(Tags.Items.FEATHERS) || stack.is(Tags.Items.EGGS)
                || stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON) || stack.is(Items.CHICKEN)
                || stack.is(Items.RABBIT) || stack.is(Items.RABBIT_HIDE)) {
            return Professions.HERDER.id();
        }
        if (stack.is(Tags.Items.CROPS) || stack.is(Tags.Items.SEEDS) || stack.is(Items.SUGAR_CANE) || stack.is(Items.MELON_SLICE)
                || stack.is(Items.PUMPKIN) || stack.is(Items.CACTUS) || stack.is(Items.BAMBOO)) {
            return Professions.FARMER.id();
        }
        return "";
    }
}
