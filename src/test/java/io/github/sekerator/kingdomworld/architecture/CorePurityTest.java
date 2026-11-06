package io.github.sekerator.kingdomworld.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The domain core must stay free of Minecraft, Forge and Mojang code so it can be tested and simulated in
 * isolation. This test fails on any reference to those packages, including imports, static imports and fully
 * qualified names.
 */
class CorePurityTest {
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(net\\.minecraft|net\\.minecraftforge|com\\.mojang)\\b");

    @Test
    void coreDoesNotReferenceMinecraftForgeOrMojang() {
        Path coreDir = SourceTree.packageDir(SourceTree.BASE_PACKAGE + ".core");
        List<Path> files = SourceTree.javaFiles(coreDir);
        assertFalse(files.isEmpty(), "No core sources found in " + coreDir.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            String code = SourceTree.code(file);
            Matcher matcher = FORBIDDEN.matcher(code);
            while (matcher.find()) {
                violations.add(SourceTree.relative(file) + ": " + matcher.group());
            }
        }
        assertTrue(violations.isEmpty(), "Core must not depend on Minecraft, Forge or Mojang:\n" + String.join("\n", violations));
    }

    @Test
    void detectorCatchesForbiddenImports() {
        assertTrue(FORBIDDEN.matcher("import net.minecraft.world.level.Level;").find());
        assertTrue(FORBIDDEN.matcher("import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;").find());
        assertTrue(FORBIDDEN.matcher("com.mojang.logging.LogUtils.getLogger()").find());
        assertFalse(FORBIDDEN.matcher("import java.util.List;").find());
    }
}
