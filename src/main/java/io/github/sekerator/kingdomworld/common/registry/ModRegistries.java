package io.github.sekerator.kingdomworld.common.registry;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.common.block.InfirmaryBlock;
import io.github.sekerator.kingdomworld.common.block.ThroneBlock;
import io.github.sekerator.kingdomworld.common.block.WorkPostBlock;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.economy.BankBlock;
import io.github.sekerator.kingdomworld.common.economy.CoinItem;
import io.github.sekerator.kingdomworld.common.economy.MintBlock;
import io.github.sekerator.kingdomworld.common.item.DescribedItem;
import io.github.sekerator.kingdomworld.common.item.KingdomAtlasItem;
import io.github.sekerator.kingdomworld.common.storage.StorehouseBlock;
import io.github.sekerator.kingdomworld.common.storage.StorehouseBlockEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
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
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, KingdomWorld.MOD_ID);

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
            () -> new DescribedItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                    "item.kingdomworld.founder_crown.tooltip"));
    /** Opens the political map of kingdoms. */
    public static final RegistryObject<Item> KINGDOM_ATLAS = ITEMS.register("kingdom_atlas",
            () -> new KingdomAtlasItem(new Item.Properties().stacksTo(1)));

    /** Storehouse of a kingdom: a large chest whose contents the kingdom's citizens use. */
    public static final RegistryObject<Block> STOREHOUSE = BLOCKS.register("storehouse", () -> new StorehouseBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Item> STOREHOUSE_ITEM = ITEMS.register("storehouse",
            () -> new BlockItem(STOREHOUSE.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<StorehouseBlockEntity>> STOREHOUSE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "storehouse", () -> BlockEntityType.Builder.of(StorehouseBlockEntity::new, STOREHOUSE.get()).build(null));
    /** Wounded citizens heal faster here. */
    public static final RegistryObject<Block> INFIRMARY = BLOCKS.register("infirmary", () -> new InfirmaryBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .strength(1.5f)
                    .sound(SoundType.WOOD)));
    public static final RegistryObject<Item> INFIRMARY_ITEM = ITEMS.register("infirmary",
            () -> new BlockItem(INFIRMARY.get(), new Item.Properties()));
    /** Workplace of the gathering professions. */
    public static final RegistryObject<Block> WORK_POST = BLOCKS.register("work_post", () -> new WorkPostBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)));
    public static final RegistryObject<Item> WORK_POST_ITEM = ITEMS.register("work_post",
            () -> new BlockItem(WORK_POST.get(), new Item.Properties()));

    /** A coin of a kingdom's currency; the stack's data names the currency and the kind of coin. */
    public static final RegistryObject<Item> COIN = ITEMS.register("coin", () -> new CoinItem(new Item.Properties()));
    /** A bank: coins onto accounts and back, in the currency of the kingdom whose land it stands on. */
    public static final RegistryObject<Block> BANK = BLOCKS.register("bank", () -> new BankBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));
    public static final RegistryObject<Item> BANK_ITEM = ITEMS.register("bank",
            () -> new BlockItem(BANK.get(), new Item.Properties()));
    /** The mint, workstation of the minter. */
    public static final RegistryObject<Block> MINT = BLOCKS.register("mint", () -> new MintBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));
    public static final RegistryObject<Item> MINT_ITEM = ITEMS.register("mint",
            () -> new BlockItem(MINT.get(), new Item.Properties()));
    /** A market stall: the market of the kingdom whose land it stands on, open while a merchant keeps it. */
    public static final RegistryObject<Block> MARKET_STALL = BLOCKS.register("market_stall",
            () -> new io.github.sekerator.kingdomworld.common.trade.MarketStallBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)));
    public static final RegistryObject<Item> MARKET_STALL_ITEM = ITEMS.register("market_stall",
            () -> new BlockItem(MARKET_STALL.get(), new Item.Properties()));
    /** A blueprint of a building template, taken in the construction tab of the throne; it lays out constructions. */
    public static final RegistryObject<Item> BLUEPRINT = ITEMS.register("blueprint",
            () -> new io.github.sekerator.kingdomworld.common.construction.BlueprintItem(new Item.Properties().stacksTo(1)));

    /**
     * The body of a citizen. Never saved with chunks: the citizen records in the kingdom data are what persists.
     */
    public static final RegistryObject<EntityType<CitizenEntity>> CITIZEN = ENTITY_TYPES.register("citizen",
            () -> EntityType.Builder.<CitizenEntity>of(CitizenEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(KingdomWorld.MOD_ID + ":citizen"));

    private ModRegistries() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        io.github.sekerator.kingdomworld.common.guild.GuildRegistries.register(modBus);
        io.github.sekerator.kingdomworld.common.army.ArmyRegistries.register(modBus);
        modBus.addListener(ModRegistries::onBuildCreativeTabs);
        modBus.addListener(ModRegistries::onEntityAttributes);
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(), CitizenEntity.createAttributes().build());
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(THRONE_ITEM);
            event.accept(STOREHOUSE_ITEM);
            event.accept(INFIRMARY_ITEM);
            event.accept(WORK_POST_ITEM);
            event.accept(BANK_ITEM);
            event.accept(MINT_ITEM);
            event.accept(MARKET_STALL_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FOUNDER_CROWN);
            event.accept(KINGDOM_ATLAS);
        }
    }
}
