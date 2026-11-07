package io.github.sekerator.kingdomworld.common.kingdom;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.network.data.KingdomMapInfo;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.packet.MapAreaPacket;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.membership.KingdomMembers;
import io.github.sekerator.kingdomworld.core.membership.Member;
import io.github.sekerator.kingdomworld.core.membership.MembershipRegistry;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import io.github.sekerator.kingdomworld.core.membership.RolePermissions;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds what clients are shown: the throne screen view of a kingdom and the political map.
 */
public final class KingdomViews {

    private KingdomViews() {
    }

    /**
     * The throne screen data of a kingdom as {@code viewer} may see it.
     */
    public static KingdomView view(MinecraftServer server, Kingdom kingdom, UUID viewer) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        MembershipRegistry membership = state.membership();
        Optional<KingdomMembers> members = membership.kingdom(kingdom.id());
        Optional<Territory> territory = state.territory().territory(kingdom.id());

        Optional<Member> viewerMember = members.flatMap(m -> m.member(viewer));
        KingdomView.Relation relation;
        if (viewerMember.isPresent()) {
            relation = KingdomView.Relation.MEMBER;
        } else if (membership.kingdomOf(viewer).isPresent()) {
            relation = KingdomView.Relation.OTHER_KINGDOM;
        } else if (membership.invitationsOf(viewer).contains(kingdom.id())) {
            relation = KingdomView.Relation.INVITED;
        } else if (membership.applicationsOf(viewer).contains(kingdom.id())) {
            relation = KingdomView.Relation.APPLIED;
        } else {
            relation = KingdomView.Relation.OUTSIDER;
        }

        Role viewerRole = viewerMember.map(Member::role).orElse(null);
        RolePermissions rights = members.map(KingdomMembers::permissions).orElseGet(RolePermissions::new);
        EnumSet<Permission> viewerPermissions = viewerRole == null ? EnumSet.noneOf(Permission.class) : rights.effective(viewerRole);

        List<KingdomView.MemberEntry> memberEntries = new ArrayList<>();
        List<KingdomView.PendingEntry> applications = new ArrayList<>();
        Map<Role, EnumSet<Permission>> rightsTable = new EnumMap<>(Role.class);
        if (viewerRole != null && members.isPresent()) {
            for (Member member : sorted(members.get())) {
                if (memberEntries.size() >= KingdomView.MAX_MEMBERS) {
                    break;
                }
                memberEntries.add(new KingdomView.MemberEntry(member.playerId(), PlayerNames.name(server, member.playerId()),
                        member.role(), PlayerNames.isOnline(server, member.playerId())));
            }
            for (Role role : Role.values()) {
                rightsTable.put(role, rights.effective(role));
            }
            if (viewerPermissions.contains(Permission.RECRUIT)) {
                for (UUID applicant : members.get().applications().keySet()) {
                    if (applications.size() >= KingdomView.MAX_APPLICATIONS) {
                        break;
                    }
                    applications.add(new KingdomView.PendingEntry(applicant, PlayerNames.name(server, applicant)));
                }
            }
        }
        BlockPos capital = territory.map(t -> new BlockPos(t.capital().x(), t.capital().y(), t.capital().z())).orElse(null);
        return new KingdomView(kingdom.id(), kingdom.name(), kingdom.color(), PlayerNames.name(server, kingdom.rulerId()),
                members.map(KingdomMembers::size).orElse(0), territory.map(Territory::size).orElse(0),
                state.chunkLimit(kingdom.id(), KingdomWorldApi.rules()), state.citizens().registry().count(kingdom.id()),
                capital, relation, viewerRole, viewerPermissions,
                memberEntries, applications, rightsTable);
    }

    /**
     * Members ordered by position, then by when they joined.
     */
    private static List<Member> sorted(KingdomMembers members) {
        List<Member> list = new ArrayList<>(members.members().values());
        list.sort((a, b) -> a.role() != b.role() ? a.role().compareTo(b.role()) : Long.compare(a.joinedGameTime(), b.joinedGameTime()));
        return list;
    }

    /**
     * Map facts about a kingdom for players looking at {@code dimension}.
     */
    public static KingdomMapInfo mapInfo(MinecraftServer server, KingdomWorldState state, Kingdom kingdom, String dimension) {
        Optional<Territory> territory = state.territory().territory(kingdom.id());
        int members = state.membership().kingdom(kingdom.id()).map(KingdomMembers::size).orElse(0);
        boolean hasCapital = territory.isPresent() && territory.get().dimension().equals(dimension);
        Capital capital = territory.map(Territory::capital).orElse(null);
        return new KingdomMapInfo(kingdom.id(), kingdom.name(), kingdom.color(), PlayerNames.name(server, kingdom.rulerId()),
                members, territory.map(Territory::size).orElse(0), state.citizens().registry().count(kingdom.id()), hasCapital,
                hasCapital ? capital.chunkX() : 0, hasCapital ? capital.chunkZ() : 0);
    }

    /**
     * Owners of a rectangle of chunks in compact form.
     */
    public static MapAreaPacket mapArea(ServerLevel level, int minX, int minZ, int width, int height) {
        return mapArea(level, null, minX, minZ, width, height);
    }

    /**
     * Owners of a rectangle of chunks as {@code viewer} may see them: land of kingdoms hidden from the viewer by the
     * {@link TerritorySnapshots#visibility() map visibility} looks unclaimed.
     */
    public static MapAreaPacket mapArea(ServerLevel level, @org.jetbrains.annotations.Nullable UUID viewer, int minX, int minZ,
                                        int width, int height) {
        MinecraftServer server = level.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        String dimension = Dimensions.id(level);
        Map<Long, UUID> claims = state.territory().claimsIn(dimension);
        int[] cells = new int[width * height];
        Map<UUID, Integer> paletteIndex = new LinkedHashMap<>();
        List<KingdomMapInfo> palette = new ArrayList<>();
        if (!claims.isEmpty()) {
            if (claims.size() < cells.length) {
                for (Map.Entry<Long, UUID> entry : claims.entrySet()) {
                    int x = ChunkKey.x(entry.getKey()) - minX;
                    int z = ChunkKey.z(entry.getKey()) - minZ;
                    if (x >= 0 && z >= 0 && x < width && z < height && TerritorySnapshots.canSee(viewer, entry.getValue())) {
                        cells[z * width + x] = indexOf(server, state, entry.getValue(), dimension, paletteIndex, palette);
                    }
                }
            } else {
                for (int z = 0; z < height; z++) {
                    for (int x = 0; x < width; x++) {
                        UUID owner = claims.get(ChunkKey.of(minX + x, minZ + z));
                        if (owner != null && TerritorySnapshots.canSee(viewer, owner)) {
                            cells[z * width + x] = indexOf(server, state, owner, dimension, paletteIndex, palette);
                        }
                    }
                }
            }
        }
        return new MapAreaPacket(dimension, minX, minZ, width, height, palette, cells);
    }

    private static int indexOf(MinecraftServer server, KingdomWorldState state, UUID kingdomId, String dimension,
                               Map<UUID, Integer> paletteIndex, List<KingdomMapInfo> palette) {
        Integer index = paletteIndex.get(kingdomId);
        if (index == null) {
            Kingdom kingdom = state.kingdoms().get(kingdomId).orElse(null);
            if (kingdom == null) {
                return 0;
            }
            palette.add(mapInfo(server, state, kingdom, dimension));
            index = palette.size();
            paletteIndex.put(kingdomId, index);
        }
        return index;
    }
}
