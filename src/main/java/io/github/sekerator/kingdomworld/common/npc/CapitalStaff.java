package io.github.sekerator.kingdomworld.common.npc;

import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings;
import io.github.sekerator.kingdomworld.common.construction.ConstructionWork;
import io.github.sekerator.kingdomworld.common.registry.ModRegistries;
import io.github.sekerator.kingdomworld.core.citizen.Site;
import io.github.sekerator.kingdomworld.core.citizen.SiteKinds;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.construction.Box;
import io.github.sekerator.kingdomworld.core.construction.Building;
import io.github.sekerator.kingdomworld.core.construction.Materials;
import io.github.sekerator.kingdomworld.core.construction.Placement;
import io.github.sekerator.kingdomworld.core.npc.NpcComposition;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.npc.StyledPlan;
import io.github.sekerator.kingdomworld.core.npc.TemplateCatalog;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The work in the buildings of a styled capital once they stand: the registrar in the guild house, the minter in the treasury,
 * the builders and carriers in their yards, the bank in the treasury and the front gardens of the houses. Safe to repeat: a
 * restart in the middle of the work does it again with the same result.
 */
final class CapitalStaff {
    /** Flowers of the front gardens, two kinds a garden. */
    private static final List<Block> FLOWERS = List.of(Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
            Blocks.AZURE_BLUET, Blocks.ALLIUM, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.PINK_TULIP, Blocks.WHITE_TULIP,
            Blocks.LILY_OF_THE_VALLEY);
    /** The same flowers in pots, for gardens on ground where flowers do not grow (sand). */
    private static final List<Block> POTTED = List.of(Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_OXEYE_DAISY,
            Blocks.POTTED_CORNFLOWER, Blocks.POTTED_AZURE_BLUET, Blocks.POTTED_ALLIUM, Blocks.POTTED_RED_TULIP, Blocks.POTTED_ORANGE_TULIP,
            Blocks.POTTED_PINK_TULIP, Blocks.POTTED_WHITE_TULIP, Blocks.POTTED_LILY_OF_THE_VALLEY);

    private CapitalStaff() {
    }

    /**
     * Sets the posts of work in the buildings of a styled capital, places its bank and lays its front gardens.
     *
     * @param id the kingdom of the capital
     */
    static void staff(MinecraftServer server, ServerLevel level, NpcSite site, NpcComposition composition, UUID id) {
        professions(server, composition, id);
        StyledPlan.bank(composition).ifPresent(bank -> place(level, id,
                new BlockPos(site.x() + bank[0], site.groundY() + 1 + bank[2], site.z() + bank[1]), ModRegistries.BANK.get().defaultBlockState()));
        for (int[] garden : composition.gardens()) {
            garden(server, level, site, composition, id, garden);
        }
    }

    // ---------------------------------------------------------------- posts of work

    /**
     * Gives the posts of work inside the guild house, the treasury and the yards their professions and seats, by the kind of
     * the template of each building ({@link StyledPlan#profession}); the seats of a building are shared between its posts.
     */
    private static void professions(MinecraftServer server, NpcComposition composition, UUID id) {
        KingdomWorldState state = KingdomWorldApi.state(server);
        int max = GatheringSettings.rules().maxSeats();
        for (Building building : state.construction().buildingsOf(id)) {
            String profession = TemplateCatalog.get(building.templateId()).map(StyledPlan::profession).orElse("");
            if (profession.isEmpty()) {
                continue;
            }
            int seats = seats(composition, profession);
            if (seats <= 0) {
                continue;
            }
            Box box = building.box();
            if (box == null) {
                continue;
            }
            // The posts are looked for among the places of the chunks of the building, not among the posts of the whole world.
            List<WorkPost> inside = new ArrayList<>();
            for (long chunk : box.chunks()) {
                for (Site place : state.citizens().sites().sitesInChunk(building.dimension(), chunk)) {
                    WorldPos at = place.pos();
                    if (SiteKinds.WORK_POST.equals(place.kind()) && id.equals(place.kingdomId()) && box.contains(at.x(), at.y(), at.z())) {
                        state.citizens().posts().get(at).ifPresent(inside::add);
                    }
                }
            }
            // In the order of their places, so that a repeat gives every post the same seats.
            inside.sort(Comparator.comparingInt((WorkPost post) -> post.pos().x()).thenComparingInt(post -> post.pos().y())
                    .thenComparingInt(post -> post.pos().z()));
            for (int i = 0; i < inside.size(); i++) {
                int share = NpcComposition.share(seats, inside.size(), i);
                if (share <= 0) {
                    continue;
                }
                WorldPos at = inside.get(i).pos();
                state.setPostProfession(null, at, profession);
                state.setPostSeats(null, at, Math.min(share, max), max);
            }
        }
    }

    /**
     * The workers of a profession inside the buildings of a styled capital: the registrar and the minter all of theirs, the
     * builders and carriers those their posts by the gates leave to their yards.
     */
    private static int seats(NpcComposition composition, String profession) {
        return switch (profession) {
            case "registrar", "minter" -> composition.jobs().getOrDefault(profession, 0);
            default -> StyledPlan.seatsInBuildings(composition, profession);
        };
    }

    /**
     * Places a block of the mod as a player would (its place counts for citizens at once), on the land of the kingdom where
     * nothing protected stands and the block is not there yet.
     */
    private static void place(ServerLevel level, UUID id, BlockPos pos, BlockState state) {
        if (!id.equals(KingdomWorldApi.ownerAt(level, pos)) || level.getBlockState(pos).is(state.getBlock())) {
            return;
        }
        BlockState there = level.getBlockState(pos);
        if (NpcGround.protectedAt(level, pos, there)) {
            return;
        }
        ConstructionWork.place(level, pos, state, state.getBlock().asItem());
    }

