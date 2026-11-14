package io.github.sekerator.kingdomworld.common.data;

import io.github.sekerator.kingdomworld.common.flag.FlagNbt;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.npc.ArchitecturalStyles;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import io.github.sekerator.kingdomworld.core.territory.ThroneMoves;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * NBT form of a kingdom with its land, members, rights, pending requests, population, orders and treasury (format 7; trade is kept at the root).
 *
 * <pre>
 * { id, name, color, ruler?, createdGameTime, style?, flag?: see {@link FlagNbt},
 *   capital?: { dimension, x, y, z }, chunks: long[], throneMove?: { id, started }, throneMoved?: long,
 *   members: [ { player, role, joined } ],
 *   permissions?: { &lt;role&gt;: [ &lt;permission&gt;, ... ] },
 *   applications: [ { player, time } ], invitations: [ { player, time } ],
 *   population?: see {@link CitizenNbt}, orders?: see {@link OrderNbt} }
 * </pre>
 *
 * <p>Reading is strict: anything that cannot be understood throws {@link IllegalArgumentException}, so the
 * caller can keep the data read-only instead of silently dropping parts of it. The exceptions are the architectural style:
 * a style this version does not know is read as none, so that a style dropped by a later version does not lock the data; and the
 * flag, which is read leniently (see {@link FlagNbt}): a flag that cannot be understood is read as the flag of the colour.
 */
public final class KingdomNbt {
    static final String ID = "id";
    static final String NAME = "name";
    static final String COLOR = "color";
    static final String RULER = "ruler";
    static final String CREATED_GAME_TIME = "createdGameTime";
    static final String STYLE = "style";
    static final String CAPITAL = "capital";
    static final String DIMENSION = "dimension";
    static final String CHUNKS = "chunks";
    static final String MEMBERS = "members";
    static final String PLAYER = "player";
    static final String ROLE = "role";
    static final String JOINED = "joined";
    static final String PERMISSIONS = "permissions";
    static final String APPLICATIONS = "applications";
    static final String INVITATIONS = "invitations";
    static final String TIME = "time";
    static final String THRONE_MOVE = "throneMove";
    static final String THRONE_MOVED = "throneMoved";
    static final String STARTED = "started";

    private KingdomNbt() {
    }

