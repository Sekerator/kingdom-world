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
                        member.role(), PlayerNames.isOnline(server, member.playerId()),
                        io.github.sekerator.kingdomworld.common.guild.GuildSavedData.get(server).state().member(member.playerId())
                                .map(guildMember -> guildMember.rank().ordinal()).orElse(-1)));
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
                memberEntries, applications, rightsTable, npcInfo(server, kingdom), rulerInfo(server, kingdom, viewer),
                KingdomStyles.of(server, kingdom.id()), KingdomStyles.changeCost())
                // Whether the throne is being moved is the business of the kingdom: told to its members alone.
                .withThroneMove(viewerRole != null && state.throneMoves().pending(kingdom.id()).isPresent(), ThroneMoving.cooldownDays());
    }

    /**
     * What a ruler of the world decreed, needs and feels about the viewer, or {@code null} for a kingdom of players.
     */
    @org.jetbrains.annotations.Nullable
    static io.github.sekerator.kingdomworld.common.network.data.RulerInfo rulerInfo(MinecraftServer server, Kingdom kingdom, UUID viewer) {
        return io.github.sekerator.kingdomworld.common.npc.NpcWork.kingdom(server, kingdom.id())
                .map(npc -> io.github.sekerator.kingdomworld.common.npc.NpcRulers.info(server, npc, viewer)).orElse(null);
    }

    /**
     * What players see of a kingdom of the world, or {@code null} for a kingdom of players: while its capital is being built,
     * the people and soldiers it is planned for, then the citizens and soldiers it has.
     */
    @org.jetbrains.annotations.Nullable
    public static io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npcInfo(MinecraftServer server, Kingdom kingdom) {
        return npcInfo(server, kingdom.id());
    }

    /**
     * What players see of the kingdom of the world {@code kingdomId}, or {@code null} for a kingdom of players.
     */
    @org.jetbrains.annotations.Nullable
    public static io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npcInfo(MinecraftServer server, UUID kingdomId) {
        io.github.sekerator.kingdomworld.core.npc.NpcState npcState = io.github.sekerator.kingdomworld.common.npc.NpcWork.state(server);
        return npcState.kingdom(kingdomId).map(npc -> {
            Optional<io.github.sekerator.kingdomworld.core.npc.NpcSite> site = npcState.site(npc.siteId());
            // As NpcWork.built: a kingdom without its place counts as standing.
            boolean raising = site.map(s -> s.status() != io.github.sekerator.kingdomworld.core.npc.NpcSite.Status.SPAWNED).orElse(false);
            int people;
            int soldiers;
            if (raising) {
                io.github.sekerator.kingdomworld.core.npc.NpcComposition planned = io.github.sekerator.kingdomworld.common.npc.NpcSpawner.composition(site.get());
                people = planned.citizens();
                soldiers = planned.soldiers();
            } else {
                people = KingdomWorldApi.state(server).citizens().registry().count(kingdomId);
                soldiers = io.github.sekerator.kingdomworld.common.army.ArmyWork.state(server).soldiersOf(kingdomId).size();
            }
            int settlements = 0;
            int settlementPeople = 0;
            for (io.github.sekerator.kingdomworld.core.npc.Settlement settlement : npcState.settlementsOf(kingdomId)) {
                if (settlement.tier() != io.github.sekerator.kingdomworld.core.land.Tier.CAPITAL
                        && settlement.status() != io.github.sekerator.kingdomworld.core.npc.Settlement.Status.RUINED) {
                    settlements++;
                    settlementPeople += settlement.population();
                }
            }
            io.github.sekerator.kingdomworld.core.npc.GuildRanks.Ranks ranks = io.github.sekerator.kingdomworld.common.npc.NpcGuilds.ranks(server, kingdomId);
            return io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo.of(npc, raising, people, soldiers, ranks.adventurers(),
                    ranks.mages()).withSettlements(settlements, settlementPeople);
        }).orElse(null);
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
     * The citizens of a kingdom; a kingdom of the world whose capital is still being built counts those it is built for.
     */
    private static int population(KingdomWorldState state, Kingdom kingdom,
                                  @org.jetbrains.annotations.Nullable io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npc) {
        int counted = state.citizens().registry().count(kingdom.id());
        return npc != null && npc.raising() ? Math.max(npc.people(), counted) : counted;
    }

    /**
     * Map facts about a kingdom for players looking at {@code dimension}.
     */
    public static KingdomMapInfo mapInfo(MinecraftServer server, KingdomWorldState state, Kingdom kingdom, String dimension) {
        Optional<Territory> territory = state.territory().territory(kingdom.id());
        int members = state.membership().kingdom(kingdom.id()).map(KingdomMembers::size).orElse(0);
        boolean hasCapital = territory.isPresent() && territory.get().dimension().equals(dimension);
        Capital capital = territory.map(Territory::capital).orElse(null);
        io.github.sekerator.kingdomworld.common.network.data.NpcKingdomInfo npc = npcInfo(server, kingdom);
        // The land of a kingdom is its claims and its provinces.
        int land = (int) Math.min(Integer.MAX_VALUE, state.territory().landSize(kingdom.id()));
        return new KingdomMapInfo(kingdom.id(), kingdom.name(), kingdom.color(), PlayerNames.name(server, kingdom.rulerId()),
                members, land, population(state, kingdom, npc), hasCapital,
                hasCapital ? capital.chunkX() : 0, hasCapital ? capital.chunkZ() : 0, npc);
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
        return mapArea(level, viewer, minX, minZ, width, height, 1);
    }

    /**
     * Owners of a rectangle of squares of {@code step} by {@code step} chunks, for a map zoomed far out: a square shows the owner
     * of the first claimed chunk found in it, otherwise the holder of the province at its middle. The claims are gone through
     * (they are few), then one look at the provinces a square: at most 128 by 128 looks however wide the area.
     */
    public static MapAreaPacket mapArea(ServerLevel level, @org.jetbrains.annotations.Nullable UUID viewer, int minX, int minZ,
                                        int width, int height, int step) {
        if (step <= 1) {
            return chunkArea(level, viewer, minX, minZ, width, height);
        }
        MinecraftServer server = level.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        String dimension = Dimensions.id(level);
        int[] cells = new int[width * height];
        Map<UUID, Integer> paletteIndex = new LinkedHashMap<>();
        List<KingdomMapInfo> palette = new ArrayList<>();
        for (Map.Entry<Long, UUID> entry : state.territory().claimsIn(dimension).entrySet()) {
            int x = Math.floorDiv(ChunkKey.x(entry.getKey()) - minX, step);
            int z = Math.floorDiv(ChunkKey.z(entry.getKey()) - minZ, step);
            if (x >= 0 && z >= 0 && x < width && z < height && cells[z * width + x] == 0 && TerritorySnapshots.canSee(viewer, entry.getValue())) {
                cells[z * width + x] = indexOf(server, state, entry.getValue(), dimension, paletteIndex, palette);
            }
        }
        if (state.territory().provinceMap().isPresent()) {
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    if (cells[z * width + x] != 0) {
                        continue;
                    }
                    UUID owner = state.territory().ownerOf(dimension, ChunkKey.of(minX + x * step + step / 2, minZ + z * step + step / 2));
                    if (owner != null && TerritorySnapshots.canSee(viewer, owner)) {
                        cells[z * width + x] = indexOf(server, state, owner, dimension, paletteIndex, palette);
                    }
                }
            }
        }
        return new MapAreaPacket(dimension, minX, minZ, width, height, palette, cells, step);
    }

    private static MapAreaPacket chunkArea(ServerLevel level, @org.jetbrains.annotations.Nullable UUID viewer, int minX, int minZ,
                                           int width, int height) {
        MinecraftServer server = level.getServer();
        KingdomWorldState state = KingdomWorldApi.state(server);
        String dimension = Dimensions.id(level);
        int[] cells = new int[width * height];
        Map<UUID, Integer> paletteIndex = new LinkedHashMap<>();
        List<KingdomMapInfo> palette = new ArrayList<>();
        if (state.territory().hasLand(dimension)) {
            // One look a chunk (the claim, then the province), at most 128 by 128 of them.
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    UUID owner = state.territory().ownerOf(dimension, ChunkKey.of(minX + x, minZ + z));
                    if (owner != null && TerritorySnapshots.canSee(viewer, owner)) {
                        cells[z * width + x] = indexOf(server, state, owner, dimension, paletteIndex, palette);
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
