package io.github.sekerator.kingdomworld.common.npc;

import com.mojang.logging.LogUtils;
import io.github.sekerator.kingdomworld.common.api.KingdomWorldApi;
import io.github.sekerator.kingdomworld.common.citizen.CitizenLife;
import io.github.sekerator.kingdomworld.common.economy.TreasuryActions;
import io.github.sekerator.kingdomworld.common.guild.GuildSettings;
import io.github.sekerator.kingdomworld.common.guild.GuildWork;
import io.github.sekerator.kingdomworld.common.magic.MagicSettings;
import io.github.sekerator.kingdomworld.common.magic.MagicWork;
import io.github.sekerator.kingdomworld.core.citizen.Citizen;
import io.github.sekerator.kingdomworld.core.citizen.Professions;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.github.sekerator.kingdomworld.core.guild.GuildRules;
import io.github.sekerator.kingdomworld.core.guild.GuildState;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.Outcome;
import io.github.sekerator.kingdomworld.core.magic.EnergyCategory;
import io.github.sekerator.kingdomworld.core.magic.KingdomSpell;
import io.github.sekerator.kingdomworld.core.magic.MagicRules;
import io.github.sekerator.kingdomworld.core.magic.MagicState;
import io.github.sekerator.kingdomworld.core.magic.Research;
import io.github.sekerator.kingdomworld.core.npc.GuildRanks;
import io.github.sekerator.kingdomworld.core.npc.NpcKingdom;
import io.github.sekerator.kingdomworld.core.npc.NpcSite;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import io.github.sekerator.kingdomworld.core.territory.ChunkKey;
import io.github.sekerator.kingdomworld.core.territory.Territory;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The two guilds of the kingdoms of the world: their branch of the adventurers' guild and their guild of mages. A kingdom gets
 * the ranks of both when it is founded (see {@link GuildRanks}), its guild of mages the researches and the energy of its rank,
 * and when its buildings stand, its adventurers and mages, who come from abroad like the recruits of the royal army. A kingdom
 * founded before the guilds had ranks gets them when it is next looked at, and its adventurers and mages come a few at a time.
 * Every day the adventurers of a kingdom hunt far from players and bring the crystals home to its guild of mages, which keeps
 * the ward over the capital and heals its people with the energy; a rich kingdom's guilds rise a rank now and then, up to the
 * highest rank of its level.
 */
public final class NpcGuilds {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Chunks around the throne a guild of mages of a kingdom of the world keeps under its ward, in each direction. */
    private static final int WARD_REACH = 1;
    /** Days of energy for the ward a guild of mages keeps before it puts the ward up again. */
    private static final int WARD_DAYS = 3;

    private NpcGuilds() {
    }

    // ---------------------------------------------------------------- the ranks

    /**
     * The ranks of the guilds of a kingdom, as its branch and its guild of mages hold them.
     */
    public static GuildRanks.Ranks ranks(MinecraftServer server, UUID kingdomId) {
        Rank adventurers = GuildWork.state(server).branchOrDefault(kingdomId, GuildSettings.rules()).rank();
        Rank mages = MagicWork.state(server).guildRank(kingdomId);
        return new GuildRanks.Ranks(adventurers, mages);
    }

