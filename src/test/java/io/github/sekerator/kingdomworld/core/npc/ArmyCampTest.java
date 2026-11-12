package io.github.sekerator.kingdomworld.core.npc;

import io.github.sekerator.kingdomworld.core.npc.ArmyCamp.Building;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The camps of the army in the style of their kingdom: the same places and ground as the plain camp.
 */
class ArmyCampTest {
    @Test
    void aStyledCampStandsWhereThePlainOneDoes() {
        List<Building> plain = ArmyCamp.buildings();
        for (String style : TemplateCatalog.STYLES) {
            List<Building> styled = ArmyCamp.buildings(style);
            assertEquals(plain.size(), styled.size(), style);
            for (int i = 0; i < plain.size(); i++) {
                Building a = plain.get(i);
                Building b = styled.get(i);
                assertTrue(b.template().startsWith("kingdomworld:" + style + "_camp_"), b.template());
                assertEquals(a.dx(), b.dx(), style);
                assertEquals(a.dz(), b.dz(), style);
                assertEquals(a.facing(), b.facing(), style);
                TemplateCatalog.Entry ofPlain = TemplateCatalog.of(a.template());
                TemplateCatalog.Entry ofStyle = TemplateCatalog.of(b.template());
                assertEquals(ofPlain.width(), ofStyle.width(), b.template());
                assertEquals(ofPlain.depth(), ofStyle.depth(), b.template());
                assertEquals(ofPlain.role(), ofStyle.role(), b.template());
                assertEquals(ofPlain.beds(), ofStyle.beds(), b.template());
                assertEquals(style, ofStyle.style());
            }
        }
        assertEquals(plain, ArmyCamp.buildings(""));
        assertEquals(plain, ArmyCamp.buildings(null));
        assertEquals(plain, ArmyCamp.buildings("brick"), "a look of the second layout has plain camps");
    }

    @Test
    void theBuildingsOfACampStayWithinItsReach() {
        for (String style : TemplateCatalog.STYLES) {
            for (Building building : ArmyCamp.buildings(style)) {
                TemplateCatalog.Entry entry = TemplateCatalog.of(building.template());
                boolean turned = building.facing() == 1 || building.facing() == 3;
                int halfX = ((turned ? entry.depth() : entry.width()) - 1) / 2;
                int halfZ = ((turned ? entry.width() : entry.depth()) - 1) / 2;
                assertTrue(Math.abs(building.dx()) + halfX <= ArmyCamp.REACH && Math.abs(building.dz()) + halfZ <= ArmyCamp.REACH,
                        building.toString());
            }
        }
    }
}
