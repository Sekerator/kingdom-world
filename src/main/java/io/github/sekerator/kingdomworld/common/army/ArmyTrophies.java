package io.github.sekerator.kingdomworld.common.army;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenBodies;
import io.github.sekerator.kingdomworld.common.citizen.CitizenEntity;
import io.github.sekerator.kingdomworld.common.diplomacy.WarWork;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.kingdom.Dimensions;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.law.LawPunish;
import io.github.sekerator.kingdomworld.common.storage.KingdomStorage;
import io.github.sekerator.kingdomworld.common.storage.StorehouseInventory;
import io.github.sekerator.kingdomworld.common.trade.Purses;
import io.github.sekerator.kingdomworld.core.army.TrophyRules;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trophies of war. A soldier who kills a soldier of another kingdom gathers the gear that fell and carries it to a storehouse
 * of its kingdom; if it is killed on the way, the gear falls again and may be taken back. Brought in, the gear is kept as a
 * trophy with the kingdom it belonged to. After peace that kingdom may buy it back for a share of its worth (by the books of
 * both treasuries) within some days; what it does not buy back goes to the storehouses of the kingdom that took it.
 */
public final class ArmyTrophies {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "kingdomworld.army.trophy.";
    /** Ticks after a death within which its gear is gathered. */
    private static final long GATHER_TICKS = 400;
    /** Blocks around the place of a death where its gear is looked for. */
    private static final double GATHER_RADIUS = 8.0;
    /** Blocks within which a soldier takes an item. */
    private static final double REACH = 3.0;
    /** Blocks from a storehouse within which a carrier hands the trophies in. */
    private static final double HAND_IN = 6.0;

    /** Gear to gather: the killer, then what fell and where. */
    private static final Map<UUID, Gathering> GATHERING = new HashMap<>();
    /** Soldiers carrying trophies, and the kingdom the gear belonged to. */
    private static final Map<UUID, UUID> CARRIERS = new HashMap<>();
    @Nullable
    private static TrophyRules override;

    private record Gathering(UUID owner, String dimension, double x, double y, double z, long until) {
    }

    private ArmyTrophies() {
    }

    public static void reset() {
        GATHERING.clear();
        CARRIERS.clear();
        override = null;
    }

    public static TrophyRules rules() {
        if (override != null) {
            return override;
        }
        try {
            return new TrophyRules(io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig.SERVER.army.trophyBuybackPercent.get(),
                    io.github.sekerator.kingdomworld.common.config.KingdomWorldConfig.SERVER.army.trophyBuybackDays.get());
        } catch (RuntimeException e) {
            return TrophyRules.DEFAULT;
        }
    }

    /**
     * Replaces the rules of trophies, for game tests; {@code null} restores the config.
     */
    public static void override(@Nullable TrophyRules rules) {
        override = rules;
    }

    /**
     * Whether a soldier carries trophies.
     */
    public static boolean carrying(UUID citizenId) {
        return CARRIERS.containsKey(citizenId);
    }

    // ---------------------------------------------------------------- the field

    /**
     * A body died. A soldier killed by a soldier of another kingdom leaves its gear (and any trophies it carried) to be
     * gathered by its killer.
     */
    public static void onDeath(MinecraftServer server, CitizenEntity victim, @Nullable CitizenEntity killer) {
        UUID carried = victim.citizenId() == null ? null : CARRIERS.remove(victim.citizenId());
        if (killer == null || killer.citizenId() == null || victim.kingdomId() == null || killer.kingdomId() == null
                || killer.kingdomId().equals(victim.kingdomId()) || !Professions.isMilitary(killer.professionId())
                || !(Professions.isMilitary(victim.professionId()) || carried != null)) {
            return;
        }
        UUID owner = Professions.isMilitary(victim.professionId()) ? victim.kingdomId() : carried;
        GATHERING.put(killer.citizenId(), new Gathering(owner, Dimensions.id(victim.level()), victim.getX(), victim.getY(), victim.getZ(),
                KingdomWorldApi.gameTime(server) + GATHER_TICKS));
    }

