package dev.gtnhblueprints;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Сквозная проверка кода, созданного сайтом. */
public class BlueprintSmoke {
    public static void main(String[] args) throws Exception {
        String code = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        Path directory = Files.createTempDirectory(Paths.get(args[1]), "blueprint-test-");
        Library library = new Library(directory);
        try {
            library.add(code);
            library.add(code);
            if (library.entries().size() != 1) throw new AssertionError("Дубликат в библиотеке");
            Blueprint blueprint = library.entries().get(0).read();
            if (!blueprint.title.equals("Электрическая доменная печь") || blueprint.blocks.length == 0) {
                throw new AssertionError("Чертёж потерял название или блоки");
            }
            Blueprint turned = blueprint.rotated().rotated().rotated().rotated();
            if (!Arrays.deepEquals(blueprint.blocks, turned.blocks) || !Arrays.equals(blueprint.size, turned.size)) {
                throw new AssertionError("Четыре поворота изменили схему");
            }
            try {
                Blueprint.decode(code.substring(0, code.length() / 2));
                throw new AssertionError("Повреждённый код принят");
            } catch (IOException expected) {
                System.out.println("Код сайта прочитан Java, библиотека сохранила " + blueprint.blocks.length + " блоков.");
            }
        } finally {
            for (Library.Entry entry : library.entries()) Files.deleteIfExists(entry.path);
            Files.delete(directory);
        }
    }
}
