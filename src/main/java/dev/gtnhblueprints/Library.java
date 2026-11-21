package dev.gtnhblueprints;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

final class Library {
    private static final int MAX_FILES = 128;
    final Path directory;

    Library(Path directory) {
        this.directory = directory;
    }

    static final class Entry {
        final Path path;
        final String title;

        Entry(Path path, String title) {
            this.path = path;
            this.title = title;
        }

        Blueprint read() throws IOException {
            if (Files.size(path) > Blueprint.MAX_CODE + 1024) throw new IOException("Файл чертежа слишком большой");
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                reader.readLine();
                String code = reader.readLine();
                if (code == null) throw new IOException("Файл чертежа повреждён");
                return Blueprint.decode(code);
            }
        }
    }

    List<Entry> entries() throws IOException {
        Files.createDirectories(directory);
        List<Entry> entries = new ArrayList<>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(directory, "*.gtnhprint")) {
            for (Path path : paths) {
                if (entries.size() >= MAX_FILES) break;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > Blueprint.MAX_CODE + 1024) continue;
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String title = reader.readLine();
                    if (title != null && title.length() <= 256) entries.add(new Entry(path, title));
                }
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.title, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    void add(String code) throws IOException {
        Blueprint blueprint = Blueprint.decode(code);
        if (entries().size() >= MAX_FILES) throw new IOException("Библиотека заполнена");
        byte[] bytes = code.getBytes(StandardCharsets.UTF_8);
        String id;
        try {
            id = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        Path target = directory.resolve(id + ".gtnhprint");
        if (Files.exists(target)) return;
        Path temporary = Files.createTempFile(directory, "import-", ".tmp");
        try {
            Files.write(temporary, Arrays.asList(blueprint.title, code), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
