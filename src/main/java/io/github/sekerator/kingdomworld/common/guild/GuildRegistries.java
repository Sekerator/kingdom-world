package io.github.sekerator.kingdomworld.common.guild;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blocks and items of the adventurers' guild.
 */
public final class GuildRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomWorld.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomWorld.MOD_ID);

    /** A desk of the guild: open while a registrar keeps it. */
    public static final RegistryObject<Block> GUILD_DESK = BLOCKS.register("guild_desk", () -> new GuildDeskBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));
    public static final RegistryObject<Item> GUILD_DESK_ITEM = ITEMS.register("guild_desk",
            () -> new BlockItem(GUILD_DESK.get(), new Item.Properties()));
    /** A crystal of a monster; its rank is in the stack's data. */
    public static final RegistryObject<Item> MONSTER_CRYSTAL = ITEMS.register("monster_crystal",
            () -> new MonsterCrystalItem(new Item.Properties()));

    private GuildRegistries() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(GuildRegistries::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(GUILD_DESK_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (Rank rank : Rank.values()) {
                event.accept(MonsterCrystalItem.stack(rank, 1));
            }
        }
    }
}
