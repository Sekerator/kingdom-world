package io.github.sekerator.kingdomworld.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Access to the main Java sources for architecture tests.
 */
final class SourceTree {
    static final String BASE_PACKAGE = "io.github.sekerator.kingdomworld";

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private SourceTree() {
    }

    static Path mainSourceRoot() {
        String configured = System.getProperty("kingdomworld.mainSourceDir");
        Path root = configured != null ? Paths.get(configured) : Paths.get("src", "main", "java");
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Main source directory not found: " + root.toAbsolutePath());
        }
        return root;
    }

    static Path packageDir(String packageName) {
        return mainSourceRoot().resolve(packageName.replace('.', '/'));
    }

    static List<Path> javaFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Source text with comments removed, so that documentation may mention forbidden packages.
     */
    static String code(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            text = BLOCK_COMMENT.matcher(text).replaceAll(" ");
            return LINE_COMMENT.matcher(text).replaceAll(" ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String relative(Path file) {
        return mainSourceRoot().relativize(file).toString().replace('\\', '/');
    }
}
