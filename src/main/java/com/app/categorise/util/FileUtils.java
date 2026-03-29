package com.app.categorise.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * General-purpose file system utilities.
 */
public final class FileUtils {

    private FileUtils() {}

    /**
     * Recursively deletes a directory and all its contents.
     * Files are deleted before their parent directories (reverse walk order).
     * Logs to stderr if any individual path cannot be deleted.
     *
     * @param dir the directory to delete
     */
    public static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path);
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to walk directory for deletion: " + dir);
        }
    }
}