    /**
     * Whether a kingdom of the world already holds the top rank, so that no other may.
     */
    private static boolean topTaken(MinecraftServer server, UUID except) {
        for (NpcSite site : NpcWork.state(server).sites()) {
            if (site.kingdomId().isEmpty() || site.kingdomId().get().equals(except) || site.status() == NpcSite.Status.REMOVED) {
                continue;
            }
            if (ranks(server, site.kingdomId().get()).highest().atLeast(GuildRanks.TOP)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the guilds of a kingdom of the world have their ranks: both at least the lowest rank of its level.
     */
    private static boolean ranked(MinecraftServer server, NpcKingdom kingdom) {
        Rank low = GuildSettings.rules().worldRankMin(kingdom.level().ordinal());
        GuildRanks.Ranks ranks = ranks(server, kingdom.kingdomId());
        return ranks.adventurers().atLeast(low) && ranks.mages().atLeast(low) && GuildWork.state(server).branch(kingdom.kingdomId()).isPresent();
    }

    /**
     * Gives the guilds of a new kingdom of the world their ranks, and its guild of mages the researches and the energy of its
     * rank: the wealth of the kingdom at its founding.
     */
    static GuildRanks.Ranks found(MinecraftServer server, NpcSite site, NpcKingdom kingdom) {
        GuildRules rules = GuildSettings.rules();
        boolean strongest = GuildRanks.mayBeStrongest(kingdom.level(), rules) && !topTaken(server, kingdom.kingdomId());
        GuildRanks.Ranks ranks = GuildRanks.of(kingdom.level(), site.seed(), kingdom.character(), strongest, rules);
        give(server, kingdom.kingdomId(), ranks);
        LOGGER.info("The guilds of {}: the branch of the adventurers' guild of rank {}, the guild of mages of rank {}{}", kingdom.name().latin(),
                ranks.adventurers().label(), ranks.mages().label(), ranks.highest().atLeast(GuildRanks.TOP) ? ", the strongest of the world" : "");
        return ranks;
    }

    private static void give(MinecraftServer server, UUID kingdomId, GuildRanks.Ranks ranks) {
        GuildRules guildRules = GuildSettings.rules();
        MagicRules magicRules = MagicSettings.rules();
        if (!GuildWork.isReadOnly(server)) {
            GuildWork.state(server).setBranchRank(kingdomId, ranks.adventurers(), guildRules);
        }
        long day = TreasuryActions.day(server);
        List<Research> researches = new ArrayList<>();
        for (Research research : Research.values()) {
            if (researches.size() < magicRules.worldResearch(ranks.mages())) {
                researches.add(research);
            }
        }
        MagicWork.change(server, "found the guild of mages of a kingdom of the world", magic -> {
            magic.found(kingdomId, ranks.mages(), researches, magicRules.worldEnergy(ranks.mages()), day, magicRules);
            return Outcome.DONE;
        });
    }

    // ---------------------------------------------------------------- adventurers and mages

    private static List<Citizen> adventurers(KingdomWorldState world, UUID kingdomId) {
        List<Citizen> found = new ArrayList<>();
        for (Citizen citizen : world.citizens().registry().citizensOf(kingdomId)) {
            if (citizen.professionId().equals(Professions.ADVENTURER.id())) {
                found.add(citizen);
            }
        }
        return found;
    }

    private static int mages(KingdomWorldState world, UUID kingdomId) {
        int count = 0;
        for (Citizen citizen : world.citizens().registry().citizensOf(kingdomId)) {
            count += citizen.professionId().equals(Professions.MAGE.id()) ? 1 : 0;
        }
        return count;
    }

    /**
     * Adventurers and mages of a kingdom of the world come from abroad while the time lasts, as many as the ranks of its guilds
     * give it.
     *
     * @param nanos  the time of this call; at least one comes if any is missing
     * @param atMost how many come in this call at most
     * @return whether all have come (or none can)
     */
    static boolean arriveSome(MinecraftServer server, UUID kingdomId, long nanos, int atMost) {
        return arrive(server, kingdomId, nanos, atMost).done();
    }

    /**
     * What came of a call for adventurers and mages: how many came, and whether all have come (or none can).
     */
    record Arrival(int came, boolean done) {
        static final Arrival NONE = new Arrival(0, true);
    }

    static Arrival arrive(MinecraftServer server, UUID kingdomId, long nanos, int atMost) {
        if (GuildWork.isReadOnly(server) || MagicWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return Arrival.NONE;
        }
        Optional<NpcKingdom> kingdom = NpcWork.kingdom(server, kingdomId);
        Optional<Capital> capital = KingdomWorldApi.state(server).territory().territory(kingdomId).map(Territory::capital);
        if (kingdom.isEmpty() || capital.isEmpty()) {
            return Arrival.NONE;
        }
        GuildRanks.Ranks ranks = ranks(server, kingdomId);
        GuildRules guildRules = GuildSettings.rules();
        MagicRules magicRules = MagicSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        WorldPos throne = new WorldPos(capital.get().dimension(), capital.get().x(), capital.get().y(), capital.get().z());
        long started = System.nanoTime();
        int came = 0;
        // Adventurers first: their ranks in order, the head of the guild first.
        List<Rank> wanted = GuildRanks.adventurers(ranks.adventurers(), guildRules.worldAdventurers(ranks.adventurers()));
        GuildState guild = GuildWork.state(server);
        boolean stopped = false;
        while (adventurers(world, kingdomId).size() < wanted.size() && came < atMost) {
            if (came > 0 && System.nanoTime() - started > nanos) {
                return told(kingdom.get(), new Arrival(came, false));
            }
            Rank rank = wanted.get(adventurers(world, kingdomId).size());
            Optional<Citizen> newcomer = CitizenLife.arrive(server, kingdomId, throne, false, false);
            if (newcomer.isEmpty()) {
                // The kingdom takes no more citizens.
                return told(kingdom.get(), new Arrival(came, true));
            }
            UUID id = newcomer.get().id();
            Outcome made = KingdomWorldApi.change(server, "make an adventurer of a kingdom of the world",
                    state -> state.setProfession(null, id, Professions.ADVENTURER.id()));
            came++;
            if (!made.ok()) {
                stopped = true;
                break;
            }
            GuildWork.change(server, "rank an adventurer of a kingdom of the world", state -> {
                state.setHirelingRank(id, kingdomId, rank);
                return Outcome.DONE;
            });
        }
        // Mages for the free seats of the posts of the mage towers of the capital: a mage comes only for a seat.
        List<WorkPost> towers = world.citizens().posts().postsOf(kingdomId, Professions.MAGE.id());
        int wantedMages = magicRules.worldMages(ranks.mages());
        if (!towers.isEmpty() && !stopped) {
            // The tower of a guild of mages of the world seats all the mages of its rank, beyond the seats of a post of players.
            int seats = (int) Math.ceil(wantedMages / (double) towers.size());
            int maxSeats = Math.max(seats, io.github.sekerator.kingdomworld.common.citizen.work.GatheringSettings.rules().maxSeats());
            int room = 0;
            for (WorkPost post : towers) {
                if (post.seats() < seats) {
                    KingdomWorldApi.change(server, "seat the mages of a kingdom of the world",
                            state -> state.setPostSeats(null, post.pos(), seats, maxSeats));
                }
                room += Math.max(post.seats(), seats);
            }
            wantedMages = Math.min(wantedMages, room);
            while (mages(world, kingdomId) < wantedMages && came < atMost) {
                if (came > 0 && System.nanoTime() - started > nanos) {
                    return told(kingdom.get(), new Arrival(came, false));
                }
                Optional<WorkPost> post = towers.stream().filter(p -> p.freeSeats() > 0).findFirst();
                if (post.isEmpty()) {
                    break;
                }
                Optional<Citizen> newcomer = CitizenLife.arrive(server, kingdomId, post.get().pos(), false, false);
                if (newcomer.isEmpty()) {
                    return told(kingdom.get(), new Arrival(came, true));
                }
                came++;
                if (!world.hire(null, post.get().pos()).outcome().ok()) {
                    break;
                }
            }
        }
        return told(kingdom.get(), new Arrival(came, true));
    }

    private static Arrival told(NpcKingdom kingdom, Arrival arrival) {
        if (arrival.came() > 0) {
            LOGGER.info("{} adventurer(s) and mage(s) came to {} from abroad", arrival.came(), kingdom.name().latin());
        }
        return arrival;
    }

    /**
     * Whether a citizen of a kingdom of the world lodges with its guild: an adventurer at the guild, a mage in the tower, while it
     * has no bed of its own. Such a citizen lives as well housed as one who has, and takes a bed when one is free. The capitals of
     * the world have a bed for every citizen they were planned for; the people of the guilds came on top of them.
     */
    public static boolean lodged(MinecraftServer server, Citizen citizen) {
        if (citizen.home() != null || citizen.kingdomId() == null) {
            return false;
        }
        String profession = citizen.professionId();
        return (profession.equals(Professions.ADVENTURER.id()) || profession.equals(Professions.MAGE.id())) && NpcWork.isNpc(server, citizen.kingdomId());
    }

    // ---------------------------------------------------------------- every day

    /**
     * Looks after the guilds of the kingdoms of the world that stand: ranks for those founded before the guilds had them, the
     * adventurers and mages they lack a few at a time.
     */
    static void keepUp(MinecraftServer server) {
        if (GuildWork.isReadOnly(server) || MagicWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        long day = TreasuryActions.day(server);
        for (NpcSite site : List.copyOf(NpcWork.state(server).sites())) {
            if (site.status() == NpcSite.Status.REMOVED || site.kingdomId().isEmpty()) {
                continue;
            }
            Optional<NpcKingdom> kingdom = NpcWork.kingdom(server, site.kingdomId().get());
            if (kingdom.isEmpty()) {
                continue;
            }
            if (!ranked(server, kingdom.get())) {
                // A kingdom founded before the guilds had ranks, its buildings standing or not yet.
                found(server, site, kingdom.get());
            }
            if (site.status() == NpcSite.Status.SPAWNED) {
                // A few a minute, and no more than ARRIVALS_PER_DAY a day: the fallen are replaced like the army's.
                UUID id = kingdom.get().kingdomId();
                long[] today = ARRIVED.get(id);
                if (today == null || today[0] != day) {
                    today = new long[]{day, 0};
                    ARRIVED.put(id, today);
                }
                int left = (int) Math.max(0, ARRIVALS_PER_DAY - today[1]);
                if (left > 0) {
                    today[1] += arrive(server, id, Long.MAX_VALUE, Math.min(2, left)).came();
                }
            }
        }
    }

    /** Adventurers and mages who may come to a kingdom of the world a day once its capital stands. */
    static final int ARRIVALS_PER_DAY = 4;
    /** The day and how many came that day, by kingdom; forgotten on a restart, which costs at most a day's arrivals. */
    private static final java.util.Map<UUID, long[]> ARRIVED = new java.util.HashMap<>();

    /**
     * The adventurers and mages of a kingdom of the world whose buildings are being raised come from abroad, while the time of
     * the step lasts.
     *
     * @return whether all have come
     */
    static boolean raise(MinecraftServer server, NpcSite site, long nanos) {
        if (site.kingdomId().isEmpty() || GuildWork.isReadOnly(server) || MagicWork.isReadOnly(server)) {
            return true;
        }
        Optional<NpcKingdom> kingdom = NpcWork.kingdom(server, site.kingdomId().get());
        if (kingdom.isEmpty()) {
            return true;
        }
        if (!ranked(server, kingdom.get())) {
            found(server, site, kingdom.get());
        }
        return arriveSome(server, kingdom.get().kingdomId(), nanos, Integer.MAX_VALUE);
    }

    /**
     * A new day for the guilds of the kingdoms of the world that stand: the adventurers bring the crystals of their hunts home,
     * the guild of mages keeps the ward over the capital and heals with the energy, and the guilds of a rich kingdom rise a rank
     * now and then.
     */
    public static void day(MinecraftServer server, long day) {
        if (GuildWork.isReadOnly(server) || MagicWork.isReadOnly(server) || KingdomWorldApi.isReadOnly(server)) {
            return;
        }
        for (NpcSite site : List.copyOf(NpcWork.state(server).sites())) {
            if (site.status() != NpcSite.Status.SPAWNED || site.kingdomId().isEmpty()) {
                continue;
            }
            NpcWork.kingdom(server, site.kingdomId().get()).ifPresent(kingdom -> {
                hunt(server, kingdom, day);
                spells(server, kingdom, day);
                grow(server, kingdom, day);
            });
        }
    }

    private static void hunt(MinecraftServer server, NpcKingdom kingdom, long day) {
        boolean hunted = MagicWork.state(server).find(kingdom.kingdomId())
                .map(magic -> magic.journal().totals(day, io.github.sekerator.kingdomworld.core.magic.EnergyJournal.Period.TODAY)
                        .getOrDefault(EnergyCategory.CRYSTALS, io.github.sekerator.kingdomworld.core.magic.EnergyJournal.Totals.ZERO).income() > 0)
                .orElse(false);
        if (hunted) {
            // The crystals of today are home already (the server was started again on the same day).
            return;
        }
        KingdomWorldState world = KingdomWorldApi.state(server);
        GuildState guild = GuildWork.state(server);
        List<Rank> ranks = new ArrayList<>();
        for (Citizen citizen : adventurers(world, kingdom.kingdomId())) {
            if (!guild.isAway(citizen.id())) {
                guild.hireling(citizen.id()).ifPresent(record -> ranks.add(record.rank()));
            }
        }
        long energy = GuildRanks.huntEnergy(ranks, GuildSettings.rules());
        if (energy <= 0) {
            return;
        }
        MagicRules rules = MagicSettings.rules();
        MagicWork.change(server, "bring the crystals of a hunt home", magic -> {
            long room = Math.max(0, magic.capacity(kingdom.kingdomId(), rules) - magic.energy(kingdom.kingdomId()));
            long brought = Math.min(room, energy);
            return brought <= 0 ? Outcome.DONE : magic.add(kingdom.kingdomId(), brought, EnergyCategory.CRYSTALS, day, rules);
        });
    }

    /**
     * The guild of mages keeps the ward over the chunks around the throne while it has the energy for a few days of it, and
     * heals the wounded of the kingdom.
     */
    private static void spells(MinecraftServer server, NpcKingdom kingdom, long day) {
        UUID id = kingdom.kingdomId();
        MagicState magic = MagicWork.state(server);
        MagicRules rules = MagicSettings.rules();
        KingdomWorldState world = KingdomWorldApi.state(server);
        Optional<Territory> territory = world.territory().territory(id);
        if (territory.isEmpty()) {
            return;
        }
        if (magic.researched(id, Research.HEALING) && !magic.isOn(id, KingdomSpell.HEALING)) {
            MagicWork.change(server, "heal the people of a kingdom of the world",
                    state -> state.setSpell(null, id, KingdomSpell.HEALING, true, 0, day, rules, world));
        }
        if (!magic.researched(id, Research.WARD)) {
            return;
        }
        Capital capital = territory.get().capital();
        int cx = capital.x() >> 4;
        int cz = capital.z() >> 4;
        String dimension = territory.get().dimension();
        int warded = magic.find(id).map(found -> found.ward().size()).orElse(0);
        if (warded == 0) {
            for (int dx = -WARD_REACH; dx <= WARD_REACH; dx++) {
                for (int dz = -WARD_REACH; dz <= WARD_REACH; dz++) {
                    long chunk = ChunkKey.of(cx + dx, cz + dz);
                    boolean own = id.equals(world.territory().ownerOf(dimension, chunk));
                    MagicWork.change(server, "put a chunk of a kingdom of the world under the ward",
                            state -> state.toggleWard(null, id, dimension, chunk, own, rules, world));
                }
            }
            warded = magic.find(id).map(found -> found.ward().size()).orElse(0);
        }
        long cost = (long) warded * rules.spellPrice(KingdomSpell.WARD);
        if (warded > 0 && !magic.isOn(id, KingdomSpell.WARD) && magic.energy(id) >= cost * WARD_DAYS) {
            long units = warded;
            MagicWork.change(server, "keep the ward of a kingdom of the world",
                    state -> state.setSpell(null, id, KingdomSpell.WARD, true, units, day, rules, world));
        }
    }

    /**
     * The energy a guild of mages of a kingdom of the world keeps for the ward: a few days of it over the chunks it holds, or
     * over those around the throne it will hold.
     */
    static long wardReserve(MinecraftServer server, UUID kingdomId) {
        MagicState magic = MagicWork.state(server);
        if (!magic.researched(kingdomId, Research.WARD)) {
            return 0;
        }
        int side = 2 * WARD_REACH + 1;
        long chunks = Math.max(side * side, magic.find(kingdomId).map(found -> found.ward().size()).orElse(0));
        return chunks * MagicSettings.rules().spellPrice(KingdomSpell.WARD) * WARD_DAYS;
    }

    /**
     * Every {@link GuildRules#worldGrowthDays} days a kingdom whose treasury holds at least what it was founded with raises its
     * guilds a rank, each up to the highest rank of its level.
     */
    private static void grow(MinecraftServer server, NpcKingdom kingdom, long day) {
        GuildRules rules = GuildSettings.rules();
        long founded = kingdom.founded() / io.github.sekerator.kingdomworld.core.citizen.CitizenNeeds.TICKS_PER_DAY;
        long age = day - founded;
        if (age <= 0 || age % rules.worldGrowthDays != 0) {
            return;
        }
        UUID id = kingdom.kingdomId();
        long funds = KingdomWorldApi.state(server).economy().treasury(id).map(io.github.sekerator.kingdomworld.core.economy.Treasury::funds)
                .orElse(0L);
        if (funds < NpcSettings.rules().money(kingdom.level())) {
            return;
        }
        GuildRanks.Ranks now = ranks(server, id);
        boolean strongest = now.highest().atLeast(GuildRanks.TOP) || GuildRanks.mayBeStrongest(kingdom.level(), rules) && !topTaken(server, id);
        Rank ceiling = GuildRanks.ceiling(kingdom.level(), strongest, rules);
        Rank adventurers = now.adventurers().atLeast(ceiling) ? now.adventurers() : now.adventurers().plus(1);
        Rank mages = now.mages().atLeast(ceiling) ? now.mages() : now.mages().plus(1);
        if (adventurers == now.adventurers() && mages == now.mages()) {
            return;
        }
        GuildWork.state(server).setBranchRank(id, adventurers, rules);
        MagicWork.change(server, "raise the guild of mages of a kingdom of the world", magic -> {
            magic.setGuildRank(id, mages);
            return Outcome.DONE;
        });
        LOGGER.info("The guilds of {} rose: the branch of the adventurers' guild of rank {}, the guild of mages of rank {}", kingdom.name().latin(),
                adventurers.label(), mages.label());
    }
}