    /**
     * Every few ticks: killers gather the gear that fell, carriers near a storehouse of their kingdom hand the trophies in.
     */
    public static void tick(MinecraftServer server) {
        long now = KingdomWorldApi.gameTime(server);
        for (Iterator<Map.Entry<UUID, Gathering>> it = GATHERING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Gathering> entry = it.next();
            CitizenEntity killer = CitizenBodies.body(entry.getKey());
            Gathering gathering = entry.getValue();
            if (killer == null || !killer.isAlive() || now > gathering.until() || !Dimensions.id(killer.level()).equals(gathering.dimension())) {
                it.remove();
                continue;
            }
            AABB area = new AABB(gathering.x() - GATHER_RADIUS, gathering.y() - 3, gathering.z() - GATHER_RADIUS, gathering.x() + GATHER_RADIUS,
                    gathering.y() + 3, gathering.z() + GATHER_RADIUS);
            List<ItemEntity> fallen = killer.level().getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive() && !item.getItem().isEmpty());
            for (ItemEntity item : fallen) {
                if (item.distanceTo(killer) > REACH) {
                    killer.getNavigation().moveTo(item, 1.0);
                    break;
                }
                ItemStack left = killer.carried().addItem(item.getItem().copy());
                CARRIERS.put(killer.citizenId(), gathering.owner());
                if (left.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(left);
                    break;
                }
            }
        }
        for (UUID carrier : List.copyOf(CARRIERS.keySet())) {
            CitizenEntity body = CitizenBodies.body(carrier);
            if (body == null || body.kingdomId() == null) {
                continue;
            }
            WorldPos store = storehouse(server, body);
            if (store != null && body.distanceToSqr(store.x() + 0.5, store.y() + 0.5, store.z() + 0.5) <= HAND_IN * HAND_IN) {
                handIn(server, body);
            }
        }
    }

    /**
     * The storehouse a carrier walks to, or {@code null} for a soldier without trophies.
     */
    @Nullable
    public static WorldPos deliverTo(MinecraftServer server, CitizenEntity body) {
        if (body.citizenId() == null || !CARRIERS.containsKey(body.citizenId()) || GATHERING.containsKey(body.citizenId())) {
            return null;
        }
        return storehouse(server, body);
    }

    @Nullable
    private static WorldPos storehouse(MinecraftServer server, CitizenEntity body) {
        WorldPos at = new WorldPos(Dimensions.id(body.level()), body.getBlockX(), body.getBlockY(), body.getBlockZ());
        List<StorehouseInventory> stores = KingdomStorage.storehouses(server, body.kingdomId(), at);
        return stores.isEmpty() ? null : stores.get(0).pos();
    }

    /**
     * A body of a carrier leaves the world far from players: it brings the trophies home on its own.
     */
    public static void unloaded(MinecraftServer server, CitizenEntity body) {
        if (body.citizenId() != null && body.isAlive() && body.getHealth() > 0.0f && CARRIERS.containsKey(body.citizenId())) {
            handIn(server, body);
        }
    }

    /**
     * A carrier hands its trophies in: they are kept with the kingdom they belonged to.
     */
    static void handIn(MinecraftServer server, CitizenEntity body) {
        UUID owner = CARRIERS.remove(body.citizenId());
        if (owner == null || body.kingdomId() == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < body.carried().getContainerSize(); slot++) {
            ItemStack stack = body.carried().removeItemNoUpdate(slot);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        if (items.isEmpty()) {
            return;
        }
        UUID holder = body.kingdomId();
        if (owner.equals(holder)) {
            // Gear of one's own kingdom taken back is no trophy: it goes to the storehouses.
            WorldPos near = new WorldPos(Dimensions.id(body.level()), body.getBlockX(), body.getBlockY(), body.getBlockZ());
            for (ItemStack stack : items) {
                ItemStack left = KingdomStorage.insert(server, holder, stack, near);
                if (!left.isEmpty()) {
                    body.spawnAtLocation(left);
                }
            }
            return;
        }
        long worth = LawPunish.worth(server, holder, items);
        TrophySavedData.Trophy trophy = TrophySavedData.get(server).add(owner, holder, worth, atWar(server, owner, holder) ? 0 : day(server), items);
        Optional<Kingdom> kingdom = KingdomWorldApi.kingdom(server, holder);
        kingdom.ifPresent(found -> KingdomActions.notifyMembers(server, found, Permission.MANAGE_ARMY, Component.translatable(KEY + "brought",
                trophy.count(), WarWork.name(server, owner)).withStyle(ChatFormatting.GOLD)));
        LOGGER.info("Trophy #{}: {} item(s) of {} brought to {}", trophy.id(), trophy.count(), owner, holder);
    }

    private static boolean atWar(MinecraftServer server, UUID a, UUID b) {
        return !WarWork.isReadOnly(server) && WarWork.state(server).warBetween(a, b).isPresent();
    }

    private static long day(MinecraftServer server) {
        return TreasuryActions.day(server);
    }

    // ---------------------------------------------------------------- after the war

    /**
     * Once a second: trophies of wars that ended get their day of peace (and the kingdom they belonged to hears it may buy
     * them back); trophies whose time is over go to the storehouses of the kingdom that took them.
     */
    public static void daily(MinecraftServer server) {
        TrophySavedData data = TrophySavedData.get(server);
        if (data.all().isEmpty() || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        long today = day(server);
        TrophyRules rules = rules();
        for (TrophySavedData.Trophy trophy : List.copyOf(data.all())) {
            boolean ownerGone = KingdomWorldApi.kingdom(server, trophy.owner()).isEmpty();
            boolean holderGone = KingdomWorldApi.kingdom(server, trophy.holder()).isEmpty();
            if (holderGone && ownerGone) {
                data.remove(trophy.id());
                continue;
            }
            if (holderGone) {
                // Nobody holds the gear any more: it goes home for nothing.
                store(server, data, trophy, trophy.owner());
                continue;
            }
            if (ownerGone || rules.expired(trophy.peaceDay(), today)) {
                store(server, data, trophy, trophy.holder());
                continue;
            }
            if (trophy.peaceDay() == 0 && !atWar(server, trophy.owner(), trophy.holder())) {
                data.put(trophy.withPeaceDay(today));
                announce(server, trophy, rules, today);
            }
        }
    }

    private static void store(MinecraftServer server, TrophySavedData data, TrophySavedData.Trophy trophy, UUID kingdom) {
        List<ItemStack> rest = new ArrayList<>();
        WorldPos near = ArmyWork.capitalOf(server, kingdom);
        for (ItemStack stack : trophy.items()) {
            ItemStack left = KingdomStorage.insert(server, kingdom, stack.copy(), near);
            if (!left.isEmpty()) {
                rest.add(left);
            }
        }
        if (rest.isEmpty()) {
            data.remove(trophy.id());
        } else {
            // The storehouses are full: the rest waits for room.
            data.put(trophy.withItems(rest));
        }
    }

    private static void announce(MinecraftServer server, TrophySavedData.Trophy trophy, TrophyRules rules, long today) {
        Optional<Kingdom> owner = KingdomWorldApi.kingdom(server, trophy.owner());
        if (owner.isEmpty()) {
            return;
        }
        long price = rules.price(trophy.worth());
        MutableComponent buy = Component.translatable(KEY + "buy_button").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kingdom trophies buy " + trophy.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/kingdom trophies buy " + trophy.id()))));
        KingdomActions.notifyMembers(server, owner.get(), Permission.MANAGE_TREASURY, Component.translatable(KEY + "offer", trophy.count(),
                WarWork.name(server, trophy.holder()), MoneyTexts.amount(price, Purses.units(server, trophy.owner())), today + rules.days())
                .withStyle(ChatFormatting.GOLD).append(" ").append(buy));
    }

    /**
     * The trophies a kingdom may buy back now, and those it holds of others.
     */
    public static List<TrophySavedData.Trophy> of(MinecraftServer server, UUID kingdomId) {
        List<TrophySavedData.Trophy> list = new ArrayList<>();
        for (TrophySavedData.Trophy trophy : TrophySavedData.get(server).all()) {
            if (trophy.owner().equals(kingdomId) || trophy.holder().equals(kingdomId)) {
                list.add(trophy);
            }
        }
        return list;
    }

    /**
     * The kingdom of a player buys back its gear: the price goes from its treasury to the treasury of the holder, the gear
     * to its storehouses (what does not fit, to the player).
     */
    public static KingdomActions.Feedback buy(ServerPlayer player, long id) {
        MinecraftServer server = player.server;
        TrophySavedData data = TrophySavedData.get(server);
        Optional<TrophySavedData.Trophy> found = data.trophy(id);
        if (found.isEmpty()) {
            return KingdomActions.Feedback.fail(Component.translatable(KEY + "unknown", id));
        }
        TrophySavedData.Trophy trophy = found.get();
        if (!KingdomWorldApi.state(server).membership().hasPermission(player.getUUID(), trophy.owner(), Permission.MANAGE_TREASURY)) {
            return KingdomActions.Feedback.error(KingdomError.NO_PERMISSION);
        }
        TrophyRules rules = rules();
        long today = day(server);
        if (!rules.open(trophy.peaceDay(), today)) {
            return KingdomActions.Feedback.fail(Component.translatable(KEY + (trophy.peaceDay() == 0 ? "at_war" : "too_late")));
        }
        long price = rules.price(trophy.worth());
        Outcome paid = WarWork.pay(server, trophy.owner(), trophy.holder(), price, today);
        if (!paid.ok()) {
            return KingdomActions.Feedback.error(paid.error());
        }
        data.remove(trophy.id());
        WorldPos near = ArmyWork.capitalOf(server, trophy.owner());
        for (ItemStack stack : trophy.items()) {
            ItemStack left = KingdomStorage.insert(server, trophy.owner(), stack.copy(), near);
            if (!left.isEmpty() && !player.getInventory().add(left)) {
                player.drop(left, false);
            }
        }
        KingdomWorldApi.kingdom(server, trophy.holder()).ifPresent(holder -> KingdomActions.notifyMembers(server, holder, Permission.MANAGE_TREASURY,
                Component.translatable(KEY + "sold", WarWork.name(server, trophy.owner()), MoneyTexts.amount(price, Purses.units(server, trophy.holder())))
                        .withStyle(ChatFormatting.GOLD)));
        LOGGER.info("Trophy #{} bought back by {} for {}", trophy.id(), trophy.owner(), price);
        return KingdomActions.Feedback.ok(KEY + "bought", trophy.count(), MoneyTexts.amount(price, Purses.units(server, trophy.owner())));
    }
}
