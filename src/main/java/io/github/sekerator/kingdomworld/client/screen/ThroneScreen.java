package io.github.sekerator.kingdomworld.client.screen;

import io.github.sekerator.kingdomworld.client.ClientHooks;
import io.github.sekerator.kingdomworld.common.kingdom.PlayerNames;
import io.github.sekerator.kingdomworld.common.network.KingdomWorldNetwork;
import io.github.sekerator.kingdomworld.common.network.data.KingdomView;
import io.github.sekerator.kingdomworld.common.network.packet.KingdomActionPacket;
import io.github.sekerator.kingdomworld.core.membership.Permission;
import io.github.sekerator.kingdomworld.core.membership.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The throne screen: overview of a kingdom, its members and applications, the rights of positions, and the
 * way to the territory map. Every button sends an intent; the server decides and answers with a result and a
 * refreshed view.
 */
public class ThroneScreen extends Screen implements ResultListener {
    private static final int PANEL_WIDTH = 330;
    private static final int MAX_PANEL_HEIGHT = 246;
    private static final int RIGHTS_ROW = 17;
    private static final int ROW_HEIGHT = 20;
    private static final int CONTENT_TOP = 48;
    private static final int BOTTOM_AREA = 52;
    private static final List<Permission> EDITABLE_RIGHTS = List.of(Permission.BUILD, Permission.CONTAINERS,
            Permission.INTERACT, Permission.RECRUIT, Permission.TERRITORY);

    private enum Tab {
        OVERVIEW,
        MEMBERS,
        RIGHTS
    }

    private record Row(KingdomView.MemberEntry member, KingdomView.PendingEntry application, Component header) {
    }

    private KingdomView view;
    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    private Component status = Component.empty();
    private boolean statusIsError;
    private boolean closeOnSuccess;

