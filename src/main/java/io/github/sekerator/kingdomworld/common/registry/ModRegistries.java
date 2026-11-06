package io.github.sekerator.kingdomworld.common.registry;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.block.ThroneBlock;
import io.github.sekerator.kingdomworld.common.item.KingdomAtlasItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Deferred registers for game objects.
 */
public final class ModRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomWorld.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomWorld.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomWorld.MOD_ID);

    /**
     * Decorative seat that becomes the heart of a kingdom when one is founded on it. Immune to explosions and
     * pistons so that the heart cannot be removed by accident.
     */
    public static final RegistryObject<Block> THRONE = BLOCKS.register("throne", () -> new ThroneBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(2.5f, 3_600_000f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Item> THRONE_ITEM = ITEMS.register("throne",
            () -> new BlockItem(THRONE.get(), new Item.Properties()));
    /** Founds a kingdom on an empty throne and is used up doing so. */
    public static final RegistryObject<Item> FOUNDER_CROWN = ITEMS.register("founder_crown",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** Opens the political map of kingdoms. */
    public static final RegistryObject<Item> KINGDOM_ATLAS = ITEMS.register("kingdom_atlas",
            () -> new KingdomAtlasItem(new Item.Properties().stacksTo(1)));

    private ModRegistries() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(ModRegistries::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(THRONE_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FOUNDER_CROWN);
            event.accept(KINGDOM_ATLAS);
        }
    }
}
