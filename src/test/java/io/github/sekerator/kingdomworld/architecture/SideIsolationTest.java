package io.github.sekerator.kingdomworld.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the boundaries that keep a dedicated server from loading client classes and keep common code
 * independent from optional mods.
 */
class SideIsolationTest {
    private static final String CLIENT_PACKAGE = SourceTree.BASE_PACKAGE + ".client";
    private static final String COMPAT_PACKAGE = SourceTree.BASE_PACKAGE + ".compat";

    /**
     * Files allowed to name client classes, always behind a {@code Dist.CLIENT} guard.
     */
    private static final Set<String> CLIENT_BOOTSTRAP_FILES = Set.of(
            "io/github/sekerator/kingdomworld/KingdomWorld.java",
            "io/github/sekerator/kingdomworld/common/item/KingdomAtlasItem.java",
            "io/github/sekerator/kingdomworld/common/network/packet/ActionResultPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/BorderCrossingPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/CitizenViewPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/KingdomListResponsePacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/KingdomViewPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/MapAreaPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/OpenFoundingScreenPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/OrderPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/EconomyPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/StoragePackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/TerritoryDeltaPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/WorkPostViewPacket.java");

    /**
     * The only file outside a compat module allowed to name compat module classes.
     */
    private static final Set<String> COMPAT_BOOTSTRAP_FILES = Set.of(
            "io/github/sekerator/kingdomworld/compat/CompatModules.java");

    private static final Pattern CLIENT_ONLY_GAME_CODE = Pattern.compile(
            "\\b(net\\.minecraft\\.client|net\\.minecraftforge\\.client|com\\.mojang\\.blaze3d)\\b");
    private static final Pattern OWN_CLIENT_CODE = Pattern.compile("\\b" + Pattern.quote(CLIENT_PACKAGE) + "\\b");
    private static final Pattern COMPAT_MODULE_CODE = Pattern.compile("\\b" + Pattern.quote(COMPAT_PACKAGE) + "\\.[a-z0-9_]+\\b");

    @Test
    void clientCodeIsOnlyReferencedThroughGuardedEntryPoints() {
        Path clientDir = SourceTree.packageDir(CLIENT_PACKAGE);
        List<String> violations = new ArrayList<>();
        for (Path file : SourceTree.javaFiles(SourceTree.packageDir(SourceTree.BASE_PACKAGE))) {
            if (file.startsWith(clientDir)) {
                continue;
            }
            String relative = SourceTree.relative(file);
            String code = SourceTree.code(file);
            if (CLIENT_ONLY_GAME_CODE.matcher(code).find()) {
                violations.add(relative + " uses client-only Minecraft classes");
            }
            if (OWN_CLIENT_CODE.matcher(code).find() && !CLIENT_BOOTSTRAP_FILES.contains(relative)) {
                violations.add(relative + " references " + CLIENT_PACKAGE);
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void compatModulesAreOnlyReferencedByTheirLoader() {
        Path compatDir = SourceTree.packageDir(COMPAT_PACKAGE);
        List<String> violations = new ArrayList<>();
        for (Path file : SourceTree.javaFiles(SourceTree.packageDir(SourceTree.BASE_PACKAGE))) {
            String relative = SourceTree.relative(file);
            boolean insideModule = file.startsWith(compatDir) && !file.getParent().equals(compatDir);
            if (insideModule || COMPAT_BOOTSTRAP_FILES.contains(relative)) {
                continue;
            }
            if (COMPAT_MODULE_CODE.matcher(SourceTree.code(file)).find()) {
                violations.add(relative + " references a compat module directly");
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