    public ThroneScreen(KingdomView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    public UUID kingdomId() {
        return view.id();
    }

    public KingdomView view() {
        return view;
    }

    /**
     * Switches to a tab by name ({@code overview}, {@code members}, {@code rights}); unknown names are ignored.
     */
    public void selectTab(String name) {
        for (Tab candidate : Tab.values()) {
            if (candidate.name().equalsIgnoreCase(name) && (candidate == Tab.OVERVIEW || isMember())) {
                tab = candidate;
                scroll = 0;
                rebuildWidgets();
            }
        }
    }

    public void update(KingdomView newView) {
        this.view = newView;
        if (newView.relation() != KingdomView.Relation.MEMBER) {
            tab = Tab.OVERVIEW;
        }
        rebuildWidgets();
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    /**
     * The panel shrinks on small windows so that the buttons at its bottom stay visible.
     */
    private int panelHeight() {
        return Math.max(200, Math.min(MAX_PANEL_HEIGHT, height - 4));
    }

    private boolean isMember() {
        return view.relation() == KingdomView.Relation.MEMBER;
    }

    private void send(KingdomActionPacket.Action action, UUID target, int value) {
        KingdomWorldNetwork.sendToServer(new KingdomActionPacket(action, target, value));
    }

    private void confirm(Component question, Runnable onYes) {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                onYes.run();
            }
            minecraft.setScreen(this);
        }, Component.literal(view.name()), question));
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        int left = left();
        int top = top();
        int x = left + 8;
        x = tabButton(Tab.OVERVIEW, "kingdomworld.screen.throne.tab.overview", x, top + 22);
        if (isMember()) {
            x = tabButton(Tab.MEMBERS, "kingdomworld.screen.throne.tab.members", x, top + 22);
            x = tabButton(Tab.RIGHTS, "kingdomworld.screen.throne.tab.rights", x, top + 22);
            addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.tab.territory"),
                    button -> ClientHooks.openTerritoryEditor(view)).bounds(x, top + 22, 78, 18).build());
        }
        switch (tab) {
            case OVERVIEW -> initOverview(left, top);
            case MEMBERS -> initMembers(left, top);
            case RIGHTS -> initRights(left, top);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 68, top + panelHeight() - 26, 60, 20).build());
    }

    private int tabButton(Tab target, String key, int x, int y) {
        Button button = addRenderableWidget(Button.builder(Component.translatable(key), b -> {
            tab = target;
            scroll = 0;
            rebuildWidgets();
        }).bounds(x, y, 78, 18).build());
        button.active = tab != target;
        return x + 80;
    }

    private void initOverview(int left, int top) {
        int y = top + panelHeight() - 26;
        int x = left + 8;
        switch (view.relation()) {
            case OUTSIDER -> addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.apply"),
                    button -> send(KingdomActionPacket.Action.APPLY, view.id(), 0)).bounds(x, y, 120, 20).build());
            case INVITED -> {
                addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.accept_invitation"),
                        button -> send(KingdomActionPacket.Action.ACCEPT_INVITATION, view.id(), 0)).bounds(x, y, 120, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.decline_invitation"),
                        button -> send(KingdomActionPacket.Action.DECLINE_INVITATION, view.id(), 0)).bounds(x + 124, y, 100, 20).build());
            }
            case MEMBER -> {
                if (view.viewerRole() == Role.KING) {
                    addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.dissolve")
                                    .withStyle(ChatFormatting.RED),
                            button -> confirm(Component.translatable("kingdomworld.screen.throne.dissolve.confirm", view.name()), () -> {
                                closeOnSuccess = true;
                                send(KingdomActionPacket.Action.DISSOLVE, null, 0);
                            })).bounds(x, y, 140, 20).build());
                } else {
                    addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.leave"),
                            button -> confirm(Component.translatable("kingdomworld.screen.throne.leave.confirm", view.name()),
                                    () -> send(KingdomActionPacket.Action.LEAVE, null, 0))).bounds(x, y, 140, 20).build());
                }
            }
            default -> {
            }
        }
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (KingdomView.MemberEntry member : view.members()) {
            rows.add(new Row(member, null, null));
        }
        if (view.can(Permission.RECRUIT)) {
            rows.add(new Row(null, null, Component.translatable("kingdomworld.screen.throne.applications", view.applications().size())));
            for (KingdomView.PendingEntry application : view.applications()) {
                rows.add(new Row(null, application, null));
            }
        }
        return rows;
    }

    private int visibleRows() {
        return (panelHeight() - CONTENT_TOP - BOTTOM_AREA) / ROW_HEIGHT;
    }

    private void initMembers(int left, int top) {
        List<Row> rows = rows();
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        boolean king = view.viewerRole() == Role.KING;
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = top + CONTENT_TOP + i * ROW_HEIGHT;
            if (row.member() != null) {
                KingdomView.MemberEntry member = row.member();
                boolean other = !member.id().equals(self) && member.role() != Role.KING;
                if (view.can(Permission.APPOINT) && other) {
                    addRenderableWidget(Button.builder(roleName(member.role()), button ->
                                    send(KingdomActionPacket.Action.SET_ROLE, member.id(), member.role().nextAssignable().ordinal()))
                            .bounds(left + 122, y, 86, 18).build());
                }
                if (view.can(Permission.RECRUIT) && other && (king || member.role() == Role.SUBJECT)) {
                    addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.kick"), button ->
                                    confirm(Component.translatable("kingdomworld.screen.throne.kick.confirm", member.name()),
                                            () -> send(KingdomActionPacket.Action.KICK, member.id(), 0)))
                            .bounds(left + 210, y, 50, 18).build());
                }
                if (king && !member.id().equals(self)) {
                    addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.crown"), button ->
                                    confirm(Component.translatable("kingdomworld.screen.throne.crown.confirm", member.name()),
                                            () -> send(KingdomActionPacket.Action.ABDICATE, member.id(), 0)))
                            .bounds(left + 262, y, 60, 18).build());
                }
            } else if (row.application() != null) {
                KingdomView.PendingEntry application = row.application();
                addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.approve"), button ->
                        send(KingdomActionPacket.Action.APPROVE, application.id(), 0)).bounds(left + 160, y, 80, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("kingdomworld.screen.throne.reject"), button ->
                        send(KingdomActionPacket.Action.REJECT, application.id(), 0)).bounds(left + 242, y, 80, 18).build());
            }
        }
    }

    private void initRights(int left, int top) {
        int row = 0;
        for (Role role : Role.values()) {
            if (!role.isAssignable()) {
                continue;
            }
            int y = top + CONTENT_TOP + 12 + row * RIGHTS_ROW;
            EnumSet<Permission> granted = view.rights().getOrDefault(role, EnumSet.noneOf(Permission.class));
            for (int column = 0; column < EDITABLE_RIGHTS.size(); column++) {
                Permission permission = EDITABLE_RIGHTS.get(column);
                boolean has = granted.contains(permission);
                Button button = addRenderableWidget(Button.builder(
                                Component.translatable(has ? "kingdomworld.screen.throne.right_on" : "kingdomworld.screen.throne.right_off")
                                        .withStyle(has ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                                b -> KingdomWorldNetwork.sendToServer(KingdomActionPacket.setPermission(role, permission, !has)))
                        .bounds(left + 112 + column * 42, y, 40, RIGHTS_ROW - 1).build());
                button.active = view.can(Permission.APPOINT);
            }
            row++;
        }
    }

    private static Component roleName(Role role) {
        return Component.translatable("kingdomworld.role." + role.id());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.MEMBERS) {
            int max = Math.max(0, rows().size() - visibleRows());
            int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onResult(boolean success, Component message) {
        if (success && closeOnSuccess && minecraft != null) {
            onClose();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(message, false);
            }
            return;
        }
        closeOnSuccess = false;
        status = message;
        statusIsError = !success;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        graphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight(), 0xE0201810);
        graphics.renderOutline(left, top, PANEL_WIDTH, panelHeight(), 0xFF000000 | view.color());
        graphics.drawCenteredString(font, Component.literal(view.name()), width / 2, top + 8, 0xFF000000 | brighten(view.color()));
        switch (tab) {
            case OVERVIEW -> renderOverview(graphics, left, top);
            case MEMBERS -> renderMembers(graphics, left, top);
            case RIGHTS -> renderRights(graphics, left, top);
        }
        int lineY = top + panelHeight() - 46;
        for (FormattedCharSequence line : font.split(status, PANEL_WIDTH - 16)) {
            if (lineY > top + panelHeight() - 30) {
                break;
            }
            graphics.drawString(font, line, left + 8, lineY, statusIsError ? 0xFFFF7070 : 0xFFA0E0A0);
            lineY += 10;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderOverview(GuiGraphics graphics, int left, int top) {
        int x = left + 12;
        int y = top + CONTENT_TOP + 4;
        int color = 0xFFE0E0E0;
        graphics.drawString(font, Component.translatable("kingdomworld.screen.throne.ruler", PlayerNames.display(view.rulerName())), x, y, color);
        graphics.drawString(font, Component.translatable("kingdomworld.screen.throne.members", view.memberCount()), x, y + 14, color);
        graphics.drawString(font, Component.translatable("kingdomworld.screen.throne.chunks", view.chunks(), view.chunkLimit()), x, y + 28, color);
        if (view.capital() != null) {
            graphics.drawString(font, Component.translatable("kingdomworld.screen.throne.capital",
                    view.capital().getX(), view.capital().getY(), view.capital().getZ()), x, y + 42, color);
        }
        Component relation = switch (view.relation()) {
            case MEMBER -> Component.translatable("kingdomworld.screen.throne.your_role", roleName(view.viewerRole()));
            case APPLIED -> Component.translatable("kingdomworld.screen.throne.applied");
            case INVITED -> Component.translatable("kingdomworld.screen.throne.invited");
            case OTHER_KINGDOM -> Component.translatable("kingdomworld.screen.throne.other_kingdom");
            case OUTSIDER -> Component.translatable("kingdomworld.screen.throne.outsider");
        };
        graphics.drawString(font, relation, x, y + 62, 0xFFE8C860);
    }

    private void renderMembers(GuiGraphics graphics, int left, int top) {
        List<Row> rows = rows();
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            Row row = rows.get(scroll + i);
            int y = top + CONTENT_TOP + i * ROW_HEIGHT + 5;
            if (row.member() != null) {
                KingdomView.MemberEntry member = row.member();
                graphics.fill(left + 10, y + 1, left + 15, y + 6, member.online() ? 0xFF50D050 : 0xFF707070);
                graphics.drawString(font, member.name(), left + 19, y, member.online() ? 0xFFFFFFFF : 0xFFA0A0A0);
                boolean hasRoleButton = view.can(Permission.APPOINT) && member.role() != Role.KING
                        && (minecraft == null || minecraft.player == null || !member.id().equals(minecraft.player.getUUID()));
                if (!hasRoleButton) {
                    graphics.drawString(font, roleName(member.role()), left + 126, y,
                            member.role() == Role.KING ? 0xFFE8C860 : 0xFFC0C0C0);
                }
            } else if (row.application() != null) {
                graphics.drawString(font, row.application().name(), left + 19, y, 0xFFE0E0A0);
            } else if (row.header() != null) {
                graphics.drawString(font, row.header(), left + 10, y, 0xFFE8C860);
            }
        }
        if (rows.size() > visibleRows()) {
            graphics.drawString(font, Component.translatable("kingdomworld.screen.throne.page", scroll + 1,
                            Math.min(rows.size(), scroll + visibleRows()), rows.size()),
                    left + PANEL_WIDTH - 70, top + CONTENT_TOP - 10, 0xFF808080);
        }
    }

    private void renderRights(GuiGraphics graphics, int left, int top) {
        for (int column = 0; column < EDITABLE_RIGHTS.size(); column++) {
            Component label = Component.translatable("kingdomworld.permission." + EDITABLE_RIGHTS.get(column).id() + ".short");
            graphics.drawCenteredString(font, label, left + 132 + column * 42, top + CONTENT_TOP, 0xFFE8C860);
        }
        int row = 0;
        for (Role role : Role.values()) {
            if (!role.isAssignable()) {
                continue;
            }
            graphics.drawString(font, roleName(role), left + 10, top + CONTENT_TOP + 16 + row * RIGHTS_ROW, 0xFFE0E0E0);
            row++;
        }
        if (!view.can(Permission.APPOINT)) {
            graphics.drawString(font, Component.translatable("kingdomworld.screen.throne.rights_read_only"),
                    left + 10, top + CONTENT_TOP + 16 + row * RIGHTS_ROW, 0xFF909090);
        }
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 60);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 60);
        int b = Math.min(255, (color & 0xFF) + 60);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
