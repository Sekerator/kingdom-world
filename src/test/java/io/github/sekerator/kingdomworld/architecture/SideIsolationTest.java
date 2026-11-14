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
            "io/github/sekerator/kingdomworld/common/item/GuideBookItem.java",
            "io/github/sekerator/kingdomworld/common/construction/ArchitectRulerItem.java",
            "io/github/sekerator/kingdomworld/common/construction/SurveyStakesItem.java",
            "io/github/sekerator/kingdomworld/common/network/packet/ActionResultPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/ArmyMapPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/ArmyPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/BorderCrossingPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/CitizenViewPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/CompanionPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/ConstructionPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/EarthworkPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/KingdomListResponsePacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/KingdomViewPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/LawPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/DiplomacyPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/NobilityPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/LifePackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/MapAreaPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/OpenFoundingScreenPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/OrderPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/ProvinceDeltaPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/ProvinceSalePackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/ProvinceSnapshotPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/RoadPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/EconomyPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/GuildPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/MagicPackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/StoragePackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/TerritoryDeltaPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/TerritorySnapshotPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/TradePackets.java",
            "io/github/sekerator/kingdomworld/common/network/packet/WorkPostViewPacket.java",
            "io/github/sekerator/kingdomworld/common/network/packet/WorkforcePackets.java");

    /**
     * Compat modules of client-only mods. They are client code: they may use client classes, and the compat loader
     * creates them on the physical client only.
     */
    private static final Set<String> CLIENT_COMPAT_PACKAGES = Set.of(
            "io/github/sekerator/kingdomworld/compat/journeymap/",
            "io/github/sekerator/kingdomworld/compat/xaero/");

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
            if (CLIENT_COMPAT_PACKAGES.stream().anyMatch(relative::startsWith)) {
                continue;
            }
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
    void clientCompatModulesAreCreatedOnTheClientOnly() {
        String loader = SourceTree.code(SourceTree.packageDir(COMPAT_PACKAGE).resolve("CompatModules.java"));
        List<String> violations = new ArrayList<>();
        for (String packagePath : CLIENT_COMPAT_PACKAGES) {
            String packageName = packagePath.substring(0, packagePath.length() - 1).replace('/', '.');
            java.util.regex.Matcher imports = Pattern.compile("import " + Pattern.quote(packageName) + "\\.(\\w+);").matcher(loader);
            while (imports.find()) {
                Pattern entry = Pattern.compile("new Entry\\(\"[a-z0-9_]+\", Dist\\.CLIENT, \\(\\) -> " + imports.group(1) + "::new\\)");
                if (!entry.matcher(loader).find()) {
                    violations.add(imports.group(1) + " is not created for Dist.CLIENT only");
                }
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
