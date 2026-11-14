package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.RenamePostScreen;
import io.github.sekerator.kingdomworld.client.screen.WorkforceScreen;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.network.data.WorkforceView;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.citizen.WorkPost;
import io.github.sekerator.kingdomworld.core.citizen.WorkStatus;
import io.github.sekerator.kingdomworld.core.citizen.Workforce;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Layout cases of the window of the workers for the client self-check: the citizens with every kind of work and lack, the posts
 * with long names, far away and with full containers, the choices of a post and of a citizen, the filters, an empty list, a
 * window still waiting for its page, the renaming of a post and the question before a worker moves over.
 */
final class WorkforceLayoutCases {
    private static final UUID KINGDOM = UUID.nameUUIDFromBytes("workforce".getBytes(StandardCharsets.UTF_8));
    private static final String LONG_PLACE = "Великое Северное Королевство Щит";
    private static final String LONG_LATIN = "Serebrozemye-nad-Rekoyu XIV";
    private static final String LONG_CYRILLIC = "Сереброземье-над-Рекою XIV";
    private static final String LONG_POST = "Северные делянки у старой мельни";
    private static final String DIMENSION = "minecraft:overworld";

    private WorkforceLayoutCases() {
    }

    static void cases(BiConsumer<String, Supplier<Screen>> add) {
        add.accept("workforce-citizens", () -> screen(citizens(WorkforceView.Query.of(WorkforceView.Mode.CITIZENS, 30)), Component.empty(), true));
        add.accept("workforce-citizens-filters", () -> screen(citizens(new WorkforceView.Query(WorkforceView.Mode.CITIZENS, "s7", "registrar",
                true, 2, 6, null, null)), Component.translatable("kingdomworld.workforce.dismissed",
                CitizenTexts.name(longestSeed(Gender.FEMALE), Gender.FEMALE)), true));
        add.accept("workforce-citizens-empty", () -> screen(new WorkforceView(new WorkforceView.Query(WorkforceView.Mode.CITIZENS, "-", "mage",
                true, 0, 6, null, null), KINGDOM, LONG_PLACE, header(false), 0, List.of(), List.of(), List.of(), null, null), Component.empty(), true));
        add.accept("workforce-posts", () -> screen(posts(WorkforceView.Query.of(WorkforceView.Mode.POSTS, 30), false), Component.empty(), true));
        add.accept("workforce-posts-error", () -> screen(posts(new WorkforceView.Query(WorkforceView.Mode.POSTS, "c0", "unemployed", true, 0, 6,
                null, null), false), Component.translatable("kingdomworld.error.no_free_seat"), false));
        add.accept("workforce-choose-post", () -> screen(choosePost(), Component.empty(), true));
        add.accept("workforce-choose-citizen", () -> screen(chooseCitizen(), Component.empty(), true));
        add.accept("workforce-choose-citizen-error", () -> screen(chooseCitizen(),
                Component.translatable("kingdomworld.error.army_profession"), false));
        add.accept("workforce-loading", () -> WorkforceScreen.chooseCitizen(null, KINGDOM, new WorkforceView.PostRef(DIMENSION, BlockPos.ZERO)));
        add.accept("workforce-rename", () -> new RenamePostScreen(new WorkforceScreen(null, posts(WorkforceView.Query.of(WorkforceView.Mode.POSTS, 6),
                false)), new WorkforceView.PostRef(DIMENSION, BlockPos.ZERO), LONG_POST, WorkforceScreen.postName("", "lumberjack", 99).getString()));
        add.accept("workforce-confirm-transfer", () -> new ConfirmScreen(yes -> {
        }, Component.translatable("kingdomworld.screen.workforce.transfer.title"), Component.translatable("kingdomworld.screen.workforce.transfer.question",
                CitizenTexts.name(longestSeed(Gender.MALE), Gender.MALE), Component.literal(LONG_POST), WorkforceScreen.postName("", "registrar", 99),
                CitizenTexts.profession("registrar", Gender.MALE)), Component.translatable("kingdomworld.screen.workforce.transfer.yes"),
                Component.translatable("gui.cancel")));
    }

