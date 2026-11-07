package io.github.sekerator.kingdomworld.common.army;

import io.github.sekerator.kingdomworld.KingdomWorld;
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
 * Blocks and items of the army.
 */
public final class ArmyRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomWorld.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomWorld.MOD_ID);

    /** The barracks: soldiers of the kingdom train here. */
    public static final RegistryObject<Block> BARRACKS = BLOCKS.register("barracks", () -> new BarracksBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)));
    public static final RegistryObject<Item> BARRACKS_ITEM = ITEMS.register("barracks",
            () -> new BlockItem(BARRACKS.get(), new Item.Properties()));
    /** The marshal's baton: chooses a squad, gives it a point, a route or an area, and shows where the squads are. */
    public static final RegistryObject<Item> MARSHAL_BATON = ITEMS.register("marshal_baton",
            () -> new MarshalBatonItem(new Item.Properties().stacksTo(1)));

    private ArmyRegistries() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(ArmyRegistries::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(BARRACKS_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(MARSHAL_BATON);
        }
    }
}
