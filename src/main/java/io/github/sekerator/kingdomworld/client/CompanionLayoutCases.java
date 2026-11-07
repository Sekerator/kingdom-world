package io.github.sekerator.kingdomworld.client;

import io.github.sekerator.kingdomworld.client.screen.CompanionScreen;
import io.github.sekerator.kingdomworld.client.screen.GuildDeskScreen;
import io.github.sekerator.kingdomworld.client.screen.LayoutCheck;
import io.github.sekerator.kingdomworld.client.screen.PartyHudPreview;
import io.github.sekerator.kingdomworld.client.screen.PartyScreen;
import io.github.sekerator.kingdomworld.common.citizen.CitizenTexts;
import io.github.sekerator.kingdomworld.common.companion.CompanionMenu;
import io.github.sekerator.kingdomworld.common.companion.CompanionTexts;
import io.github.sekerator.kingdomworld.common.economy.MoneyTexts;
import io.github.sekerator.kingdomworld.common.guild.GuildTexts;
import io.github.sekerator.kingdomworld.common.kingdom.KingdomActions;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import io.github.sekerator.kingdomworld.common.network.data.GuildViews;
import io.github.sekerator.kingdomworld.common.network.data.TradeViews;
import io.github.sekerator.kingdomworld.core.citizen.CitizenNames;
import io.github.sekerator.kingdomworld.core.citizen.Gender;
import io.github.sekerator.kingdomworld.core.companion.CompanionOrder;
import io.github.sekerator.kingdomworld.core.companion.LoyaltyReason;
import io.github.sekerator.kingdomworld.core.companion.Origin;
import io.github.sekerator.kingdomworld.core.guild.Contract;
import io.github.sekerator.kingdomworld.core.guild.Rank;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomError;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The screens and messages of companions and parties in the layout check: the market and the hire at the desk of the guild
 * in every state, the contracts of a party at the desk, the window of a slave and of a free companion with packs of every
 * size, the window of a party for a leader, a member and an outsider with invitations, the list in the corner of the
 * screen, and the lines above the hotbar. Views carry the largest numbers, the longest names and more rows than fit.
 */
final class CompanionLayoutCases {
    private static final String LONG_KINGDOM = "Великое Северное Королевство Щит";
    private static final String LONG_CURRENCY = "Великая Северная Крона Щит";
    private static final String LONG_PLAYER = "WWWWWWWWWWWWWWWW";
    private static final long MUCH = 999_999_999_999L;
    private static final UUID BRANCH = UUID.nameUUIDFromBytes("companions".getBytes(StandardCharsets.UTF_8));
    private static final BlockPos FAR = new BlockPos(-30_000_000, 319, 29_999_999);

    private CompanionLayoutCases() {
    }

    private static TradeViews.Currency currency() {
        return new TradeViews.Currency(BRANCH, LONG_CURRENCY, LONG_KINGDOM, 0x6C3483, 10, 100, 9_990_000L);
    }