    /**
     * The window of a post with a long name of its own, opened from the window of the workers (it goes back there) or at the
     * post; and one without a name, which is called by its profession and number.
     */
    static void posts(BiConsumer<String, Supplier<Screen>> add,
                      java.util.function.BiFunction<String, Boolean, io.github.sekerator.kingdomworld.common.network.data.WorkPostView> view) {
        add.accept("post-named-remote", () -> new io.github.sekerator.kingdomworld.client.screen.WorkPostScreen(view.apply("miner", true)
                .withPlace(new io.github.sekerator.kingdomworld.common.network.data.WorkPostView.Place(DIMENSION,
                        LONG_POST.substring(0, WorkPost.MAX_NAME_LENGTH), 99)), screen(citizens(WorkforceView.Query.of(WorkforceView.Mode.POSTS, 6)),
                Component.empty(), true)));
        add.accept("post-numbered", () -> new io.github.sekerator.kingdomworld.client.screen.WorkPostScreen(view.apply("lumberjack", true)
                .withPlace(new io.github.sekerator.kingdomworld.common.network.data.WorkPostView.Place(DIMENSION, "", 99))));
        add.accept("post-minter-remote", () -> new io.github.sekerator.kingdomworld.client.screen.WorkPostScreen(view.apply("minter", true)
                .withPlace(new io.github.sekerator.kingdomworld.common.network.data.WorkPostView.Place(DIMENSION, "", 99)),
                screen(posts(WorkforceView.Query.of(WorkforceView.Mode.POSTS, 6), false), Component.empty(), true)));
    }

    private static WorkforceScreen screen(WorkforceView view, Component status, boolean success) {
        WorkforceScreen screen = new WorkforceScreen(null, view);
        if (!status.getString().isEmpty()) {
            screen.onResult(success, status);
        }
        return screen;
    }

    /**
     * A seed that gives one of the longest names in the current language.
     */
    private static int longestSeed(Gender gender) {
        CitizenNames.Alphabet alphabet = CitizenNames.alphabetFor(Minecraft.getInstance().getLanguageManager().getSelected());
        int best = 0;
        int bestWidth = -1;
        for (int seed = 0; seed < 4_000; seed++) {
            int width = Minecraft.getInstance().font.width(CitizenNames.render(seed, gender, alphabet));
            if (width > bestWidth) {
                best = seed;
                bestWidth = width;
            }
        }
        return best;
    }

    /**
     * A kingdom that lacks everything there is: every tool, food for a single day, and settlements with long names.
     */
    private static WorkforceView.Header header(boolean everything) {
        List<WorkforceView.Shortage> shortages = new ArrayList<>();
        if (everything) {
            int count = 1;
            for (String tool : List.of("axe", "pickaxe", "fishing_rod", "shears", "weapon")) {
                shortages.add(new WorkforceView.Shortage(Workforce.Need.TOOL, tool, 9_999 - count));
                shortages.add(new WorkforceView.Shortage(Workforce.Need.BETTER_TOOL, tool, count));
                count += 20;
            }
            for (Workforce.Need need : Workforce.Need.values()) {
                if (need != Workforce.Need.TOOL && need != Workforce.Need.BETTER_TOOL) {
                    shortages.add(new WorkforceView.Shortage(need, "", count++));
                }
            }
        }
        List<WorkforceView.Place> places = List.of(new WorkforceView.Place("c0", LONG_LATIN, LONG_CYRILLIC),
                new WorkforceView.Place("c12", LONG_PLACE, LONG_PLACE), new WorkforceView.Place("s7", LONG_LATIN + " II", LONG_CYRILLIC + " II"));
        return new WorkforceView.Header(9_999, 9_999, 9_999, 9_999, everything ? 1 : 9_999, shortages, places, 9_999, 9_999, 9_999);
    }

    private static WorkforceView.PostRef far(int i) {
        return new WorkforceView.PostRef(DIMENSION, new BlockPos(-29_999_000 + i, 319, 29_999_999));
    }

