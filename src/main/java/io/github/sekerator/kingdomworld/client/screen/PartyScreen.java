package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.CompanionViews;
import io.github.sekerator.kingdomworld.common.network.packet.CompanionPackets;
import io.github.sekerator.kingdomworld.common.trade.TradeTexts;
import io.github.sekerator.kingdomworld.core.companion.Party;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The window of a party: its members with their rank, health and share of rewards; for the leader, inviting by name, the
 * shares, handing over the lead, removing members, splitting evenly or by shares and turning friendly fire on or off;
 * invitations to answer; leaving. Every button sends an intent; the server decides and answers with a fresh view.
 */
public class PartyScreen extends KingdomScreen implements ResultListener {
    private static final String KEY = "kingdomworld.screen.party.";
    private static final int ROW = 20;
    private static final int GAP = 2;
    private static final int MIN_PANEL_WIDTH = 280;
    private static final int MAX_PANEL_HEIGHT = 260;
    private static final int BOX = 90;
    private static final int STATUS_LINES = 2;

    private CompanionViews.PartyView view;
    private String inviteName = "";
    private int scroll;
    private Component status = Component.empty();
    private boolean statusIsError;
    private int panelWidth;
    private int panelHeight;
    private int doneWidth;
    private int inviteWidth;
    private int leaveWidth;
    private int splitWidth;
    private int fireWidth;
    private int smallWidth;
    private int leaderWidth;
    private int kickWidth;
    private int answerWidth;

    public PartyScreen(CompanionViews.PartyView view) {
        super(Component.translatable(KEY + "title"));
        this.view = view;
    }

    @Override
    public String checkName() {
        return "party-" + (!view.inParty() ? "none" : view.leader() ? "leader" : "member");
    }

    public void update(CompanionViews.PartyView newView) {
        view = newView;
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    /**
     * Fills the name to invite; for the layout check.
     */
    public void showForCheck(String name) {
        inviteName = name;
    }

    /**
     * A row of the list: a member, or an invitation to the player.
     */
    private record Row(CompanionViews.MemberRow member, CompanionViews.InviteRow invitation, CompanionViews.InviteRow invited) {
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (CompanionViews.MemberRow member : view.members()) {
            rows.add(new Row(member, null, null));
        }
        for (CompanionViews.InviteRow invited : view.invited()) {
            rows.add(new Row(null, null, invited));
        }
        for (CompanionViews.InviteRow invitation : view.invitations()) {
            rows.add(new Row(null, invitation, null));
        }
        return rows;
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        doneWidth = Math.max(50, buttonWidth(Component.translatable("gui.done")));
        inviteWidth = buttonWidth(Component.translatable(KEY + "invite"));
        leaveWidth = buttonWidth(Component.translatable(KEY + "leave"));
        splitWidth = buttonWidth(Component.translatable(KEY + "split.even"), Component.translatable(KEY + "split.shares"));
        fireWidth = buttonWidth(Component.translatable(KEY + "fire.on"), Component.translatable(KEY + "fire.off"));
        smallWidth = buttonWidth(Component.literal("+"), Component.literal("-"));
        leaderWidth = buttonWidth(Component.translatable(KEY + "make_leader"));
        kickWidth = buttonWidth(Component.translatable(KEY + "kick"));
        answerWidth = buttonWidth(Component.translatable(KEY + "accept"), Component.translatable(KEY + "decline"));
        int controls = Math.max(BOX + GAP + inviteWidth + GAP + leaveWidth, splitWidth + GAP + fireWidth);
        int list = 120 + 2 * smallWidth + leaderWidth + kickWidth + 3 * GAP;
        int inner = Math.max(controls, list);
        inner = Math.max(inner, 100 + 2 * answerWidth + GAP);
        panelWidth = Math.min(Math.max(MIN_PANEL_WIDTH, inner + 2 * PAD), width - 8);
        panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 8);
    }

    private int left() {
        return (width - panelWidth) / 2;
    }

    private int top() {
        return (height - panelHeight) / 2;
    }

    private int controlsY() {
        return top() + 20 + LINE + 4;
    }

    private int listTop() {
        return controlsY() + 2 * ROW + 2;
    }

    private int bottomArea() {
        return 26 + 4 + STATUS_LINES * LINE;
    }

    private int visibleRows() {
        return Math.max(1, (top() + panelHeight - bottomArea() - listTop() - 11) / ROW);
    }

