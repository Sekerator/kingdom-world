package io.github.sekerator.kingdomworld.architecture;

import io.github.sekerator.kingdomworld.generator.BuildingGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The built-in building templates in the resources are exactly what the generator draws, byte for byte, so that nobody
 * changes one by hand and the generator stays the source of the buildings. The same holds for the data pack of the smoke
 * check.
 */
class BuildingTemplatesTest {

    @Test
    void committedTemplatesMatchTheGenerator(@TempDir Path temp) throws IOException {
        Path generated = temp.resolve("buildings");
        Path pack = temp.resolve("pack");
        BuildingGenerator.main(new String[]{generated.toString(), pack.toString()});
        Path resources = SourceTree.mainSourceRoot().getParent().resolve("resources").resolve("data").resolve("kingdomworld")
                .resolve("kingdomworld_buildings");
        compare(generated, resources);
        Path smoke = SourceTree.mainSourceRoot().getParent().getParent().getParent().resolve("scripts").resolve("smoke-datapack");
        compare(pack, smoke);
    }

    private static void compare(Path generated, Path committed) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(generated)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        assertTrue(files.size() >= 2, "the generator wrote nothing into " + generated);
        for (Path file : files) {
            Path other = committed.resolve(generated.relativize(file).toString());
            assertTrue(Files.exists(other), other + " is missing; run gradlew generateBuildings");
            assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(other), other + " differs from the generator; run gradlew generateBuildings");
        }
        try (Stream<Path> walk = Files.walk(committed)) {
            assertEquals(files.size(), walk.filter(Files::isRegularFile).count(), committed + " holds files the generator does not write");
        }
    }

    @Test
    void thereAreAtLeastSevenBuildings() {
        assertTrue(BuildingGenerator.buildingCount() >= 7);
    }
}