    private static List<WorkforceView.CitizenRow> citizenRows(boolean candidates) {
        List<WorkforceView.CitizenRow> rows = new ArrayList<>();
        String[] professions = {"registrar", "unemployed", "lumberjack", "warrior", "miner", "farmer", "adventurer", "dragon_tamer", "smelter",
                "innkeeper", "carrier", "mage"};
        Workforce.Doing[] doings = Workforce.Doing.values();
        WorkStatus[] statuses = WorkStatus.values();
        Workforce.Candidate[] kinds = {Workforce.Candidate.IDLE_SAME, Workforce.Candidate.UNEMPLOYED, Workforce.Candidate.SAME_ELSEWHERE,
                Workforce.Candidate.OTHER_WORK};
        for (int i = 0; i < WorkforceView.MAX_PAGE; i++) {
            Gender gender = i % 2 == 0 ? Gender.FEMALE : Gender.MALE;
            String profession = candidates && professions[i % professions.length].equals("warrior") ? "smith" : professions[i % professions.length];
            List<WorkforceView.Lack> lacks = new ArrayList<>();
            if (i % 3 != 2) {
                lacks.add(new WorkforceView.Lack(Workforce.Need.TOOL, i % 2 == 0 ? "fishing_rod" : "pickaxe"));
                lacks.add(new WorkforceView.Lack(Workforce.Need.values()[i % Workforce.Need.values().length], ""));
                lacks.add(new WorkforceView.Lack(Workforce.Need.STORAGE_SPACE, ""));
            }
            WorkforceView.Workplace workplace = i % 4 == 1 ? null : new WorkforceView.Workplace(far(i), i % 5 != 3, i % 6 == 0 ? LONG_POST : "",
                    99, profession, i % 7 == 6 ? WorkforceView.FAR : 99_999);
            Workforce.Candidate candidate = candidates ? kinds[i % kinds.length]
                    : profession.equals("warrior") ? Workforce.Candidate.SOLDIER : Workforce.Candidate.OTHER_WORK;
            rows.add(new WorkforceView.CitizenRow(UUID.nameUUIDFromBytes(("workforce" + i).getBytes(StandardCharsets.UTF_8)),
                    i < 2 ? longestSeed(gender) : i * 7919, gender == Gender.FEMALE, profession,
                    i % 3 == 1 ? CitizenState.WOUNDED : CitizenState.HEALTHY, 1024 - i, 1024, i * 7 % 101, i % 2 == 0 ? "c12" : "s7", workplace,
                    doings[i % doings.length], statuses[i % statuses.length], lacks, candidate, i % 7 == 6 ? WorkforceView.FAR : 99_999,
                    new WorkforceView.Spot(i % 5 == 4 ? "minecraft:the_nether" : "minecraft:overworld", new BlockPos(-29_999_999 + i, 319, 29_999_999),
                            i % 3 != 0, i % 2 == 0 ? "c12" : "", io.github.sekerator.kingdomworld.core.citizen.Whereabouts.Away.values()[i % 3],
                            io.github.sekerator.kingdomworld.core.citizen.Whereabouts.Hold.values()[i % 5], 99_999)));
        }
        return rows;
    }

    private static WorkforceView citizens(WorkforceView.Query query) {
        return new WorkforceView(query, KINGDOM, LONG_PLACE, header(true), 9_999, citizenRows(false), List.of(), List.of(), null, null);
    }

    private static List<WorkforceView.PostRow> postRows(boolean free) {
        List<WorkforceView.PostRow> rows = new ArrayList<>();
        String[] professions = {"registrar", "unemployed", "lumberjack", "miner", "smelter", "builder", "carrier", "herder"};
        WorkStatus[] statuses = WorkStatus.values();
        for (int i = 0; i < WorkforceView.MAX_PAGE; i++) {
            String profession = professions[i % professions.length];
            List<WorkforceView.Content> contents = new ArrayList<>();
            if (i % 4 == 0) {
                ItemStack named = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
                named.setHoverName(Component.literal(LONG_PLACE));
                contents.add(new WorkforceView.Content(named, 99_999));
                contents.add(new WorkforceView.Content(new ItemStack(Items.SPRUCE_SAPLING), 9_999));
                contents.add(new WorkforceView.Content(new ItemStack(Items.COBBLESTONE), 64));
            }
            boolean loaded = i % 3 != 2;
            rows.add(new WorkforceView.PostRow(far(i), i % 5 == 0 ? LONG_POST.substring(0, WorkPost.MAX_NAME_LENGTH) : "", 99, profession,
                    i % 2 == 0 ? "c12" : i % 3 == 0 ? "" : "s7", i % 7 == 6 ? WorkforceView.FAR : 99_999, free ? 1 : i % 17, 16,
                    statuses[i % statuses.length], loaded, i % 4 != 1, contents, i % 4 == 0 ? 999_999 : 0));
        }
        return rows;
    }

    private static WorkforceView posts(WorkforceView.Query query, boolean free) {
        return new WorkforceView(query, KINGDOM, LONG_PLACE, header(true), 9_999, List.of(), postRows(free), List.of(), null, null);
    }

    private static WorkforceView choosePost() {
        UUID citizen = UUID.nameUUIDFromBytes("workforce0".getBytes(StandardCharsets.UTF_8));
        List<String> professions = new ArrayList<>();
        for (var profession : Workforce.professionsWithoutPost()) {
            professions.add(profession.id());
        }
        return new WorkforceView(WorkforceView.Query.choosePost(citizen, 30), KINGDOM, LONG_PLACE, header(true), 9_999, List.of(), postRows(true),
                professions, citizenRows(false).get(0), null);
    }

    private static WorkforceView chooseCitizen() {
        WorkforceView.PostRow post = postRows(false).get(0);
        return new WorkforceView(WorkforceView.Query.chooseCitizen(post.ref(), 30), KINGDOM, LONG_PLACE, header(true), 9_999, citizenRows(true),
                List.of(), List.of(), null, post);
    }
}
