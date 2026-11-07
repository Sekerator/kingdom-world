package io.github.sekerator.kingdomworld.common.companion;

import io.github.sekerator.kingdomworld.KingdomWorld;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The body of a companion and the window of its pack.
 */
public final class CompanionRegistries {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomWorld.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, KingdomWorld.MOD_ID);

    /**
     * The body of a companion. Never saved with chunks: the records in the companion data are what persists.
     */
    public static final RegistryObject<EntityType<CompanionEntity>> COMPANION = ENTITY_TYPES.register("companion",
            () -> EntityType.Builder.<CompanionEntity>of(CompanionEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(KingdomWorld.MOD_ID + ":companion"));
    /** The pack of a companion next to the owner's inventory. */
    public static final RegistryObject<MenuType<CompanionMenu>> MENU = MENUS.register("companion",
            () -> IForgeMenuType.create(CompanionMenu::fromNetwork));

    private CompanionRegistries() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(CompanionRegistries::onEntityAttributes);
    }

    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(COMPANION.get(), CompanionEntity.createAttributes().build());
    }
}