    /**
     * The basic fields of a kingdom, without land and members.
     */
    public static CompoundTag write(Kingdom kingdom) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, kingdom.id());
        tag.putString(NAME, kingdom.name());
        tag.putInt(COLOR, kingdom.color());
        if (kingdom.rulerId() != null) {
            tag.putUUID(RULER, kingdom.rulerId());
        }
        tag.putLong(CREATED_GAME_TIME, kingdom.createdGameTime());
        if (!kingdom.style().isEmpty()) {
            tag.putString(STYLE, kingdom.style());
        }
        FlagNbt.put(tag, kingdom.flag());
        return tag;
    }

    /**
     * A kingdom with everything the state knows about it.
     */
    public static CompoundTag write(KingdomWorldState state, Kingdom kingdom) {
        CompoundTag tag = write(kingdom);
        Optional<Territory> territory = state.territory().territory(kingdom.id());
        if (territory.isPresent()) {
            Capital capital = territory.get().capital();
            CompoundTag capitalTag = new CompoundTag();
            capitalTag.putString(DIMENSION, capital.dimension());
            capitalTag.putInt("x", capital.x());
            capitalTag.putInt("y", capital.y());
            capitalTag.putInt("z", capital.z());
            tag.put(CAPITAL, capitalTag);
            tag.put(CHUNKS, new LongArrayTag(territory.get().chunks().stream().mapToLong(Long::longValue).toArray()));
        }
        state.throneMoves().pending(kingdom.id()).ifPresent(move -> {
            CompoundTag moveTag = new CompoundTag();
            moveTag.putUUID(ID, move.id());
            moveTag.putLong(STARTED, move.started());
            tag.put(THRONE_MOVE, moveTag);
        });
        state.throneMoves().lastMoved(kingdom.id()).ifPresent(time -> tag.putLong(THRONE_MOVED, time));
        Optional<KingdomMembers> members = state.membership().kingdom(kingdom.id());
        if (members.isPresent()) {
            ListTag memberList = new ListTag();
            for (Member member : members.get().members().values()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID(PLAYER, member.playerId());
                entry.putString(ROLE, member.role().id());
                entry.putLong(JOINED, member.joinedGameTime());
                memberList.add(entry);
            }
            tag.put(MEMBERS, memberList);
            RolePermissions rights = members.get().permissions();
            CompoundTag rightsTag = new CompoundTag();
            for (Role role : Role.values()) {
                if (role == Role.KING) {
                    continue;
                }
                ListTag list = new ListTag();
                for (Permission permission : rights.effective(role)) {
                    list.add(StringTag.valueOf(permission.id()));
                }
                rightsTag.put(role.id(), list);
            }
            tag.put(PERMISSIONS, rightsTag);
            tag.put(APPLICATIONS, writePending(members.get().applications()));
            tag.put(INVITATIONS, writePending(members.get().invitations()));
        }
        tag.put(CitizenNbt.POPULATION, CitizenNbt.write(state.citizens(), kingdom.id()));
        tag.put(OrderNbt.ORDERS, OrderNbt.write(state.orders(), kingdom.id()));
        state.economy().treasury(kingdom.id()).ifPresent(treasury -> tag.put(EconomyNbt.TREASURY, EconomyNbt.write(treasury)));
        return tag;
    }

    /**
     * Reads the basic fields of a kingdom.
     *
     * @throws IllegalArgumentException if the tag does not describe a valid kingdom
     */
    public static Kingdom read(CompoundTag tag) {
        if (!tag.hasUUID(ID)) {
            throw new IllegalArgumentException("Missing kingdom id");
        }
        if (!tag.contains(NAME, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing kingdom name");
        }
        UUID ruler = tag.hasUUID(RULER) ? tag.getUUID(RULER) : null;
        String style = tag.contains(STYLE, Tag.TAG_STRING) ? tag.getString(STYLE) : "";
        return new Kingdom(tag.getUUID(ID), tag.getString(NAME), tag.getInt(COLOR), ruler, tag.getLong(CREATED_GAME_TIME),
                ArchitecturalStyles.known(style) ? style : "", FlagNbt.get(tag));
    }

    /**
     * Reads a kingdom with land and members and restores it into the state; all or nothing.
     *
     * @throws IllegalArgumentException if the tag cannot be understood or conflicts with restored kingdoms
     */
    public static Kingdom restore(KingdomWorldState state, CompoundTag tag) {
        Kingdom kingdom = read(tag);
        Capital capital = null;
        List<Long> chunks = new ArrayList<>();
        if (tag.contains(CAPITAL)) {
            if (!tag.contains(CAPITAL, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Capital of " + kingdom.name() + " is not a compound");
            }
            CompoundTag capitalTag = tag.getCompound(CAPITAL);
            if (!capitalTag.contains(DIMENSION, Tag.TAG_STRING) || !capitalTag.contains("x", Tag.TAG_INT)
                    || !capitalTag.contains("y", Tag.TAG_INT) || !capitalTag.contains("z", Tag.TAG_INT)) {
                throw new IllegalArgumentException("Capital of " + kingdom.name() + " is incomplete");
            }
            capital = new Capital(capitalTag.getString(DIMENSION), capitalTag.getInt("x"), capitalTag.getInt("y"), capitalTag.getInt("z"));
        }
        if (tag.contains(CHUNKS)) {
            if (!tag.contains(CHUNKS, Tag.TAG_LONG_ARRAY)) {
                throw new IllegalArgumentException("Chunks of " + kingdom.name() + " are not a long array");
            }
            for (long chunk : tag.getLongArray(CHUNKS)) {
                chunks.add(chunk);
            }
        }

        List<Member> members = new ArrayList<>();
        for (CompoundTag entry : compounds(tag, MEMBERS, kingdom)) {
            if (!entry.hasUUID(PLAYER)) {
                throw new IllegalArgumentException("Member of " + kingdom.name() + " without player id");
            }
            Role role = Role.byId(entry.getString(ROLE))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role '" + entry.getString(ROLE) + "' in " + kingdom.name()));
            members.add(new Member(entry.getUUID(PLAYER), role, entry.getLong(JOINED)));
        }

        RolePermissions rights;
        if (tag.contains(PERMISSIONS)) {
            if (!tag.contains(PERMISSIONS, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Rights of " + kingdom.name() + " are not a compound");
            }
            CompoundTag rightsTag = tag.getCompound(PERMISSIONS);
            rights = new RolePermissions();
            for (String key : rightsTag.getAllKeys()) {
                Role role = Role.byId(key).filter(Role::isAssignable)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown position '" + key + "' in rights of " + kingdom.name()));
                if (!rightsTag.contains(key, Tag.TAG_LIST)) {
                    throw new IllegalArgumentException("Rights of " + key + " in " + kingdom.name() + " are not a list");
                }
                ListTag list = (ListTag) rightsTag.get(key);
                if (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING) {
                    throw new IllegalArgumentException("Rights of " + key + " in " + kingdom.name() + " are not strings");
                }
                for (int i = 0; i < list.size(); i++) {
                    String id = list.getString(i);
                    Permission permission = Permission.byId(id)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown right '" + id + "' in " + kingdom.name()));
                    if (RolePermissions.isEditable(role, permission)) {
                        rights.set(role, permission, true);
                    }
                }
            }
        } else {
            rights = RolePermissions.defaults();
        }

        Map<UUID, Long> applications = readPending(tag, APPLICATIONS, kingdom);
        Map<UUID, Long> invitations = readPending(tag, INVITATIONS, kingdom);
        CitizenNbt.Population population = CitizenNbt.read(tag, kingdom.id(), kingdom.name());
        OrderNbt.Orders orders = OrderNbt.read(tag, kingdom.name());
        io.github.sekerator.kingdomworld.core.economy.Treasury treasury = EconomyNbt.read(tag, kingdom.id(), kingdom.name(),
                io.github.sekerator.kingdomworld.core.economy.CurrencyUnits.DEFAULT,
                io.github.sekerator.kingdomworld.core.economy.EconomyRules.DEFAULT.defaultPollTax());
        state.restore(kingdom, capital, chunks, rights, members, applications, invitations);
        if (capital != null) {
            state.throneMoves().restore(kingdom.id(), readThroneMove(tag), tag.contains(THRONE_MOVED, Tag.TAG_LONG)
                    ? java.util.OptionalLong.of(tag.getLong(THRONE_MOVED)) : java.util.OptionalLong.empty());
        }
        try {
            state.citizens().restore(kingdom.id(), population.sites(), population.posts(), population.citizens(),
                    population.lastArrival(), population.chronicle(), population.production());
            state.orders().restore(kingdom.id(), orders.orders(), orders.next());
            state.economy().restore(treasury);
        } catch (IllegalArgumentException e) {
            // All or nothing: a kingdom whose people or money cannot be restored is not restored either.
            state.discard(kingdom.id());
            throw e;
        }
        return kingdom;
    }

    /**
     * The move of the throne going on. Data from before thrones moved has none; a move that cannot be read is dropped rather
     * than locking the data: the throne handed out for it then does nothing, and the king starts a new move.
     */
    private static Optional<ThroneMoves.Move> readThroneMove(CompoundTag tag) {
        if (!tag.contains(THRONE_MOVE, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag moveTag = tag.getCompound(THRONE_MOVE);
        return moveTag.hasUUID(ID) ? Optional.of(new ThroneMoves.Move(moveTag.getUUID(ID), moveTag.getLong(STARTED))) : Optional.empty();
    }

    private static ListTag writePending(Map<UUID, Long> pending) {
        ListTag list = new ListTag();
        pending.forEach((player, time) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(PLAYER, player);
            entry.putLong(TIME, time);
            list.add(entry);
        });
        return list;
    }

    private static Map<UUID, Long> readPending(CompoundTag tag, String key, Kingdom kingdom) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        for (CompoundTag entry : compounds(tag, key, kingdom)) {
            if (!entry.hasUUID(PLAYER)) {
                throw new IllegalArgumentException("Entry of " + key + " in " + kingdom.name() + " without player id");
            }
            result.put(entry.getUUID(PLAYER), entry.getLong(TIME));
        }
        return result;
    }

    private static List<CompoundTag> compounds(CompoundTag tag, String key, Kingdom kingdom) {
        if (!tag.contains(key)) {
            return List.of();
        }
        if (!tag.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException(key + " of " + kingdom.name() + " is not a list");
        }
        ListTag raw = (ListTag) tag.get(key);
        if (!raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " of " + kingdom.name() + " does not hold compounds");
        }
        List<CompoundTag> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            result.add(raw.getCompound(i));
        }
        return result;
    }
}
