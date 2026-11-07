package io.github.sekerator.kingdomworld.common.road;

import io.github.sekerator.kingdomworld.KingdomWorld;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blocks and creatures of cities, roads and robbers. Robbers and merchants of caravans are never saved with chunks: the
 * camps and caravans in the saved data of the roads are what persists, and their bodies come back near players.
 */
public final class RoadRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, KingdomWorld.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomWorld.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomWorld.MOD_ID);

    /** The town hall: founds a city of the kingdom whose land it stands on. */
    public static final RegistryObject<Block> TOWN_HALL = BLOCKS.register("town_hall", () -> new TownHallBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.STONE)));
    public static final RegistryObject<Item> TOWN_HALL_ITEM = ITEMS.register("town_hall",
            () -> new BlockItem(TOWN_HALL.get(), new Item.Properties()));

    public static final RegistryObject<EntityType<BanditEntity>> BANDIT = ENTITIES.register("bandit",
            () -> EntityType.Builder.<BanditEntity>of(BanditEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(KingdomWorld.MOD_ID + ":bandit"));
    public static final RegistryObject<EntityType<BanditEntity>> BANDIT_LEADER = ENTITIES.register("bandit_leader",
            () -> EntityType.Builder.<BanditEntity>of(BanditEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(KingdomWorld.MOD_ID + ":bandit_leader"));
    public static final RegistryObject<EntityType<CaravanMerchantEntity>> CARAVAN_MERCHANT = ENTITIES.register("caravan_merchant",
            () -> EntityType.Builder.<CaravanMerchantEntity>of(CaravanMerchantEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(KingdomWorld.MOD_ID + ":caravan_merchant"));

    private RoadRegistries() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(RoadRegistries::onBuildCreativeTabs);
        modBus.addListener(RoadRegistries::onEntityAttributes);
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(BANDIT.get(), BanditEntity.createAttributes(false).build());
        event.put(BANDIT_LEADER.get(), BanditEntity.createAttributes(true).build());
        event.put(CARAVAN_MERCHANT.get(), CaravanMerchantEntity.createAttributes().build());
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(TOWN_HALL_ITEM);
        }
    }
}
