package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Profession;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.Whereabouts;
import io.github.sekerator.kingdomworld.core.citizen.Workforce;
import io.github.sekerator.kingdomworld.core.citizen.WorldPos;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * What the window of the workers of a kingdom shows: the numbers of its work and what it lacks, and one page of its citizens
 * or of its work posts, or the choices for one citizen (a post with a free seat or a profession without a post) or for one
 * post (the citizens who may work there). The server answers every request with one page; nothing is sent by itself.
 *
 * @param query       what was asked for, so that a late answer to an old question is recognized
 * @param total       rows of the whole list the page belongs to
 * @param citizens    the page of citizens (the list of citizens, or the candidates of a post)
 * @param posts       the page of posts (the list of posts, or the posts a citizen may go to)
 * @param professions the professions without a post a citizen may get, on the first page of its choices
 * @param forCitizen  the citizen the choices are for
 * @param forPost     the post the choices are for
 */
public record WorkforceView(Query query, UUID kingdomId, String kingdomName, Header header, int total, List<CitizenRow> citizens,
                            List<PostRow> posts, List<String> professions, @Nullable CitizenRow forCitizen, @Nullable PostRow forPost) {
    public static final int MAX_PAGE = 30;
    public static final int MAX_PLACES = 64;
    public static final int MAX_SHORTAGES = 32;
    public static final int MAX_LACKS = 6;
    public static final int MAX_CONTENTS = 3;
    public static final int MAX_PROFESSIONS = 16;
    private static final int MAX_KEY = 24;
    private static final int MAX_PLACE_NAME = 64;
    private static final int MAX_KINGDOM_NAME = 64;
    private static final int MAX_DIMENSION = 128;
    private static final int MAX_DETAIL = 32;
    /** The distance of something in another dimension or not known. */
    public static final int FAR = -1;

    /**
     * What the window shows.
     */
    public enum Mode {
        CITIZENS,
        POSTS,
        /** The posts with a free seat and the professions without a post, for one citizen. */
        CHOOSE_POST,
        /** The citizens who may work at one post. */
        CHOOSE_CITIZEN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean choice() {
            return this == CHOOSE_POST || this == CHOOSE_CITIZEN;
        }
    }

    /**
     * Which citizens the list shows and in what order: all of them as usual, the nearest to the player first, or only those gone
     * far from their home or post, the nearest first.
     */
    public enum Where {
        ALL,
        NEAREST,
        AWAY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Where a citizen is now.
     *
     * @param dimension the world it is in
     * @param pos       the place of its body, or the last place the data knows when it has none
     * @param live      whether it has a body there now; otherwise it lives far from players and that is the last place known
     * @param place     the key of the settlement or city it is in, empty outside them
     * @param away      whether it went far from its home or post
     * @param hold      what keeps it from being sent back, {@link Whereabouts.Hold#NONE} if nothing does
     * @param distance  from the player when the page was made, in blocks, {@link #FAR} in another world
     */
    public record Spot(String dimension, BlockPos pos, boolean live, String place, Whereabouts.Away away, Whereabouts.Hold hold,
                       int distance) {
        /** A citizen of a page made without places. */
        public static final Spot UNKNOWN = new Spot("", BlockPos.ZERO, false, "", Whereabouts.Away.NONE, Whereabouts.Hold.NOWHERE, FAR);

        public Spot {
            Objects.requireNonNull(dimension, "dimension");
            pos = pos.immutable();
            place = place == null ? "" : place;
            Objects.requireNonNull(away, "away");
            Objects.requireNonNull(hold, "hold");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(dimension, MAX_DIMENSION);
            buf.writeBlockPos(pos);
            buf.writeBoolean(live);
            buf.writeUtf(place, MAX_KEY);
            buf.writeEnum(away);
            buf.writeEnum(hold);
            buf.writeVarInt(distance + 1);
        }

        static Spot decode(FriendlyByteBuf buf) {
            return new Spot(buf.readUtf(MAX_DIMENSION), buf.readBlockPos(), buf.readBoolean(), buf.readUtf(MAX_KEY),
                    KingdomView.readEnum(buf, Whereabouts.Away.class), KingdomView.readEnum(buf, Whereabouts.Hold.class), buf.readVarInt() - 1);
        }

        public WorldPos worldPos() {
            return new WorldPos(dimension, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * A work post by its place in the world.
     */
    public record PostRef(String dimension, BlockPos pos) {
        public PostRef {
            Objects.requireNonNull(dimension, "dimension");
            pos = pos.immutable();
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(dimension, MAX_DIMENSION);
            buf.writeBlockPos(pos);
        }

        static PostRef decode(FriendlyByteBuf buf) {
            return new PostRef(buf.readUtf(MAX_DIMENSION), buf.readBlockPos());
        }
    }

    /**
     * What the window asks for.
     *
     * @param place      the settlement or city whose citizens or posts are listed ({@code s<id>}, {@code c<id>}, {@code -} for
     *                   none), empty for all
     * @param profession the profession listed, empty for all
     * @param problems   whether only those with a problem are listed
     * @param page       the page, from 0
     * @param pageSize   rows on a page
     * @param citizen    the citizen of {@link Mode#CHOOSE_POST}; in {@link Mode#CITIZENS} the citizen whose page is shown, the window
     *                   opened by a click on it
     * @param post       the post of {@link Mode#CHOOSE_CITIZEN}
     */
    public record Query(Mode mode, String place, String profession, boolean problems, int page, int pageSize, @Nullable UUID citizen,
                        @Nullable PostRef post, Where where) {
        public Query {
            Objects.requireNonNull(mode, "mode");
            place = place == null ? "" : place;
            profession = profession == null ? "" : profession;
            page = Math.max(0, page);
            pageSize = Math.max(1, Math.min(MAX_PAGE, pageSize));
            where = where == null ? Where.ALL : where;
        }

        public Query(Mode mode, String place, String profession, boolean problems, int page, int pageSize, @Nullable UUID citizen,
                     @Nullable PostRef post) {
            this(mode, place, profession, problems, page, pageSize, citizen, post, Where.ALL);
        }

        /**
         * The same list in another order, or of those gone far only, from its first page.
         */
        public Query withWhere(Where value) {
            return new Query(mode, place, profession, problems, 0, pageSize, citizen, post, value);
        }

        public static Query of(Mode mode, int pageSize) {
            return new Query(mode, "", "", false, 0, pageSize, null, null);
        }

        public Query withPage(int value) {
            return new Query(mode, place, profession, problems, value, pageSize, citizen, post, where);
        }

        public Query withPageSize(int value) {
            return new Query(mode, place, profession, problems, page, value, citizen, post, where);
        }

        public Query withFilters(String newPlace, String newProfession, boolean newProblems) {
            return new Query(mode, newPlace, newProfession, newProblems, 0, pageSize, citizen, post, where);
        }

        /**
         * The same list without the citizen it was opened at, once the window has chosen it.
         */
        public Query withoutFocus() {
            return mode == Mode.CITIZENS && citizen != null ? new Query(mode, place, profession, problems, page, pageSize, null, post, where) : this;
        }

        public static Query choosePost(UUID citizen, int pageSize) {
            return new Query(Mode.CHOOSE_POST, "", "", false, 0, pageSize, citizen, null);
        }

        public static Query chooseCitizen(PostRef post, int pageSize) {
            return new Query(Mode.CHOOSE_CITIZEN, "", "", false, 0, pageSize, null, post);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeEnum(mode);
            buf.writeUtf(place, MAX_KEY);
            buf.writeUtf(profession, Profession.MAX_ID_LENGTH);
            buf.writeBoolean(problems);
            buf.writeVarInt(page);
            buf.writeVarInt(pageSize);
            buf.writeBoolean(citizen != null);
            if (citizen != null) {
                buf.writeUUID(citizen);
            }
            buf.writeBoolean(post != null);
            if (post != null) {
                post.encode(buf);
            }
            buf.writeEnum(where);
        }

        public static Query decode(FriendlyByteBuf buf) {
            Mode mode = KingdomView.readEnum(buf, Mode.class);
            String place = buf.readUtf(MAX_KEY);
            String profession = buf.readUtf(Profession.MAX_ID_LENGTH);
            if (!profession.isEmpty() && !Profession.isValidId(profession)) {
                throw new DecoderException("Invalid profession id");
            }
            boolean problems = buf.readBoolean();
            int page = buf.readVarInt();
            int pageSize = buf.readVarInt();
            UUID citizen = buf.readBoolean() ? buf.readUUID() : null;
            PostRef post = buf.readBoolean() ? PostRef.decode(buf) : null;
            return new Query(mode, place, profession, problems, page, pageSize, citizen, post, KingdomView.readEnum(buf, Where.class));
        }
    }

    /**
     * A settlement or city of the kingdom, a choice of the filter.
     *
     * @param key {@code s<id>} for a settlement of a state of the world, {@code c<id>} for a city ({@code c0} the capital)
     */
    public record Place(String key, String latin, String cyrillic) {
        public Place {
            Objects.requireNonNull(key, "key");
            latin = latin == null ? "" : latin;
            cyrillic = cyrillic == null ? "" : cyrillic;
        }
    }

    /**
     * Something the citizens of the kingdom lack, and how many of them.
     *
     * @param detail what exactly, for a tool its kind ({@code axe}), otherwise empty
     */
    public record Shortage(Workforce.Need need, String detail, int count) {
        public Shortage {
            Objects.requireNonNull(need, "need");
            detail = detail == null ? "" : detail;
        }
    }

    /**
     * The numbers of the kingdom's work.
     *
     * @param foodDays   whole days the storehouses feed everybody, {@link CitizenView#UNLIMITED_FOOD} if citizens need no food
     * @param postCount  work posts of the kingdom
     * @param emptyPosts posts without a profession
     * @param freeSeats  free seats at the posts with a profession
     */
    public record Header(int working, int unemployed, int withoutPost, int soldiers, int foodDays, List<Shortage> shortages, List<Place> places,
                         int postCount, int emptyPosts, int freeSeats) {
        public static final Header EMPTY = new Header(0, 0, 0, 0, CitizenView.UNLIMITED_FOOD, List.of(), List.of(), 0, 0, 0);

        public Header {
            shortages = List.copyOf(shortages);
            places = List.copyOf(places);
            if (shortages.size() > MAX_SHORTAGES || places.size() > MAX_PLACES) {
                throw new IllegalArgumentException("Header of the workers too large");
            }
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(working);
            buf.writeVarInt(unemployed);
            buf.writeVarInt(withoutPost);
            buf.writeVarInt(soldiers);
            buf.writeVarInt(foodDays + 1);
            buf.writeVarInt(shortages.size());
            for (Shortage shortage : shortages) {
                buf.writeEnum(shortage.need());
                buf.writeUtf(shortage.detail(), MAX_DETAIL);
                buf.writeVarInt(shortage.count());
            }
            buf.writeVarInt(places.size());
            for (Place place : places) {
                buf.writeUtf(place.key(), MAX_KEY);
                buf.writeUtf(place.latin(), MAX_PLACE_NAME);
                buf.writeUtf(place.cyrillic(), MAX_PLACE_NAME);
            }
            buf.writeVarInt(postCount);
            buf.writeVarInt(emptyPosts);
            buf.writeVarInt(freeSeats);
        }

        static Header decode(FriendlyByteBuf buf) {
            int working = buf.readVarInt();
            int unemployed = buf.readVarInt();
            int withoutPost = buf.readVarInt();
            int soldiers = buf.readVarInt();
            int foodDays = buf.readVarInt() - 1;
            int shortageCount = KingdomView.readSize(buf, MAX_SHORTAGES);
            List<Shortage> shortages = new ArrayList<>(shortageCount);
            for (int i = 0; i < shortageCount; i++) {
                shortages.add(new Shortage(KingdomView.readEnum(buf, Workforce.Need.class), buf.readUtf(MAX_DETAIL), buf.readVarInt()));
            }
            int placeCount = KingdomView.readSize(buf, MAX_PLACES);
            List<Place> places = new ArrayList<>(placeCount);
            for (int i = 0; i < placeCount; i++) {
                places.add(new Place(buf.readUtf(MAX_KEY), buf.readUtf(MAX_PLACE_NAME), buf.readUtf(MAX_PLACE_NAME)));
            }
            return new Header(working, unemployed, withoutPost, soldiers, foodDays, shortages, places, buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt());
        }
    }

    /**
     * Something a citizen lacks.
     *
     * @param detail what exactly, for a tool its kind ({@code axe}), otherwise empty
     */
    public record Lack(Workforce.Need need, String detail) {
        public Lack {
            Objects.requireNonNull(need, "need");
            detail = detail == null ? "" : detail;
        }
    }

    /**
     * Where a citizen works.
     *
     * @param post     whether it is a work post; otherwise a place of the profession's own (a farmer's composter)
     * @param name     the name the post was given, empty for its default name
     * @param number   the number of the post among the posts of its profession
     * @param distance blocks from the viewer, {@link #FAR} in another dimension
     */
    public record Workplace(PostRef ref, boolean post, String name, int number, String profession, int distance) {
        public Workplace {
            Objects.requireNonNull(ref, "ref");
            name = name == null ? "" : name;
            Objects.requireNonNull(profession, "profession");
        }
    }

    /**
     * One citizen.
     *
     * @param place     the settlement or city it lives in (a key of {@link Place}), empty if none
     * @param workplace where it works, {@code null} if nowhere
     * @param candidate how it stands to the post of the choices, otherwise how it stands to its own work
     * @param distance  for the choices of a post: blocks from the post, {@link #FAR} if far away; otherwise from the viewer
     */
    public record CitizenRow(UUID id, int seed, boolean female, String profession, CitizenState state, int health, int maxHealth, int mood,
                             String place, @Nullable Workplace workplace, Workforce.Doing doing, WorkStatus status, List<Lack> lacks,
                             Workforce.Candidate candidate, int distance, Spot spot) {
        public CitizenRow(UUID id, int seed, boolean female, String profession, CitizenState state, int health, int maxHealth, int mood,
                          String place, @Nullable Workplace workplace, Workforce.Doing doing, WorkStatus status, List<Lack> lacks,
                          Workforce.Candidate candidate, int distance) {
            this(id, seed, female, profession, state, health, maxHealth, mood, place, workplace, doing, status, lacks, candidate, distance,
                    Spot.UNKNOWN);
        }

        public CitizenRow {
            Objects.requireNonNull(spot, "spot");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(profession, "profession");
            Objects.requireNonNull(state, "state");
            place = place == null ? "" : place;
            Objects.requireNonNull(doing, "doing");
            Objects.requireNonNull(status, "status");
            lacks = List.copyOf(lacks.size() > MAX_LACKS ? lacks.subList(0, MAX_LACKS) : lacks);
            Objects.requireNonNull(candidate, "candidate");
        }

        public boolean soldier() {
            return candidate == Workforce.Candidate.SOLDIER;
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeInt(seed);
            buf.writeBoolean(female);
            buf.writeUtf(profession, Profession.MAX_ID_LENGTH);
            buf.writeEnum(state);
            buf.writeVarInt(health);
            buf.writeVarInt(maxHealth);
            buf.writeVarInt(mood);
            buf.writeUtf(place, MAX_KEY);
            buf.writeBoolean(workplace != null);
            if (workplace != null) {
                workplace.ref().encode(buf);
                buf.writeBoolean(workplace.post());
                buf.writeUtf(workplace.name(), WorkPost.MAX_NAME_LENGTH);
                buf.writeVarInt(workplace.number());
                buf.writeUtf(workplace.profession(), Profession.MAX_ID_LENGTH);
                buf.writeVarInt(workplace.distance() + 1);
            }
            buf.writeEnum(doing);
            buf.writeEnum(status);
            buf.writeVarInt(lacks.size());
            for (Lack lack : lacks) {
                buf.writeEnum(lack.need());
                buf.writeUtf(lack.detail(), MAX_DETAIL);
            }
            buf.writeEnum(candidate);
            buf.writeVarInt(distance + 1);
            spot.encode(buf);
        }

        static CitizenRow decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            int seed = buf.readInt();
            boolean female = buf.readBoolean();
            String profession = CitizenView.readProfession(buf);
            CitizenState state = KingdomView.readEnum(buf, CitizenState.class);
            int health = buf.readVarInt();
            int maxHealth = buf.readVarInt();
            int mood = buf.readVarInt();
            String place = buf.readUtf(MAX_KEY);
            Workplace workplace = null;
            if (buf.readBoolean()) {
                PostRef ref = PostRef.decode(buf);
                boolean post = buf.readBoolean();
                String name = buf.readUtf(WorkPost.MAX_NAME_LENGTH);
                int number = buf.readVarInt();
                String workProfession = CitizenView.readProfession(buf);
                workplace = new Workplace(ref, post, name, number, workProfession, buf.readVarInt() - 1);
            }
            Workforce.Doing doing = KingdomView.readEnum(buf, Workforce.Doing.class);
            WorkStatus status = KingdomView.readEnum(buf, WorkStatus.class);
            int lackCount = KingdomView.readSize(buf, MAX_LACKS);
            List<Lack> lacks = new ArrayList<>(lackCount);
            for (int i = 0; i < lackCount; i++) {
                lacks.add(new Lack(KingdomView.readEnum(buf, Workforce.Need.class), buf.readUtf(MAX_DETAIL)));
            }
            Workforce.Candidate candidate = KingdomView.readEnum(buf, Workforce.Candidate.class);
            return new CitizenRow(id, seed, female, profession, state, health, maxHealth, mood, place, workplace, doing, status, lacks, candidate,
                    buf.readVarInt() - 1, Spot.decode(buf));
        }
    }

    /**
     * Items of one kind in the container of a post.
     */
    public record Content(ItemStack item, int count) {
        public Content {
            item = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
            count = Math.max(0, count);
        }
    }

    /**
     * One work post.
     *
     * @param name     the name it was given, empty for its default name (its profession and number)
     * @param number   its number among the posts of its profession
     * @param place    the settlement or city it stands in (a key of {@link Place}), empty if none
     * @param distance blocks from the viewer, or from the citizen of the choices; {@link #FAR} in another dimension
     * @param loaded   whether its surroundings are loaded, so that its container could be looked into
     * @param contents what its container holds most of, at most {@link #MAX_CONTENTS} kinds
     * @param items    items in its container in all
     */
    public record PostRow(PostRef ref, String name, int number, String profession, String place, int distance, int workers, int seats,
                          WorkStatus status, boolean loaded, boolean container, List<Content> contents, int items) {
        public PostRow {
            Objects.requireNonNull(ref, "ref");
            name = name == null ? "" : name;
            Objects.requireNonNull(profession, "profession");
            place = place == null ? "" : place;
            Objects.requireNonNull(status, "status");
            contents = List.copyOf(contents.size() > MAX_CONTENTS ? contents.subList(0, MAX_CONTENTS) : contents);
        }

        public int freeSeats() {
            return Math.max(0, seats - workers);
        }

        void encode(FriendlyByteBuf buf) {
            ref.encode(buf);
            buf.writeUtf(name, WorkPost.MAX_NAME_LENGTH);
            buf.writeVarInt(number);
            buf.writeUtf(profession, Profession.MAX_ID_LENGTH);
            buf.writeUtf(place, MAX_KEY);
            buf.writeVarInt(distance + 1);
            buf.writeVarInt(workers);
            buf.writeVarInt(seats);
            buf.writeEnum(status);
            buf.writeBoolean(loaded);
            buf.writeBoolean(container);
            buf.writeVarInt(contents.size());
            for (Content content : contents) {
                buf.writeItem(content.item());
                buf.writeVarInt(content.count());
            }
            buf.writeVarInt(items);
        }

        static PostRow decode(FriendlyByteBuf buf) {
            PostRef ref = PostRef.decode(buf);
            String name = buf.readUtf(WorkPost.MAX_NAME_LENGTH);
            int number = buf.readVarInt();
            String profession = CitizenView.readProfession(buf);
            String place = buf.readUtf(MAX_KEY);
            int distance = buf.readVarInt() - 1;
            int workers = buf.readVarInt();
            int seats = buf.readVarInt();
            WorkStatus status = KingdomView.readEnum(buf, WorkStatus.class);
            boolean loaded = buf.readBoolean();
            boolean container = buf.readBoolean();
            int count = KingdomView.readSize(buf, MAX_CONTENTS);
            List<Content> contents = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                contents.add(new Content(buf.readItem(), buf.readVarInt()));
            }
            return new PostRow(ref, name, number, profession, place, distance, workers, seats, status, loaded, container, contents, buf.readVarInt());
        }
    }

    public WorkforceView {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(kingdomId, "kingdomId");
        kingdomName = kingdomName == null ? "" : kingdomName;
        Objects.requireNonNull(header, "header");
        citizens = List.copyOf(citizens);
        posts = List.copyOf(posts);
        professions = List.copyOf(professions);
        if (citizens.size() > MAX_PAGE || posts.size() > MAX_PAGE || professions.size() > MAX_PROFESSIONS) {
            throw new IllegalArgumentException("Page of the workers too large");
        }
    }

    /**
     * Pages of the whole list.
     */
    public int pages() {
        return Math.max(1, (total + query.pageSize() - 1) / query.pageSize());
    }

    public void encode(FriendlyByteBuf buf) {
        query.encode(buf);
        buf.writeUUID(kingdomId);
        buf.writeUtf(kingdomName, MAX_KINGDOM_NAME);
        header.encode(buf);
        buf.writeVarInt(total);
        buf.writeVarInt(citizens.size());
        for (CitizenRow row : citizens) {
            row.encode(buf);
        }
        buf.writeVarInt(posts.size());
        for (PostRow row : posts) {
            row.encode(buf);
        }
        buf.writeVarInt(professions.size());
        for (String profession : professions) {
            buf.writeUtf(profession, Profession.MAX_ID_LENGTH);
        }
        buf.writeBoolean(forCitizen != null);
        if (forCitizen != null) {
            forCitizen.encode(buf);
        }
        buf.writeBoolean(forPost != null);
        if (forPost != null) {
            forPost.encode(buf);
        }
    }

    public static WorkforceView decode(FriendlyByteBuf buf) {
        Query query = Query.decode(buf);
        UUID kingdomId = buf.readUUID();
        String kingdomName = buf.readUtf(MAX_KINGDOM_NAME);
        Header header = Header.decode(buf);
        int total = buf.readVarInt();
        int citizenCount = KingdomView.readSize(buf, MAX_PAGE);
        List<CitizenRow> citizens = new ArrayList<>(citizenCount);
        for (int i = 0; i < citizenCount; i++) {
            citizens.add(CitizenRow.decode(buf));
        }
        int postCount = KingdomView.readSize(buf, MAX_PAGE);
        List<PostRow> posts = new ArrayList<>(postCount);
        for (int i = 0; i < postCount; i++) {
            posts.add(PostRow.decode(buf));
        }
        int professionCount = KingdomView.readSize(buf, MAX_PROFESSIONS);
        List<String> professions = new ArrayList<>(professionCount);
        for (int i = 0; i < professionCount; i++) {
            professions.add(CitizenView.readProfession(buf));
        }
        CitizenRow forCitizen = buf.readBoolean() ? CitizenRow.decode(buf) : null;
        PostRow forPost = buf.readBoolean() ? PostRow.decode(buf) : null;
        return new WorkforceView(query, kingdomId, kingdomName, header, total, citizens, posts, professions, forCitizen, forPost);
    }
}
