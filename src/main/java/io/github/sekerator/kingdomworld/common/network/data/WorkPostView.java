package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.MineGoal;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the screen of a work post shows: its settings with the ranges the server allows, its status, what it
 * yields and who works there.
 *
 * @param canManage      whether the viewer may change the post
 * @param profession     the post's profession, {@code unemployed} if none is chosen
 * @param animal         entity type id of a herd, empty if none is chosen
 * @param animals        kinds of animals living in the zone, the choices for a herd
 * @param container      whether a container stands next to the post
 * @param measured       whether the yield was measured (otherwise it is an estimate of the zone)
 * @param perDay         items one worker gathers in a game day, rounded
 * @param producedToday  items gathered at the post today
 * @param unemployed     citizens without work in the kingdom, who could be hired
 * @param auto           for a workshop: whether it works on its own when nothing is ordered
 * @param stations       for a workshop: the workstations in its zone, by kind with their number
 * @param current        for a workshop: what it makes now, empty if nothing
 * @param progress       how far the current item is, in percent
 * @param goal           for a miner: what the post is asked for
 * @param mint           for a minter: the metals of the mint of the kingdom
 */
public record WorkPostView(BlockPos pos, UUID kingdomId, boolean canManage, String profession, int radius, int maxRadius,
                           int seats, int maxSeats, String animal, List<String> animals, int herdSize, int maxHerd,
                           WorkStatus status, boolean container, boolean measured, int perDay, long producedToday,
                           List<Worker> workers, int unemployed, boolean auto, List<Station> stations, ItemStack current,
                           int progress, net.minecraft.network.chat.Component site, Goal goal, Mint mint) {
    public static final int MAX_WORKERS = 16;
    public static final int MAX_ORES = 128;

    /**
     * What a miner's post is asked for, with what the window needs to offer the choices.
     *
     * @param ores      ids of the kinds of ore of the running game, the choices besides anything
     * @param ore       the kind asked for, empty for anything
     * @param amount    items the storehouses should hold, 0 for no limit
     * @param stock     items of it the storehouses hold now
     * @param autoDepth whether the depth comes from the kind of ore
     * @param depth     the height the mine works at
     * @param minDepth  the lowest height a mine may work at
     * @param maxDepth  the highest: the level of the post
     */
    public record Goal(List<String> ores, String ore, int amount, long stock, boolean autoDepth, int depth, int minDepth,
                       int maxDepth) {
        public static final Goal NONE = new Goal(List.of(), "", 0, 0L, true, 0, 0, 0);

        public Goal {
            ores = List.copyOf(ores);
            Objects.requireNonNull(ore, "ore");
            if (ores.size() > MAX_ORES) {
                throw new IllegalArgumentException("Too many kinds of ore");
            }
        }

        public boolean met() {
            return !ore.isEmpty() && amount > 0 && stock >= amount;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(ores.size());
            for (String kind : ores) {
                buf.writeUtf(kind, MineGoal.MAX_ORE_LENGTH);
            }
            buf.writeUtf(ore, MineGoal.MAX_ORE_LENGTH);
            buf.writeVarInt(amount);
            buf.writeVarLong(stock);
            buf.writeBoolean(autoDepth);
            buf.writeInt(depth);
            buf.writeInt(minDepth);
            buf.writeInt(maxDepth);
        }

        static Goal decode(FriendlyByteBuf buf) {
            int count = KingdomView.readSize(buf, MAX_ORES);
            List<String> ores = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ores.add(buf.readUtf(MineGoal.MAX_ORE_LENGTH));
            }
            return new Goal(ores, buf.readUtf(MineGoal.MAX_ORE_LENGTH), buf.readVarInt(), buf.readVarLong(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    /**
     * The metals of the mint of a minter's kingdom, which the window of the post shows and changes like the treasury tab.
     *
     * @param canManage whether the viewer has the right to manage the treasury, which the settings of the mint need
     * @param metals    the metals of the config and what the mint does with each
     */
    public record Mint(boolean canManage, List<TreasuryView.MintMetal> metals) {
        public static final Mint NONE = new Mint(false, List.of());

        public Mint {
            metals = List.copyOf(metals);
            if (metals.size() > TreasuryView.MAX_METALS) {
                throw new IllegalArgumentException("Too many metals of the mint");
            }
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(canManage);
            TreasuryView.MintMetal.encodeAll(buf, metals);
        }

        static Mint decode(FriendlyByteBuf buf) {
            return new Mint(buf.readBoolean(), TreasuryView.MintMetal.decodeAll(buf));
        }
    }

    /**
     * The view of a post that is not a mint.
     */
    public WorkPostView(BlockPos pos, UUID kingdomId, boolean canManage, String profession, int radius, int maxRadius,
                        int seats, int maxSeats, String animal, List<String> animals, int herdSize, int maxHerd,
                        WorkStatus status, boolean container, boolean measured, int perDay, long producedToday,
                        List<Worker> workers, int unemployed, boolean auto, List<Station> stations, ItemStack current,
                        int progress, net.minecraft.network.chat.Component site, Goal goal) {
        this(pos, kingdomId, canManage, profession, radius, maxRadius, seats, maxSeats, animal, animals, herdSize, maxHerd, status,
                container, measured, perDay, producedToday, workers, unemployed, auto, stations, current, progress, site, goal, Mint.NONE);
    }

    public WorkPostView withMint(Mint value) {
        return new WorkPostView(pos, kingdomId, canManage, profession, radius, maxRadius, seats, maxSeats, animal, animals, herdSize,
                maxHerd, status, container, measured, perDay, producedToday, workers, unemployed, auto, stations, current, progress,
                site, goal, value);
    }

    /**
     * The view of a post that is not a mine.
     */
    public WorkPostView(BlockPos pos, UUID kingdomId, boolean canManage, String profession, int radius, int maxRadius,
                        int seats, int maxSeats, String animal, List<String> animals, int herdSize, int maxHerd,
                        WorkStatus status, boolean container, boolean measured, int perDay, long producedToday,
                        List<Worker> workers, int unemployed, boolean auto, List<Station> stations, ItemStack current,
                        int progress, net.minecraft.network.chat.Component site) {
        this(pos, kingdomId, canManage, profession, radius, maxRadius, seats, maxSeats, animal, animals, herdSize, maxHerd, status,
                container, measured, perDay, producedToday, workers, unemployed, auto, stations, current, progress, site, Goal.NONE);
    }

    public WorkPostView withGoal(Goal value) {
        return new WorkPostView(pos, kingdomId, canManage, profession, radius, maxRadius, seats, maxSeats, animal, animals, herdSize,
                maxHerd, status, container, measured, perDay, producedToday, workers, unemployed, auto, stations, current, progress,
                site, value, mint);
    }
    public static final int MAX_ANIMALS = 16;
    public static final int MAX_STATIONS = 16;

    /**
     * Workstations of one kind in the zone of a workshop.
     */
    public record Station(String kind, int count) {
        public Station {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * A worker of the post.
     */
    public record Worker(UUID id, int seed, boolean female, WorkStatus status) {
        public Worker {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
        }
    }

    public WorkPostView {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(kingdomId, "kingdomId");
        Objects.requireNonNull(profession, "profession");
        Objects.requireNonNull(animal, "animal");
        Objects.requireNonNull(status, "status");
        animals = List.copyOf(animals);
        workers = List.copyOf(workers);
        stations = List.copyOf(stations);
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(mint, "mint");
        progress = Math.max(0, Math.min(100, progress));
        if (animals.size() > MAX_ANIMALS || workers.size() > MAX_WORKERS || stations.size() > MAX_STATIONS) {
            throw new IllegalArgumentException("Work post view too large");
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUUID(kingdomId);
        buf.writeBoolean(canManage);
        buf.writeUtf(profession, Profession.MAX_ID_LENGTH);
        buf.writeVarInt(radius);
        buf.writeVarInt(maxRadius);
        buf.writeVarInt(seats);
        buf.writeVarInt(maxSeats);
        buf.writeUtf(animal, WorkPost.MAX_ANIMAL_LENGTH);
        buf.writeVarInt(animals.size());
        for (String kind : animals) {
            buf.writeUtf(kind, WorkPost.MAX_ANIMAL_LENGTH);
        }
        buf.writeVarInt(herdSize);
        buf.writeVarInt(maxHerd);
        buf.writeEnum(status);
        buf.writeBoolean(container);
        buf.writeBoolean(measured);
        buf.writeVarInt(perDay);
        buf.writeVarLong(producedToday);
        buf.writeVarInt(workers.size());
        for (Worker worker : workers) {
            buf.writeUUID(worker.id());
            buf.writeInt(worker.seed());
            buf.writeBoolean(worker.female());
            buf.writeEnum(worker.status());
        }
        buf.writeVarInt(unemployed);
        buf.writeBoolean(auto);
        buf.writeVarInt(stations.size());
        for (Station station : stations) {
            buf.writeUtf(station.kind(), Profession.MAX_ID_LENGTH);
            buf.writeVarInt(station.count());
        }
        buf.writeItem(current);
        buf.writeVarInt(progress);
        buf.writeComponent(site);
        goal.encode(buf);
        mint.encode(buf);
    }

    public static WorkPostView decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID kingdomId = buf.readUUID();
        boolean canManage = buf.readBoolean();
        String profession = buf.readUtf(Profession.MAX_ID_LENGTH);
        if (!Profession.isValidId(profession)) {
            throw new DecoderException("Invalid profession id");
        }
        int radius = buf.readVarInt();
        int maxRadius = buf.readVarInt();
        int seats = buf.readVarInt();
        int maxSeats = buf.readVarInt();
        String animal = buf.readUtf(WorkPost.MAX_ANIMAL_LENGTH);
        int animalCount = KingdomView.readSize(buf, MAX_ANIMALS);
        List<String> animals = new ArrayList<>(animalCount);
        for (int i = 0; i < animalCount; i++) {
            animals.add(buf.readUtf(WorkPost.MAX_ANIMAL_LENGTH));
        }
        int herdSize = buf.readVarInt();
        int maxHerd = buf.readVarInt();
        WorkStatus status = KingdomView.readEnum(buf, WorkStatus.class);
        boolean container = buf.readBoolean();
        boolean measured = buf.readBoolean();
        int perDay = buf.readVarInt();
        long produced = buf.readVarLong();
        int workerCount = KingdomView.readSize(buf, MAX_WORKERS);
        List<Worker> workers = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            workers.add(new Worker(buf.readUUID(), buf.readInt(), buf.readBoolean(), KingdomView.readEnum(buf, WorkStatus.class)));
        }
        int unemployed = buf.readVarInt();
        boolean auto = buf.readBoolean();
        int stationCount = KingdomView.readSize(buf, MAX_STATIONS);
        List<Station> stations = new ArrayList<>(stationCount);
        for (int i = 0; i < stationCount; i++) {
            stations.add(new Station(buf.readUtf(Profession.MAX_ID_LENGTH), buf.readVarInt()));
        }
        ItemStack current = buf.readItem();
        int progress = buf.readVarInt();
        net.minecraft.network.chat.Component site = buf.readComponent();
        return new WorkPostView(pos, kingdomId, canManage, profession, radius, maxRadius, seats, maxSeats, animal, animals, herdSize,
                maxHerd, status, container, measured, perDay, produced, workers, unemployed, auto, stations, current, progress, site,
                Goal.decode(buf), Mint.decode(buf));
    }
}