    // ---------------------------------------------------------------- front gardens

    /**
     * A front garden before a house that stands back from its row: a fence of the wood of the house along the street with an
     * opening before the door, and flowers between the fence and the house.
     *
     * @param garden {minX, minZ, maxX, maxZ, facing, door} from the throne; facing is where the front of the house looks, door
     *               the column of the opening along the street
     */
    private static void garden(MinecraftServer server, ServerLevel level, NpcSite site, NpcComposition composition, UUID id, int[] garden) {
        int minX = garden[0];
        int minZ = garden[1];
        int maxX = garden[2];
        int maxZ = garden[3];
        int facing = garden[4];
        boolean alongX = facing == 0 || facing == 2;
        Optional<NpcComposition.Plot> house = houseOf(composition, garden);
        int door = door(house, garden);
        BlockState fence = NpcCity.block(fenceWood(server, site, house) + "_fence", Blocks.OAK_FENCE);
        long seed = NpcCity.plotSeed(site, -1, minX, minZ);
        int first = (int) Math.floorMod(seed, (long) FLOWERS.size());
        int second = (first + 1 + (int) Math.floorMod(seed >>> 16, (long) (FLOWERS.size() - 1))) % FLOWERS.size();
        int y = site.groundY() + 1;
        List<BlockPos> fences = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int along = alongX ? x : z;
                if (along == door) {
                    // The way from the street to the door stays open.
                    continue;
                }
                boolean street = switch (facing) {
                    case 0 -> z == maxZ;
                    case 1 -> x == minX;
                    case 2 -> z == minZ;
                    default -> x == maxX;
                };
                BlockPos pos = new BlockPos(site.x() + x, y, site.z() + z);
                BlockState there = level.getBlockState(pos);
                if (street) {
                    if (there.is(fence.getBlock()) || free(there) && NpcGround.set(level, id, pos, fence)) {
                        fences.add(pos);
                    }
                    continue;
                }
                if (!free(there)) {
                    continue;
                }
                int kind = Math.floorMod(along, 2) == 0 ? first : second;
                BlockState flower = FLOWERS.get(kind).defaultBlockState();
                if (flower.canSurvive(level, pos)) {
                    NpcGround.set(level, id, pos, flower);
                } else if (Math.floorMod(along, 2) == 0) {
                    NpcGround.set(level, id, pos, POTTED.get(kind).defaultBlockState());
                }
            }
        }
        // The posts of a fence join once all of them stand.
        for (BlockPos pos : fences) {
            BlockState state = level.getBlockState(pos);
            BlockState joined = Block.updateFromNeighbourShapes(state, level, pos);
            if (joined != state) {
                level.setBlock(pos, joined, Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Whether a garden may take a place: nothing stands there but air or what is replaced by anything set there (grass).
     */
    private static boolean free(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * The house a front garden lies before: the plot facing the same way whose front touches the back of the garden.
     */
    private static Optional<NpcComposition.Plot> houseOf(NpcComposition composition, int[] garden) {
        int facing = garden[4];
        for (NpcComposition.Plot plot : composition.buildings()) {
            if (plot.facing() != facing || TemplateCatalog.get(plot.template()).isEmpty()) {
                continue;
            }
            int[] f = NpcComposition.footprint(plot);
            boolean touches = switch (facing) {
                case 0 -> f[3] + 1 == garden[1] && f[0] <= garden[2] && f[2] >= garden[0];
                case 1 -> f[0] - 1 == garden[2] && f[1] <= garden[3] && f[3] >= garden[1];
                case 2 -> f[1] - 1 == garden[3] && f[0] <= garden[2] && f[2] >= garden[0];
                default -> f[2] + 1 == garden[0] && f[1] <= garden[3] && f[3] >= garden[1];
            };
            if (touches) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    /**
     * The column of a garden before the door of its house, along the street: the one the plan gives with the garden, else where
     * the template of the house has its door, else the middle of the garden.
     */
    private static int door(Optional<NpcComposition.Plot> house, int[] garden) {
        boolean alongX = garden[4] == 0 || garden[4] == 2;
        int low = alongX ? garden[0] : garden[1];
        int high = alongX ? garden[2] : garden[3];
        if (garden.length > 5 && garden[5] >= low && garden[5] <= high) {
            return garden[5];
        }
        if (house.isPresent()) {
            NpcComposition.Plot plot = house.get();
            List<TemplateCatalog.Spot> doors = TemplateCatalog.of(plot.template()).points("door");
            if (!doors.isEmpty()) {
                TemplateCatalog.Spot spot = doors.get(0);
                int[] turned = new Placement(plot.facing(), plot.mirror()).apply(spot.x(), spot.y(), spot.z());
                int at = alongX ? plot.x() + turned[0] : plot.z() + turned[2];
                if (at >= low && at <= high) {
                    return at;
                }
            }
        }
        return Math.floorDiv(low + high, 2);
    }

    /**
     * The wood of the fence of a garden: that of its house, else the wood of the place.
     */
    private static String fenceWood(MinecraftServer server, NpcSite site, Optional<NpcComposition.Plot> house) {
        String wood = house.map(plot -> NpcCity.materials(server, site, plot).wood()).orElse("");
        return wood.isEmpty() || Materials.AUTO.equals(wood) ? NpcCity.wood(site) : wood;
    }
}
