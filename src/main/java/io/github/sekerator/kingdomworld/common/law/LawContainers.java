package io.github.sekerator.kingdomworld.common.law;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.BuildingRoles;
import io.github.sekerator.kingdomworld.core.law.CrimeKind;
import io.github.sekerator.kingdomworld.core.law.Victims;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Taking from another's container on the land of a kingdom. Opening is no longer refused: while the container is open the
 * server compares what lies in it with what lay there, and what the player took (not what was moved about or put in) is a
 * theft by its worth. Thefts close together make one crime. Containers a player placed on the land belong to that player.
 * The record names whose goods were taken: a citizen's from the chest of its house, the treasury's from a storehouse (fined
 * higher, and noticed at once by a soldier near the storehouse).
 */
final class LawContainers {
    /** Where the owner of a container a player placed on the land of a kingdom is kept. */
    static final String OWNER = "kingdomworld_owner";
    /** Ticks between a click on a container and its window opening. */
    private static final long OPEN_TICKS = 5;

    private record Pending(BlockPos pos, @Nullable UUID owner, long at) {
    }

    private static final class Open {
        final AbstractContainerMenu menu;
        final BlockPos pos;
        final UUID owner;
        Map<String, Integer> counts;
        final Map<String, ItemStack> samples = new HashMap<>();

        Open(AbstractContainerMenu menu, BlockPos pos, UUID owner) {
            this.menu = menu;
            this.pos = pos;
            this.owner = owner;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Open> OPEN = new HashMap<>();

    private LawContainers() {
    }

    static void reset() {
        PENDING.clear();
        OPEN.clear();
    }

    /**
     * The player leaves: one last look at a watched window, so that what was taken just before is a theft all the same.
     */
    static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        Open open = OPEN.remove(player.getUUID());
        if (open != null) {
            check(player, open);
        }
    }

    /**
     * The player who placed a container on the land of a kingdom, if one did.
     */
    @Nullable
    static UUID ownerOf(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return null;
        }
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(OWNER) ? data.getUUID(OWNER) : null;
    }

    /**
     * Notes the player who placed a container on the land of a kingdom.
     */
    static void markOwner(Level level, BlockPos pos, UUID player) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null) {
            entity.getPersistentData().putUUID(OWNER, player);
            entity.setChanged();
        }
    }

    /**
     * A player without the right to containers is about to open one on the land of a kingdom.
     */
    static void aboutToOpen(ServerPlayer player, BlockPos pos, @Nullable UUID owner) {
        PENDING.put(player.getUUID(), new Pending(pos.immutable(), owner, LawWork.now(player.server)));
    }

    /**
     * A window opened: if the player clicked a foreign container a moment ago, its contents are watched.
     */
    static void opened(ServerPlayer player, AbstractContainerMenu menu) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || LawWork.now(player.server) - pending.at() > OPEN_TICKS) {
            return;
        }
        Open open = new Open(menu, pending.pos(), pending.owner());
        open.counts = count(open, player);
        OPEN.put(player.getUUID(), open);
    }

    static void closed(ServerPlayer player, AbstractContainerMenu menu) {
        Open open = OPEN.get(player.getUUID());
        if (open != null && open.menu == menu) {
            check(player, open);
            OPEN.remove(player.getUUID());
        }
    }

    /**
     * Looks at every watched window: what was taken since the last look is a theft.
     */
    static void tick(MinecraftServer server) {
        for (Map.Entry<UUID, Open> entry : List.copyOf(OPEN.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.containerMenu != entry.getValue().menu) {
                OPEN.remove(entry.getKey());
                if (player != null) {
                    // The window closed without an event (a death, another window): one last look.
                    check(player, entry.getValue());
                }
                continue;
            }
            check(player, entry.getValue());
        }
    }

    private static void check(ServerPlayer player, Open open) {
        Map<String, Integer> now = count(open, player);
        List<ItemStack> taken = new ArrayList<>();
        for (Map.Entry<String, Integer> before : open.counts.entrySet()) {
            int left = now.getOrDefault(before.getKey(), 0);
            if (left < before.getValue()) {
                ItemStack sample = open.samples.get(before.getKey());
                if (sample != null) {
                    taken.add(sample.copyWithCount(before.getValue() - left));
                }
            }
        }
        open.counts = now;
        if (taken.isEmpty()) {
            return;
        }
        UUID kingdom = KingdomWorldApi.ownerAt(player.level(), open.pos);
        if (kingdom == null) {
            return;
        }
        long worth = LawPunish.worth(player.server, kingdom, taken);
        boolean owner = open.owner != null && open.owner.equals(player.getUUID());
        String victim = open.owner != null ? "" : victimOf(player, open.pos, kingdom);
        LawWork.deed(player, open.pos, kingdom, CrimeKind.THEFT, worth, victim, Permission.CONTAINERS, owner, null);
    }

    /**
     * Whose goods a container of a kingdom holds: the treasury's in a storehouse (the storehouse block, or a chest of a
     * warehouse), a citizen's in the house the citizen lives in, nobody's in particular anywhere else.
     */
    static String victimOf(ServerPlayer player, BlockPos pos, UUID kingdom) {
        Level level = player.level();
        MinecraftServer server = player.server;
        if (level.getBlockState(pos).is(io.github.sekerator.kingdomworld.common.registry.ModRegistries.STOREHOUSE.get())) {
            return Victims.TREASURY;
        }
        String dimension = io.github.sekerator.kingdomworld.common.kingdom.Dimensions.id(level);
        for (Building building : KingdomWorldApi.state(server).construction().buildingsOf(kingdom)) {
            if (!building.dimension().equals(dimension) || !building.box().contains(pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }
            if (BuildingRoles.WAREHOUSE.equals(building.role())) {
                return Victims.TREASURY;
            }
            if (BuildingRoles.HOUSE.equals(building.role())) {
                return io.github.sekerator.kingdomworld.common.economy.Households.residentOf(server, building)
                        .map(citizen -> Victims.citizen(citizen.nameSeed(),
                                citizen.gender() == io.github.sekerator.kingdomworld.core.citizen.Gender.FEMALE)).orElse("");
            }
            return "";
        }
        return "";
    }

    /**
     * What lies in the container part of a window, by item and data.
     */
    private static Map<String, Integer> count(Open open, ServerPlayer player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : open.menu.slots) {
            if (slot.container == player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + (stack.hasTag() ? stack.getTag().toString() : "");
            counts.merge(key, stack.getCount(), Integer::sum);
            open.samples.putIfAbsent(key, stack.copyWithCount(1));
        }
        return counts;
    }
}
