package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * What the throne screen shows about a kingdom to one player. The server leaves out what the player may not
 * see: members and rights only go to members, applications only to members who may recruit.
 *
 * @param capital    the throne position, or {@code null} for a kingdom without land
 * @param viewerRole the viewer's position, or {@code null} if the viewer is not a member
 */
public record KingdomView(UUID id, String name, int color, String rulerName, int memberCount, int chunks,
                          int chunkLimit, int population, @Nullable BlockPos capital, Relation relation, @Nullable Role viewerRole,
                          EnumSet<Permission> viewerPermissions, List<MemberEntry> members,
                          List<PendingEntry> applications, Map<Role, EnumSet<Permission>> rights) {
    public static final int MAX_MEMBERS = 1000;
    public static final int MAX_APPLICATIONS = 100;

    /**
     * How the viewer relates to the kingdom.
     */
    public enum Relation {
        MEMBER,
        /** The viewer has applied to join. */
        APPLIED,
        /** The kingdom has invited the viewer. */
        INVITED,
        /** The viewer belongs to another kingdom. */
        OTHER_KINGDOM,
        OUTSIDER
    }

    public record MemberEntry(UUID id, String name, Role role, boolean online) {
    }

    public record PendingEntry(UUID id, String name) {
    }

    public KingdomView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(relation, "relation");
        viewerPermissions = viewerPermissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(viewerPermissions);
        members = List.copyOf(members);
        applications = List.copyOf(applications);
        EnumMap<Role, EnumSet<Permission>> copy = new EnumMap<>(Role.class);
        rights.forEach((role, set) -> copy.put(role, set.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(set)));
        rights = copy;
        if (members.size() > MAX_MEMBERS || applications.size() > MAX_APPLICATIONS) {
            throw new IllegalArgumentException("Kingdom view too large");
        }
    }

    public boolean can(Permission permission) {
        return viewerPermissions.contains(permission);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, KingdomNames.MAX_LENGTH);
        buf.writeInt(color);
        buf.writeUtf(rulerName, KingdomMapInfo.MAX_PLAYER_NAME);
        buf.writeVarInt(memberCount);
        buf.writeVarInt(chunks);
        buf.writeVarInt(chunkLimit);
        buf.writeVarInt(population);
        buf.writeBoolean(capital != null);
        if (capital != null) {
            buf.writeBlockPos(capital);
        }
        buf.writeEnum(relation);
        buf.writeBoolean(viewerRole != null);
        if (viewerRole != null) {
            buf.writeEnum(viewerRole);
        }
        buf.writeEnumSet(viewerPermissions, Permission.class);
        buf.writeVarInt(members.size());
        for (MemberEntry member : members) {
            buf.writeUUID(member.id());
            buf.writeUtf(member.name(), KingdomMapInfo.MAX_PLAYER_NAME);
            buf.writeEnum(member.role());
            buf.writeBoolean(member.online());
        }
        buf.writeVarInt(applications.size());
        for (PendingEntry entry : applications) {
            buf.writeUUID(entry.id());
            buf.writeUtf(entry.name(), KingdomMapInfo.MAX_PLAYER_NAME);
        }
        buf.writeVarInt(rights.size());
        rights.forEach((role, set) -> {
            buf.writeEnum(role);
            buf.writeEnumSet(set, Permission.class);
        });
    }

    public static KingdomView decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(KingdomNames.MAX_LENGTH);
        int color = buf.readInt() & Kingdom.MAX_COLOR;
        String ruler = buf.readUtf(KingdomMapInfo.MAX_PLAYER_NAME);
        int memberCount = buf.readVarInt();
        int chunks = buf.readVarInt();
        int chunkLimit = buf.readVarInt();
        int population = buf.readVarInt();
        BlockPos capital = buf.readBoolean() ? buf.readBlockPos() : null;
        Relation relation = readEnum(buf, Relation.class);
        Role role = buf.readBoolean() ? readEnum(buf, Role.class) : null;
        EnumSet<Permission> permissions = buf.readEnumSet(Permission.class);
        int memberSize = readSize(buf, MAX_MEMBERS);
        List<MemberEntry> members = new ArrayList<>(memberSize);
        for (int i = 0; i < memberSize; i++) {
            members.add(new MemberEntry(buf.readUUID(), buf.readUtf(KingdomMapInfo.MAX_PLAYER_NAME), readEnum(buf, Role.class), buf.readBoolean()));
        }
        int applicationSize = readSize(buf, MAX_APPLICATIONS);
        List<PendingEntry> applications = new ArrayList<>(applicationSize);
        for (int i = 0; i < applicationSize; i++) {
            applications.add(new PendingEntry(buf.readUUID(), buf.readUtf(KingdomMapInfo.MAX_PLAYER_NAME)));
        }
        int rightsSize = readSize(buf, Role.values().length);
        Map<Role, EnumSet<Permission>> rights = new EnumMap<>(Role.class);
        for (int i = 0; i < rightsSize; i++) {
            Role key = readEnum(buf, Role.class);
            rights.put(key, buf.readEnumSet(Permission.class));
        }
        return new KingdomView(id, name, color, ruler, memberCount, chunks, chunkLimit, population, capital, relation, role,
                permissions, members, applications, rights);
    }

    static int readSize(FriendlyByteBuf buf, int max) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new DecoderException("List size " + size + " out of range (max " + max + ")");
        }
        return size;
    }

    /**
     * Reads an enum constant, refusing ordinals out of range instead of failing with an index error.
     */
    public static <E extends Enum<E>> E readEnum(FriendlyByteBuf buf, Class<E> type) {
        int ordinal = buf.readVarInt();
        E[] values = type.getEnumConstants();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("Invalid " + type.getSimpleName() + " ordinal " + ordinal);
        }
        return values[ordinal];
    }
}
