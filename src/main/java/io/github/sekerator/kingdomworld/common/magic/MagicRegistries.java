package io.github.sekerator.kingdomworld.common.magic;

import io.github.sekerator.kingdomworld.KingdomWorld;
import io.github.sekerator.kingdomworld.core.magic.MagicDevice;
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
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blocks, items and the fireball of the magic of kingdoms.
 */
public final class MagicRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomWorld.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomWorld.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,
            KingdomWorld.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomWorld.MOD_ID);

    private static BlockBehaviour.Properties stone(int light) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0f, 6.0f).sound(SoundType.AMETHYST)
                .lightLevel(state -> light);
    }

    /** The heart of the mage tower: mages dissolve crystals here, players learn spells. */
    public static final RegistryObject<Block> MAGIC_FOCUS = BLOCKS.register("magic_focus",
            () -> new MagicDeviceBlock(MagicDevice.Kind.FOCUS, stone(10)));
    /** More room for the kingdom's energy while it stands on the kingdom's land. */
    public static final RegistryObject<Block> CRYSTAL_ACCUMULATOR = BLOCKS.register("crystal_accumulator",
            () -> new MagicDeviceBlock(MagicDevice.Kind.ACCUMULATOR, stone(6)));
    /** Gives the kingdom's energy to machines as Forge Energy. */
    public static final RegistryObject<Block> CRYSTAL_GENERATOR = BLOCKS.register("crystal_generator",
            () -> new CrystalGeneratorBlock(stone(8)));
    /** Subjects travel between the gates of their kingdom. */
    public static final RegistryObject<Block> MAGIC_GATE = BLOCKS.register("magic_gate",
            () -> new MagicDeviceBlock(MagicDevice.Kind.GATE, stone(12).noOcclusion()));

    public static final RegistryObject<Item> MAGIC_FOCUS_ITEM = ITEMS.register("magic_focus", () -> new BlockItem(MAGIC_FOCUS.get(),
            new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CRYSTAL_ACCUMULATOR_ITEM = ITEMS.register("crystal_accumulator",
            () -> new BlockItem(CRYSTAL_ACCUMULATOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> CRYSTAL_GENERATOR_ITEM = ITEMS.register("crystal_generator",
            () -> new BlockItem(CRYSTAL_GENERATOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> MAGIC_GATE_ITEM = ITEMS.register("magic_gate", () -> new BlockItem(MAGIC_GATE.get(),
            new Item.Properties().rarity(Rarity.UNCOMMON)));
    /** A staff with slots for spells. */
    public static final RegistryObject<Item> STAFF = ITEMS.register("staff", () -> new StaffItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<CrystalGeneratorBlockEntity>> GENERATOR_ENTITY = BLOCK_ENTITIES.register(
            "crystal_generator", () -> BlockEntityType.Builder.of(CrystalGeneratorBlockEntity::new, CRYSTAL_GENERATOR.get()).build(null));

    public static final RegistryObject<EntityType<SpellFireball>> SPELL_FIREBALL = ENTITIES.register("spell_fireball",
            () -> EntityType.Builder.<SpellFireball>of(SpellFireball::new, MobCategory.MISC).sized(0.3125f, 0.3125f)
                    .clientTrackingRange(4).updateInterval(10).build(KingdomWorld.id("spell_fireball").toString()));

    private MagicRegistries() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(MagicRegistries::onBuildCreativeTabs);
    }

    /**
     * The registry name of the block of a kind of device.
     */
    public static String blockId(MagicDevice.Kind kind) {
        return switch (kind) {
            case FOCUS -> "magic_focus";
            case ACCUMULATOR -> "crystal_accumulator";
            case GENERATOR -> "crystal_generator";
            case GATE -> "magic_gate";
        };
    }

    public static Block block(MagicDevice.Kind kind) {
        return switch (kind) {
            case FOCUS -> MAGIC_FOCUS.get();
            case ACCUMULATOR -> CRYSTAL_ACCUMULATOR.get();
            case GENERATOR -> CRYSTAL_GENERATOR.get();
            case GATE -> MAGIC_GATE.get();
        };
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MAGIC_FOCUS_ITEM);
            event.accept(CRYSTAL_ACCUMULATOR_ITEM);
            event.accept(CRYSTAL_GENERATOR_ITEM);
            event.accept(MAGIC_GATE_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(STAFF);
        }
    }
}
