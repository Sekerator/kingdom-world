package io.github.sekerator.kingdomworld.core.citizen;

import io.github.sekerator.kingdomworld.core.kingdom.CreateKingdomResult;
import io.github.sekerator.kingdomworld.core.state.KingdomRules;
import io.github.sekerator.kingdomworld.core.state.KingdomWorldState;
import io.github.sekerator.kingdomworld.core.territory.Capital;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The listener of posts, which the game uses to show the profession of a post on its block: it hears every post that comes,
 * goes or gets another profession, once, after the change, and never the restoring of saved data.
 */
class PostListenerTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private KingdomWorldState state;
    private Citizens citizens;
    private UUID realm;
    private final List<WorldPos> heard = new ArrayList<>();

    private static WorldPos pos(int x, int z) {
        return new WorldPos(OVERWORLD, x, 64, z);
    }

    @BeforeEach
    void setUp() {
        state = new KingdomWorldState();
        citizens = state.citizens();
        CreateKingdomResult result = state.found(UUID.randomUUID(), "Postland", 0xFFFFFF, UUID.randomUUID(),
                new Capital(OVERWORLD, 8, 64, 8), 0L, KingdomRules.DEFAULT);
        realm = ((CreateKingdomResult.Created) result).kingdom().id();
        citizens.setPostListener(heard::add);
    }

    @Test
    void aNewPostIsHeardOnceAndNothingElse() {
        assertTrue(citizens.addSite(pos(5, 5), SiteKinds.WORK_POST, realm));
        assertEquals(List.of(pos(5, 5)), heard);
        heard.clear();

        assertTrue(!citizens.addSite(pos(5, 5), SiteKinds.WORK_POST, realm), "the post is known already");
        assertTrue(citizens.addSite(pos(6, 6), SiteKinds.BED, realm));
        assertTrue(citizens.addSite(pos(7, 7), SiteKinds.COMPOSTER, realm));
        citizens.removeSite(pos(6, 6));
        assertEquals(List.of(), heard, "a known post, a bed and a composter say nothing");
    }

    @Test
    void aPostReplacedByAnotherPlaceIsHeard() {
        citizens.addSite(pos(4, 4), SiteKinds.WORK_POST, realm);
        heard.clear();
        assertTrue(citizens.addSite(pos(4, 4), SiteKinds.BED, realm));
        assertEquals(List.of(pos(4, 4)), heard);
        assertTrue(citizens.posts().get(pos(4, 4)).isEmpty());
    }

    @Test
    void aNewProfessionIsHeardAfterTheChange() {
        citizens.addSite(pos(5, 5), SiteKinds.WORK_POST, realm);
        List<String> seen = new ArrayList<>();
        citizens.setPostListener(at -> seen.add(citizens.posts().get(at).orElseThrow().professionId()));
        assertTrue(state.setPostProfession(null, pos(5, 5), "lumberjack").ok());
        assertTrue(state.setPostProfession(null, pos(5, 5), "unemployed").ok());
        assertEquals(List.of("lumberjack", "unemployed"), seen, "heard once each, with the profession already set");
        assertTrue(!state.setPostProfession(null, pos(5, 5), "unemployed").ok());
        assertEquals(2, seen.size(), "a profession refused as unchanged says nothing");
    }

    @Test
    void aRemovedPostIsHeardOnce() {
        citizens.addSite(pos(5, 5), SiteKinds.WORK_POST, realm);
        heard.clear();
        citizens.removeSite(pos(5, 5));
        citizens.removeSite(pos(5, 5));
        assertEquals(List.of(pos(5, 5)), heard);
    }

    @Test
    void postsOfLandGivenUpAreHeardOnceEach() {
        citizens.addSite(pos(1, 1), SiteKinds.WORK_POST, realm);
        citizens.addSite(pos(2, 2), SiteKinds.WORK_POST, realm);
        citizens.addSite(pos(3, 3), SiteKinds.BED, realm);
        citizens.addSite(pos(20, 20), SiteKinds.WORK_POST, realm);
        heard.clear();
        citizens.removeSitesInChunk(realm, OVERWORLD, pos(1, 1).chunk());
        assertEquals(2, heard.size());
        assertTrue(heard.containsAll(List.of(pos(1, 1), pos(2, 2))));
        assertTrue(citizens.posts().get(pos(20, 20)).isPresent());
    }

    @Test
    void postsOfAKingdomDissolvedAreHeardOnceEach() {
        citizens.addSite(pos(1, 1), SiteKinds.WORK_POST, realm);
        citizens.addSite(pos(20, 20), SiteKinds.WORK_POST, realm);
        citizens.addSite(pos(3, 3), SiteKinds.BED, realm);
        state.setPostProfession(null, pos(20, 20), "miner");
        heard.clear();
        assertTrue(state.dissolve(realm).isPresent());
        assertEquals(2, heard.size());
        assertTrue(heard.containsAll(List.of(pos(1, 1), pos(20, 20))));
        assertEquals(0, citizens.posts().size());
    }

    @Test
    void restoringSavedDataSaysNothing() {
        WorkPost post = new WorkPost(pos(8, 8), realm, 5, 6);
        Citizens restored = new Citizens();
        List<WorldPos> told = new ArrayList<>();
        restored.setPostListener(told::add);
        restored.restore(realm, List.of(new Site(pos(8, 8), SiteKinds.WORK_POST, realm, null),
                        new Site(pos(9, 9), SiteKinds.WORK_POST, realm, null)), List.of(post), List.of(), 0L, List.of(), List.of());
        assertEquals(2, restored.posts().size());
        assertEquals(List.of(), told);
    }
}
