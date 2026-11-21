import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Подготавливает классы Forge и Minecraft для javac. Эти классы нельзя включать в мод. */
public class SrgCompile {
    public static void main(String[] args) throws Exception {
        Map<String, String> classes = new HashMap<>();
        Map<String, String> members = new HashMap<>();
        for (String row : Files.readAllLines(Paths.get(args[0]))) {
            String[] parts = row.trim().split(" +");
            switch (parts[0]) {
                case "CL:": classes.put(parts[1], parts[2]); break;
                case "MD:": members.put(parts[1] + parts[2], shortName(parts[3])); break;
                case "FD:": members.put(parts[1], shortName(parts[2])); break;
                default: break;
            }
        }
        Remapper remap = new Remapper() {
            @Override public String map(String name) { return classes.getOrDefault(name, name); }
            @Override public String mapFieldName(String owner, String name, String descriptor) {
                return members.getOrDefault(owner + "/" + name, name);
            }
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                return members.getOrDefault(owner + "/" + name + descriptor, name);
            }
        };
        try (JarFile input = new JarFile(args[1]);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(Paths.get(args[2])))) {
            Enumeration<JarEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassReader reader = new ClassReader(input.getInputStream(entry));
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new RemappingClassAdapter(writer, remap), ClassReader.SKIP_FRAMES);
                output.putNextEntry(new JarEntry(remap.map(reader.getClassName()) + ".class"));
                output.write(writer.toByteArray());
                output.closeEntry();
            }
        }
    }

    private static String shortName(String name) {
        return name.substring(name.lastIndexOf('/') + 1);
    }
}