    private static int longestSeed(Gender gender) {
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

    private static Component longName(Gender gender) {
        return CitizenTexts.name(longestSeed(gender), gender);
    }

    private static Component money() {
        return MoneyTexts.amount(MUCH, currency().units());
    }

    // ---------------------------------------------------------------- lines above the hotbar

    /**
     * Lines above the hotbar: whose companion this is, a companion protected from a stranger, a gift. Answers to the
     * windows show in the window or in the chat, which wraps them.
     */
    static void actionbars(Font font, int narrowest) {
        for (Gender gender : Gender.values()) {
            Component name = longName(gender);
            LayoutCheck.actionbar(font, Component.translatable(CitizenTexts.gendered("kingdomworld.companion.belongs", gender), name, LONG_PLAYER),
                    narrowest);
            LayoutCheck.actionbar(font, Component.translatable(CitizenTexts.gendered("kingdomworld.companion.protected", gender), name, LONG_PLAYER),
                    narrowest);
            LayoutCheck.actionbar(font, Component.translatable("kingdomworld.companion.gift", name, 15), narrowest);
        }
        LayoutCheck.actionbar(font, Component.translatable("kingdomworld.companion.read_only"), narrowest);
    }

    // ---------------------------------------------------------------- the market at the desk

    private static List<CompanionViews.OfferRow> offers(boolean slaves, int count) {
        List<CompanionViews.OfferRow> rows = new ArrayList<>();
        Origin[] origins = Origin.offered(slaves);
        for (int i = 0; i < count; i++) {
            Gender gender = i % 2 == 0 ? Gender.FEMALE : Gender.MALE;
            rows.add(new CompanionViews.OfferRow(i + 1L, slaves, i < 2 ? longestSeed(gender) : i * 7_919, gender, i % 4,
                    origins[i % origins.length], 30, 10, 10, 10, 60, 99_999, MUCH));
        }
        return rows;
    }

    private static CompanionViews.MarketView market(boolean member, boolean closed, boolean king, boolean forbidden, boolean empty) {
        return new CompanionViews.MarketView(FAR, BRANCH, LONG_KINGDOM, 0x6C3483, currency(), member, Rank.SSS, 5, 5, MUCH, closed, king,
                forbidden, 23_999L * 30, empty ? List.of() : offers(true, 16), empty ? List.of() : offers(false, 16));
    }

    private static GuildViews.Card card() {
        return new GuildViews.Card(true, Rank.SS, 999_999_999L, 1_999_999_999L, 999_999, 99_999, GuildViews.ExamState.COOLDOWN, 23_999L * 3,
                4_095, 4_096, true);
    }

    private static GuildViews.DeskView desk(List<GuildViews.ContractRow> mine) {
        return new GuildViews.DeskView(FAR, BRANCH, LONG_KINGDOM, 0x6C3483, currency(), card(), List.of(), mine, MUCH, true, 300, 20, MUCH, 3, 7,
                4_096, MUCH);
    }

    private static GuildDeskScreen marketScreen(boolean slaves, CompanionViews.MarketView view, boolean coins, Component status) {
        GuildDeskScreen screen = new GuildDeskScreen(desk(List.of()));
        screen.showMarketForCheck(slaves, view, coins);
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    private static GuildDeskScreen partyContracts() {
        List<GuildViews.ContractRow> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Contract.Kind kind = i % 2 == 0 ? Contract.Kind.GATHER : Contract.Kind.HUNT;
            String target = kind == Contract.Kind.GATHER ? "minecraft:netherite_upgrade_smithing_template" : "minecraft:zombified_piglin";
            rows.add(new GuildViews.ContractRow(i + 1, kind, target, Rank.SSS, false, 4_096, 4_095, Rank.SSS, MUCH, 999_999, 300, 23_999L * 7,
                    Contract.Poster.PLAYER, LONG_PLAYER, i % 3 == 0, true, false, false, true, 0, GuildTexts.icon(kind, target, Rank.SSS), i % 3 != 0));
        }
        GuildDeskScreen screen = new GuildDeskScreen(desk(rows));
        screen.showForCheck("mine", "", ItemStack.EMPTY, Component.empty(), "4096", "999999g99s9c", false, null, false);
        return screen;
    }

    // ---------------------------------------------------------------- the window of a companion

    private static CompanionViews.CompanionView view(boolean slave, boolean subject, boolean warned, int slots, Gender gender) {
        List<CompanionViews.Note> notes = new ArrayList<>();
        LoyaltyReason[] reasons = LoyaltyReason.values();
        for (int i = 0; i < 5; i++) {
            notes.add(new CompanionViews.Note(reasons[(i * 3) % reasons.length], i % 2 == 0 ? -99 : 99));
        }
        List<ItemStack> gear = List.of(new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.NETHERITE_HELMET),
                ItemStack.EMPTY, new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS));
        Origin origin = slave ? Origin.SOLD_BY_FAMILY : Origin.FREEDMAN;
        return new CompanionViews.CompanionView(UUID.nameUUIDFromBytes("companion".getBytes(StandardCharsets.UTF_8)), longestSeed(gender), gender,
                3, slave, origin, 30, 999_999, 1_999_999, 10, 10, 10, 60.0f, 60.0f, 0, warned, warned ? 0 : 100, warned, notes,
                CompanionOrder.CONTRACT, 99_999, 999_999, currency(), MUCH, MUCH, !warned, subject, slots, gear);
    }

    private static CompanionScreen companion(CompanionViews.CompanionView view) {
        Minecraft minecraft = Minecraft.getInstance();
        Inventory inventory = minecraft.player != null ? minecraft.player.getInventory() : new Inventory(null);
        CompanionMenu menu = new CompanionMenu(0, inventory, view.id(), new SimpleContainer(view.slots()), view.slots(), view);
        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_SWORD));
        return new CompanionScreen(menu, inventory, CitizenTexts.name(view.seed(), view.gender()));
    }

    // ---------------------------------------------------------------- the window of a party

    private static CompanionViews.PartyView party(boolean inParty, boolean leader, boolean even, boolean member) {
        List<CompanionViews.MemberRow> members = new ArrayList<>();
        List<CompanionViews.InviteRow> invited = new ArrayList<>();
        List<CompanionViews.InviteRow> invitations = new ArrayList<>();
        if (inParty) {
            for (int i = 0; i < 16; i++) {
                members.add(new CompanionViews.MemberRow(UUID.nameUUIDFromBytes(("member" + i).getBytes(StandardCharsets.UTF_8)), LONG_PLAYER, i == 0,
                        i % 3 != 2, 60.0f, 60.0f, 100, 1000, "SSS"));
            }
            for (int i = 0; i < 4; i++) {
                invited.add(new CompanionViews.InviteRow(1, LONG_PLAYER, 5_999));
            }
        } else {
            for (int i = 0; i < 6; i++) {
                invitations.add(new CompanionViews.InviteRow(i + 1, LONG_PLAYER, 5_999));
            }
        }
        return new CompanionViews.PartyView(member, inParty, leader, 16, even, !even, 999, "SSS", members, invited, invitations);
    }

    private static PartyScreen partyScreen(CompanionViews.PartyView view, Component status) {
        PartyScreen screen = new PartyScreen(view);
        screen.showForCheck(LONG_PLAYER);
        if (!status.getString().isEmpty()) {
            screen.onResult(false, status);
        }
        return screen;
    }

    // ---------------------------------------------------------------- the list in the corner

    private static List<CompanionViews.HudLine> hud(int members, int companions) {
        List<CompanionViews.HudLine> lines = new ArrayList<>();
        for (int i = 0; i < members; i++) {
            lines.add(new CompanionViews.HudLine(false, LONG_PLAYER, 0, Gender.MALE, i == 0, i % 3 != 2, 1_024.0f, 1_024.0f, false));
        }
        for (int i = 0; i < companions; i++) {
            Gender gender = i % 2 == 0 ? Gender.FEMALE : Gender.MALE;
            lines.add(new CompanionViews.HudLine(true, "", longestSeed(gender), gender, false, i % 3 != 1, 60.0f, 60.0f, i == 0));
        }
        return lines;
    }

    /**
     * Adds every case of companions and parties.
     */
    static void cases(BiConsumer<String, Supplier<Screen>> add) {
        Component longError = KingdomActions.errorMessage(KingdomError.TOO_MANY_COMPANIONS);
        add.accept("guild-desk-market", () -> marketScreen(true, market(true, false, true, false, false), false, longError));
        add.accept("guild-desk-market-coins", () -> marketScreen(true, market(true, false, false, false, false), true, Component.empty()));
        add.accept("guild-desk-market-closed", () -> marketScreen(true, market(true, true, true, false, false), false, Component.empty()));
        add.accept("guild-desk-market-forbidden", () -> marketScreen(true, market(true, false, false, true, false), false, Component.empty()));
        add.accept("guild-desk-market-outsider", () -> marketScreen(true, market(false, false, false, false, false), false, Component.empty()));
        add.accept("guild-desk-market-empty", () -> marketScreen(true, market(true, false, false, false, true), false, Component.empty()));
        add.accept("guild-desk-market-loading", () -> marketScreen(true, null, false, Component.empty()));
        add.accept("guild-desk-hire", () -> marketScreen(false, market(true, true, true, false, false), false, longError));
        // The longest answers of the market, in the status lines of the desk.
        for (Gender gender : Gender.values()) {
            add.accept("guild-desk-market-bought-" + gender.id(), () -> marketScreen(true, market(true, false, false, false, false), false,
                    Component.translatable(CitizenTexts.gendered("kingdomworld.companion.bought", gender), longName(gender), money())));
            add.accept("guild-desk-hire-hired-" + gender.id(), () -> marketScreen(false, market(true, false, false, false, false), false,
                    Component.translatable("kingdomworld.companion.hired", longName(gender), money())));
        }
        add.accept("guild-desk-hire-empty", () -> marketScreen(false, market(true, false, false, false, true), true, Component.empty()));
        add.accept("guild-desk-mine-party", CompanionLayoutCases::partyContracts);
        for (int slots : new int[]{9, 18, 27, 36}) {
            add.accept("companion-slave-" + slots, () -> companion(view(true, true, slots == 36, slots, slots % 2 == 0 ? Gender.FEMALE : Gender.MALE)));
        }
        add.accept("companion-slave-no-kingdom", () -> companion(view(true, false, false, 18, Gender.MALE)));
        add.accept("companion-free", () -> companion(view(false, true, true, 27, Gender.FEMALE)));
        add.accept("party-leader", () -> partyScreen(party(true, true, false, true), longError));
        add.accept("party-leader-answer", () -> partyScreen(party(true, true, false, true), Component.translatable("kingdomworld.party.share_set",
                LONG_PLAYER, 100)));
        add.accept("party-leader-even", () -> partyScreen(party(true, true, true, true), Component.empty()));
        add.accept("party-member", () -> partyScreen(party(true, false, false, true), Component.empty()));
        add.accept("party-none", () -> partyScreen(party(false, false, true, true), Component.empty()));
        add.accept("party-outsider", () -> partyScreen(new CompanionViews.PartyView(false, false, false, 5, true, false, 0, "", List.of(), List.of(),
                List.of()), Component.empty()));
        add.accept("party-hud", () -> new PartyHudPreview("full", hud(16, 5)));
        add.accept("party-hud-small", () -> new PartyHudPreview("small", hud(2, 1)));
        add.accept("party-hud-companions", () -> new PartyHudPreview("companions", hud(0, 5)));
    }
}
