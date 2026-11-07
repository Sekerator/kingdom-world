package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.GuildDeskScreen;
import io.github.sekerator.kingdomworld.client.screen.ItemTooltipScreen;
import io.github.sekerator.kingdomworld.client.screen.LayoutCheck;
import io.github.sekerator.kingdomworld.client.screen.ThroneScreen;
import io.github.sekerator.kingdomworld.common.guild.GuildTexts;
import io.github.sekerator.kingdomworld.common.guild.MonsterCrystalItem;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.GuildViews;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.CitizenState;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The screens and messages of the guild in the layout check: the desk in every view and state, the guild tab of the
 * throne screen, the tooltips of crystals and the lines above the hotbar. Views carry the largest numbers, the longest
 * names and more rows than fit.
 */
final class GuildLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_PLAYER = "WWWWWWWWWWWWWWWW";
    private static final String LONG_CURRENCY = "Великая Северная Крона Щит";
    private static final long MUCH = 999_999_999_999L;
    private static final UUID BRANCH = UUID.nameUUIDFromBytes("guild".getBytes(StandardCharsets.UTF_8));
    private static final BlockPos FAR = new BlockPos(-30_000_000, 319, 29_999_999);
    /** Monsters with long names, of the game. */
    private static final String[] MONSTERS = {"minecraft:zombified_piglin", "minecraft:elder_guardian", "minecraft:wither_skeleton",
            "minecraft:cave_spider", "minecraft:ender_dragon", "minecraft:piglin_brute"};

    private GuildLayoutCases() {
    }

    private static TradeViews.Currency currency() {
        return new TradeViews.Currency(BRANCH, LONG_CURRENCY, LONG_KINGDOM, 0x6C3483, 10, 100, 9_990_000L);
    }

    /**
     * Lines above the hotbar: progress of a contract with the longest task, and the desk refusing.
     */
    static void actionbars(Font font, int narrowest) {
        Rank[] ranks = Rank.values();
        for (String monster : MONSTERS) {
            Contract hunt = new Contract(1, Contract.Kind.HUNT, BRANCH, Contract.Poster.GUILD, BRANCH, Rank.F, monster, Rank.SSS, false, 4_096, 4_095,
                    BRANCH, 0, 0, 0, 0, 1, Contract.Status.OPEN, null, 0, true);
            LayoutCheck.actionbar(font, Component.translatable("kingdomworld.guild.progress", GuildTexts.shortTarget(hunt), 4_095, 4_096), narrowest);
        }
        for (Rank rank : ranks) {
            Contract exam = new Contract(1, Contract.Kind.EXAM, BRANCH, Contract.Poster.GUILD, BRANCH, Rank.F, "", rank, rank.atLeast(Rank.S), 4_096,
                    4_095, BRANCH, 0, 0, 0, 0, 1, Contract.Status.OPEN, null, 0, false);
            LayoutCheck.actionbar(font, Component.translatable("kingdomworld.guild.progress", GuildTexts.shortTarget(exam), 4_095, 4_096), narrowest);
        }
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.guild.desk.outside"), narrowest);
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.guild.desk.read_only"), narrowest);
        LayoutCheck.actionbar(font, KingdomActions.errorMessage(KingdomError.BRANCH_CLOSED), narrowest);
    }

    private static List<GuildViews.ContractRow> contracts(boolean mine, int count) {
        List<GuildViews.ContractRow> rows = new ArrayList<>();
        Contract.Kind[] kinds = {Contract.Kind.HUNT, Contract.Kind.GATHER, Contract.Kind.BOUNTY, Contract.Kind.HUNT};
        Contract.Poster[] posters = Contract.Poster.values();
        for (int i = 0; i < count; i++) {
            Contract.Kind kind = mine && i == 0 ? Contract.Kind.EXAM : kinds[i % kinds.length];
            String target = switch (kind) {
                case GATHER -> i % 2 == 0 ? "minecraft:netherite_upgrade_smithing_template" : "minecraft:light_blue_glazed_terracotta";
                case HUNT -> i % 4 == 3 ? "" : MONSTERS[i % MONSTERS.length];
                case BOUNTY -> MONSTERS[(i + 3) % MONSTERS.length];
                default -> "";
            };
            Rank rank = Rank.ofIndex(i % Rank.COUNT);
            ItemStack icon = GuildTexts.icon(kind, target, rank);
            boolean taken = !mine && i % 3 == 2;
            rows.add(new GuildViews.ContractRow(i + 1, kind, target, kind == Contract.Kind.EXAM ? Rank.SSS : rank, kind == Contract.Kind.EXAM,
                    4_096, mine ? 4_095 : 0, Rank.ofIndex((i + 2) % Rank.COUNT), MUCH, 999_999, 300, 23_999L * 7 - i * 1_000L, posters[i % 3],
                    i % 3 == 0 ? LONG_KINGDOM : LONG_PLAYER, mine && i != 3, taken || mine, mine && i == 3, i % 5 == 1, i % 2 == 0, 0, icon));
        }
        return rows;
    }

    private static GuildViews.Card card(GuildViews.ExamState exam) {
        return new GuildViews.Card(true, Rank.SS, 999_999_999L, exam == GuildViews.ExamState.TOP ? -1 : 1_999_999_999L, 999_999, 99_999, exam,
                23_999L * 3, 4_095, 4_096, true);
    }

    private static GuildViews.DeskView desk(GuildViews.Card card, boolean empty, boolean kingdom) {
        return new GuildViews.DeskView(FAR, BRANCH, LONG_KINGDOM, 0x6C3483, currency(), card, empty ? List.of() : contracts(false, 24),
                empty ? List.of() : contracts(true, 8), MUCH, kingdom, 300, 20, MUCH, 3, 7, 4_096, MUCH);
    }

    private static GuildDeskScreen deskScreen(GuildViews.DeskView view, String mode, Component status, boolean success) {
        GuildDeskScreen screen = new GuildDeskScreen(view);
        screen.showForCheck(mode, "", ItemStack.EMPTY, Component.empty(), "4096", "999999g99s9c", false, null, false);
        if (!status.getString().isEmpty()) {
            screen.onResult(success, status);
        }
        return screen;
    }

    private static GuildDeskScreen form(boolean kingdom, boolean gather, String search) {
        GuildDeskScreen screen = new GuildDeskScreen(desk(card(GuildViews.ExamState.AVAILABLE), false, kingdom));
        ItemStack icon = gather ? new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
                : GuildTexts.icon(Contract.Kind.HUNT, "minecraft:zombified_piglin", Rank.D);
        Component name = gather ? icon.getHoverName() : GuildTexts.monsterName("minecraft:zombified_piglin");
        screen.showForCheck("post", gather ? "minecraft:netherite_upgrade_smithing_template" : "minecraft:zombified_piglin", icon, name, "4096",
                "999999g99s9c", kingdom, search, !gather);
        return screen;
    }

    private static int longestNameSeed(Gender gender) {
        CitizenNames.Alphabet alphabet = CitizenNames.alphabetFor(Minecraft.getInstance().getLanguageManager().getSelected());
        int best = 0;
        int bestWidth = -1;
        for (int seed = 0; seed < 20_000; seed += 7) {
            int width = Minecraft.getInstance().font.width(CitizenNames.render(seed, gender, alphabet));
            if (width > bestWidth) {
                best = seed;
                bestWidth = width;
            }
        }
        return best;
    }

    private static GuildViews.GuildTabView tab(UUID kingdom, boolean canManage, boolean empty) {
        List<GuildViews.AdventurerRow> adventurers = new ArrayList<>();
        List<GuildViews.SquadRow> squads = new ArrayList<>();
        long[] crystals = new long[Rank.COUNT];
        long[] energy = new long[Rank.COUNT];
        if (!empty) {
            for (int i = 0; i < 20; i++) {
                Gender gender = i % 2 == 0 ? Gender.FEMALE : Gender.MALE;
                Component name = io.github.sekerator.kingdomworld.common.citizen.CitizenTexts.name(i < 2 ? longestNameSeed(gender) : i * 7_919, gender);
                ItemStack weapon = i % 4 == 3 ? ItemStack.EMPTY : new ItemStack(i % 2 == 0 ? Items.NETHERITE_SWORD : Items.IRON_AXE);
                adventurers.add(new GuildViews.AdventurerRow(UUID.nameUUIDFromBytes(("adventurer" + i).getBytes(StandardCharsets.UTF_8)), name,
                        Rank.ofIndex(i % Rank.COUNT), MUCH, 999_999, i % 5 == 4 ? CitizenState.WOUNDED : CitizenState.HEALTHY, i % 3 == 0 ? 0 : 99,
                        i % 3 == 1, 23_999L * 3, weapon, 20));
            }
            for (int i = 0; i < 12; i++) {
                List<Component> members = new ArrayList<>();
                for (int m = 0; m < 5; m++) {
                    members.add(adventurers.get((i + m) % adventurers.size()).name());
                }
                squads.add(new GuildViews.SquadRow(i + 1, members, Rank.ofIndex(i % Rank.COUNT), 999_999, i % 2 == 1, 23_999L * 3, i + 1,
                        i % 2 == 1 ? GuildTexts.task(Contract.Kind.HUNT, MONSTERS[i % MONSTERS.length], Rank.SSS, false, 4_096) : Component.empty()));
            }
            for (Rank rank : Rank.values()) {
                crystals[rank.ordinal()] = 999_999_999L;
                energy[rank.ordinal()] = MUCH;
            }
        }
        return new GuildViews.GuildTabView(kingdom, canManage, currency(), 300, MUCH, MUCH, empty ? 0 : 99, empty ? 0 : 99, 0, 300, MUCH, 2, 5,
                adventurers, squads, empty ? List.of() : contracts(false, 24), crystals, energy);
    }

    private static ThroneScreen guildTab(Function<Role, KingdomView> member, Role role, boolean canManage, boolean empty, String mode, int chosen,
                                         long sending, Component status) {
        KingdomView view = member.apply(role);
        ThroneScreen screen = new ThroneScreen(view);
        screen.selectTab("guild");
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        screen.updateGuild(tab(view.id(), canManage, empty));
        screen.showGuild(mode, chosen, sending);
        return screen;
    }

    /**
     * The tooltips of crystals of every rank, one and a stack.
     */
    private static ItemTooltipScreen crystalTooltips(int part) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            if (rank.ordinal() / 3 == part) {
                stacks.add(MonsterCrystalItem.stack(rank, rank.ordinal() % 2 == 0 ? 64 : 1));
            }
        }
        if (part == 3) {
            stacks.add(new ItemStack(io.github.sekerator.kingdomworld.common.guild.GuildRegistries.MONSTER_CRYSTAL.get()));
            stacks.add(new ItemStack(io.github.sekerator.kingdomworld.common.guild.GuildRegistries.GUILD_DESK_ITEM.get()));
        }
        return new ItemTooltipScreen("crystals-" + part, stacks);
    }

    /**
     * Adds every case of the guild.
     *
     * @param member a kingdom seen by a member of the position
     */
    static void cases(BiConsumer<String, Supplier<Screen>> add, Function<Role, KingdomView> member) {
        Component longError = KingdomActions.errorMessage(KingdomError.READ_ONLY);
        Component done = Component.translatable("kingdomworld.guild.done", GuildTexts.task(Contract.Kind.HUNT, "minecraft:zombified_piglin",
                Rank.SSS, false, 4_096), io.github.sekerator.kingdomworld.common.economy.MoneyTexts.amount(MUCH, currency().units()),
                GuildTexts.points(999_999));
        add.accept("guild-desk-outsider", () -> deskScreen(new GuildViews.DeskView(FAR, BRANCH, LONG_KINGDOM, 0x6C3483, currency(),
                GuildViews.Card.NONE, List.of(), List.of(), MUCH, false, 300, 20, MUCH, 3, 7, 4_096, MUCH), "card", Component.empty(), false));
        for (GuildViews.ExamState exam : new GuildViews.ExamState[]{GuildViews.ExamState.AVAILABLE, GuildViews.ExamState.UNDER_WAY,
                GuildViews.ExamState.COOLDOWN, GuildViews.ExamState.TOO_FEW_POINTS, GuildViews.ExamState.TOP}) {
            add.accept("guild-desk-card-" + exam.name().toLowerCase(java.util.Locale.ROOT), () -> deskScreen(desk(card(exam), false, false), "card",
                    Component.empty(), false));
        }
        add.accept("guild-desk-board", () -> deskScreen(desk(card(GuildViews.ExamState.COOLDOWN), false, false), "board", longError, false));
        add.accept("guild-desk-board-empty", () -> deskScreen(desk(card(GuildViews.ExamState.COOLDOWN), true, false), "board", Component.empty(),
                false));
        add.accept("guild-desk-mine", () -> deskScreen(desk(card(GuildViews.ExamState.UNDER_WAY), false, false), "mine", done, true));
        add.accept("guild-desk-mine-empty", () -> deskScreen(desk(card(GuildViews.ExamState.UNDER_WAY), true, false), "mine", Component.empty(),
                false));
        add.accept("guild-desk-post-hunt", () -> form(false, false, null));
        add.accept("guild-desk-post-gather-kingdom", () -> form(true, true, null));
        add.accept("guild-desk-post-search-monsters", () -> form(false, false, "zomb"));
        add.accept("guild-desk-post-search-items", () -> form(false, true, "ingot"));
        add.accept("guild-desk-post-search-none", () -> form(false, false, "no such monster at all"));
        for (int part = 0; part < 4; part++) {
            int shown = part;
            add.accept("crystal-tooltip-" + part, () -> crystalTooltips(shown));
        }
        add.accept("throne-guild-loading", () -> {
            ThroneScreen screen = new ThroneScreen(member.apply(Role.KING));
            screen.selectTab("guild");
            return screen;
        });
        add.accept("throne-guild-branch", () -> guildTab(member, Role.KING, true, false, "branch", 0, 0, Component.empty()));
        add.accept("throne-guild-branch-subject", () -> guildTab(member, Role.SUBJECT, false, false, "branch", 0, 0, Component.empty()));
        add.accept("throne-guild-branch-empty", () -> guildTab(member, Role.MARSHAL, true, true, "branch", 0, 0, longError));
        add.accept("throne-guild-adventurers", () -> guildTab(member, Role.MARSHAL, true, false, "adventurers", 3, 0, Component.empty()));
        add.accept("throne-guild-adventurers-empty", () -> guildTab(member, Role.KING, true, true, "adventurers", 0, 0, Component.empty()));
        add.accept("throne-guild-squads", () -> guildTab(member, Role.KING, true, false, "squads", 0, 0, Component.empty()));
        add.accept("throne-guild-squads-subject", () -> guildTab(member, Role.SUBJECT, false, false, "squads", 0, 0, Component.empty()));
        add.accept("throne-guild-squads-empty", () -> guildTab(member, Role.KING, true, true, "squads", 0, 0, Component.empty()));
        add.accept("throne-guild-send", () -> guildTab(member, Role.KING, true, false, "squads", 0, 1, Component.empty()));
        add.accept("throne-guild-send-none", () -> guildTab(member, Role.KING, true, true, "squads", 0, 1, Component.empty()));
        add.accept("throne-guild-contracts", () -> guildTab(member, Role.MARSHAL, true, false, "contracts", 0, 0, longError));
        add.accept("throne-guild-contracts-empty", () -> guildTab(member, Role.SUBJECT, false, true, "contracts", 0, 0, Component.empty()));
    }
}