    @Override
    protected void init() {
        measure();
        int left = left();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        int y = controlsY();
        if (view.member() && (!view.inParty() || view.leader())) {
            boolean room = !view.inParty() || view.members().size() < view.maxMembers();
            Button invite = Button.builder(Component.translatable(KEY + "invite"),
                    b -> send(CompanionPackets.PartyAction.Action.INVITE, null, inviteName.strip(), 0))
                    .bounds(x + BOX + GAP, y, inviteWidth, ROW - 2).build();
            invite.active = !inviteName.isBlank() && room;
            EditBox box = new EditBox(font, x + 1, y + 1, BOX - 2, ROW - 4, Component.translatable(KEY + "name"));
            box.setMaxLength(CompanionViews.MAX_NAME);
            box.setValue(inviteName);
            box.setResponder(value -> {
                inviteName = value;
                invite.active = !value.isBlank() && room;
            });
            box.setTooltip(Tooltip.create(Component.translatable(KEY + "name_hint")));
            addRenderableWidget(box);
            addRenderableWidget(invite);
        }
        if (view.inParty()) {
            addRenderableWidget(Button.builder(Component.translatable(KEY + "leave"), b -> send(CompanionPackets.PartyAction.Action.LEAVE, null, "", 0))
                    .bounds(right - leaveWidth, y, leaveWidth, ROW - 2).build());
            Button split = addRenderableWidget(Button.builder(Component.translatable(view.evenSplit() ? KEY + "split.even" : KEY + "split.shares"),
                    b -> send(CompanionPackets.PartyAction.Action.EVEN, null, "", view.evenSplit() ? 0 : 1))
                    .bounds(x, y + ROW, splitWidth, ROW - 2).tooltip(Tooltip.create(Component.translatable(KEY + "split.hint"))).build());
            split.active = view.leader();
            Button fire = addRenderableWidget(Button.builder(Component.translatable(view.friendlyFire() ? KEY + "fire.on" : KEY + "fire.off"),
                    b -> send(CompanionPackets.PartyAction.Action.FIRE, null, "", view.friendlyFire() ? 0 : 1))
                    .bounds(x + splitWidth + GAP, y + ROW, fireWidth, ROW - 2).tooltip(Tooltip.create(Component.translatable(KEY + "fire.hint")))
                    .build());
            fire.active = view.leader();
        }
        List<Row> rows = rows();
        int visible = visibleRows();
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int rowY = listTop() + i * ROW;
            if (row.invitation() != null) {
                long party = row.invitation().party();
                addRenderableWidget(Button.builder(Component.translatable(KEY + "decline"),
                        b -> send(CompanionPackets.PartyAction.Action.DECLINE, null, "", party))
                        .bounds(right - answerWidth, rowY, answerWidth, ROW - 2).build());
                Button accept = addRenderableWidget(Button.builder(Component.translatable(KEY + "accept"),
                        b -> send(CompanionPackets.PartyAction.Action.ACCEPT, null, "", party))
                        .bounds(right - 2 * answerWidth - GAP, rowY, answerWidth, ROW - 2).build());
                accept.active = !view.inParty();
            } else if (row.member() != null && view.leader() && !self(row.member().player())) {
                UUID player = row.member().player();
                int bx = right - kickWidth;
                addRenderableWidget(Button.builder(Component.translatable(KEY + "kick"),
                        b -> send(CompanionPackets.PartyAction.Action.KICK, player, "", 0)).bounds(bx, rowY, kickWidth, ROW - 2).build());
                bx -= leaderWidth + GAP;
                addRenderableWidget(Button.builder(Component.translatable(KEY + "make_leader"),
                        b -> send(CompanionPackets.PartyAction.Action.LEADER, player, "", 0)).bounds(bx, rowY, leaderWidth, ROW - 2).build());
                if (!view.evenSplit()) {
                    addShares(row.member(), bx, rowY);
                }
            } else if (row.member() != null && view.leader() && !view.evenSplit()) {
                addShares(row.member(), right - kickWidth - leaderWidth - GAP, rowY);
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth - PAD - doneWidth, top() + panelHeight - 26, doneWidth, 20).build());
    }

    private void addShares(CompanionViews.MemberRow member, int beforeX, int y) {
        int share = member.share();
        Button plus = addRenderableWidget(Button.builder(Component.literal("+"),
                b -> send(CompanionPackets.PartyAction.Action.SHARE, member.player(), "", Math.min(Party.MAX_SHARE, share + 5)))
                .bounds(beforeX - GAP - smallWidth, y, smallWidth, ROW - 2).build());
        plus.active = share < Party.MAX_SHARE;
        Button minus = addRenderableWidget(Button.builder(Component.literal("-"),
                b -> send(CompanionPackets.PartyAction.Action.SHARE, member.player(), "", Math.max(0, share - 5)))
                .bounds(beforeX - 2 * (GAP + smallWidth), y, smallWidth, ROW - 2).build());
        minus.active = share > 0;
    }

    private boolean self(UUID player) {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(player);
    }

    private void send(CompanionPackets.PartyAction.Action action, UUID target, String name, long value) {
        if (Minecraft.getInstance().getConnection() != null) {
            KingdomWorldNetwork.sendToServer(new CompanionPackets.PartyAction(action, target == null ? new UUID(0, 0) : target, name, value));
        }
    }

    @Override
    public void onResult(boolean success, Component message) {
        status = message;
        statusIsError = !success;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, rows().size() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ---------------------------------------------------------------- drawing

    private int buttonsWidth(Row row) {
        if (row.invitation() != null) {
            return 2 * answerWidth + GAP;
        }
        if (row.member() == null || !view.leader()) {
            return 0;
        }
        int width = self(row.member().player()) ? 0 : kickWidth + leaderWidth + GAP;
        if (!view.evenSplit()) {
            width = kickWidth + leaderWidth + GAP + 2 * (GAP + smallWidth);
        }
        return width;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        int x = left + PAD;
        int right = left + panelWidth - PAD;
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0201810);
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF4A90C0);
        centeredLabel(graphics, Component.translatable(KEY + "title"), width / 2, top + 7, 0xFFFFFFFF, left + PAD, right);
        Component summary;
        if (!view.member()) {
            summary = Component.translatable(KEY + "not_member");
        } else if (!view.inParty()) {
            summary = Component.translatable(KEY + "none");
        } else {
            summary = Component.translatable(KEY + "summary", io.github.sekerator.kingdomworld.common.companion.CompanionTexts.members(
                    view.members().size()), view.maxMembers(), view.leaderRank(), view.contracts());
        }
        name(graphics, summary.getString(), x, top + 20, 0xFFF0D070, right - x);
        if (view.inParty() && !view.leader()) {
            Component settings = Component.translatable(KEY + "settings", Component.translatable(view.evenSplit() ? KEY + "split.even"
                    : KEY + "split.shares"), Component.translatable(view.friendlyFire() ? KEY + "fire.on" : KEY + "fire.off"));
            int textX = x;
            name(graphics, settings.getString(), textX, controlsY() + 5, 0xFFB8B8B8, right - leaveWidth - 6 - textX);
        }
        List<Row> rows = rows();
        int listTop = listTop();
        if (rows.isEmpty()) {
            paragraph(graphics, Component.translatable(view.member() ? KEY + "empty" : KEY + "join_guild"), x, listTop + 5, right - x, 2, 0xFFB8B8B8);
        }
        int visible = visibleRows();
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = listTop + i * ROW;
            int textRight = right - buttonsWidth(row) - (buttonsWidth(row) > 0 ? 4 : 0);
            if (row.member() != null) {
                CompanionViews.MemberRow member = row.member();
                String mark = member.leader() ? "* " : "";
                Component side = member.online() ? Component.translatable(KEY + "member_side", (int) Math.ceil(member.health()),
                        (int) Math.ceil(member.maxHealth()), TradeTexts.percent(member.permille()))
                        : Component.translatable(KEY + "member_offline", TradeTexts.percent(member.permille()));
                int sideWidth = Math.min(font.width(side), Math.max(0, (textRight - x) / 2));
                String rank = member.rank().isEmpty() ? "" : " [" + member.rank() + "]";
                name(graphics, mark + member.name() + rank, x, y + 5, member.online() ? 0xFFFFFFFF : 0xFF9A9A9A, textRight - sideWidth - 6 - x);
                name(graphics, side.getString(), textRight - sideWidth, y + 5, 0xFFB0E0B0, sideWidth);
            } else if (row.invited() != null) {
                name(graphics, Component.translatable(KEY + "invited", row.invited().name(), TradeTexts.ticks(row.invited().ticksLeft())).getString(),
                        x, y + 5, 0xFFB8B8B8, textRight - x);
            } else {
                name(graphics, Component.translatable(KEY + "invitation", row.invitation().name(), TradeTexts.ticks(row.invitation().ticksLeft()))
                        .getString(), x, y + 5, 0xFFF0D070, textRight - x);
            }
        }
        if (rows.size() > visible) {
            Component page = Component.translatable("kingdomworld.screen.throne.page", scroll + 1, Math.min(rows.size(), scroll + visible), rows.size());
            label(graphics, page, right - font.width(page), listTop + visible * ROW + 1, 0xFF9A9A9A, right);
        }
        Component bottom = status;
        int color = statusIsError ? 0xFFFF8080 : 0xFFA0E0A0;
        if (bottom.getString().isEmpty()) {
            bottom = Component.translatable(view.inParty() ? KEY + "hint.party" : KEY + "hint.none");
            color = 0xFFB8B8B8;
        }
        paragraph(graphics, bottom, x, top + panelHeight - bottomArea() + 2, right - x - doneWidth - 6, STATUS_LINES, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
