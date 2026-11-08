package io.github.sekerator.kingdomworld.common.siege;

import io.github.sekerator.kingdomworld.KingdomWorld;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Items of the war in the world.
 */
public final class SiegeRegistries {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KingdomWorld.MOD_ID);

    /** A ram: breaks gates and walls faster, in the hands of players and of soldiers at a siege. */
    public static final RegistryObject<Item> BATTERING_RAM = ITEMS.register("battering_ram",
            () -> new BatteringRamItem(new Item.Properties().stacksTo(1)));

    private SiegeRegistries() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(SiegeRegistries::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(BATTERING_RAM);
        }
    }
}
