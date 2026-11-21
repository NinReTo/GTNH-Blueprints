package dev.gtnhblueprints;

import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.BitSet;
import java.util.zip.GZIPInputStream;

final class Blueprint {
    static final int MAX_CODE = 4_000_000;
    private static final int MAX_JSON = 24_000_000;
    private static final int MAX_BLOCKS = 200_000;
    private static final int MAX_VOLUME = 4_000_000;
    private static final String PREFIX = "GTNH1.";

    static final class Material {
        String kind;
        String name;
        String item;
        int damage;
    }

    String title;
    String tier;
    int[] size;
    Material[] palette;
    int[][] blocks;

    Blueprint rotated() {
        int[] faces = {4, 5, 2, 3, 1, 0};
        Blueprint result = new Blueprint();
        result.title = title;
        result.tier = tier;
        result.size = new int[] {size[2], size[1], size[0]};
        result.palette = palette;
        result.blocks = new int[blocks.length][];
        for (int i = 0; i < blocks.length; i++) {
            int[] cell = blocks[i];
            int links = 0;
            for (int face = 0; face < faces.length; face++) {
                if ((cell[5] & (1 << face)) != 0) links |= 1 << faces[face];
            }
            int front = cell[4];
            if (front < 0) {
                switch (palette[cell[3]].kind) {
                    case "ownHopperBack": front = 5; break;
                    case "ownHopperFront": front = 4; break;
                    case "ownHopperRight": front = 0; break;
                    default: break;
                }
            }
            result.blocks[i] = new int[] {size[2] - 1 - cell[2], cell[1], cell[0],
                cell[3], front < 0 ? -1 : faces[front], links};
        }
        return result;
    }

    static Blueprint decode(String code) throws IOException {
        if (!code.startsWith(PREFIX) || code.length() > MAX_CODE) throw invalid();
        try {
            byte[] compressed = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
                 ByteArrayOutputStream json = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = gzip.read(buffer)) != -1) {
                    if (json.size() + read > MAX_JSON) throw invalid();
                    json.write(buffer, 0, read);
                }
                Blueprint blueprint = new Gson().fromJson(new String(json.toByteArray(), StandardCharsets.UTF_8), Blueprint.class);
                if (blueprint == null) throw invalid();
                blueprint.validate();
                return blueprint;
            }
        } catch (RuntimeException failure) {
            throw new IOException("Некорректный код чертежа", failure);
        }
    }

    private void validate() throws IOException {
        if (title == null || title.length() > 256 || title.contains("\n") || title.contains("\r") ||
            tier == null || !tier.matches("[a-z]{2,5}") ||
            size == null || size.length != 3 || size[0] < 1 || size[0] > 4096 || size[1] < 1 || size[1] > 256 ||
            size[2] < 1 || size[2] > 4096 || (long) size[0] * size[1] * size[2] > MAX_VOLUME ||
            blocks == null || blocks.length == 0 || blocks.length > MAX_BLOCKS ||
            palette == null || palette.length == 0 || palette.length > blocks.length) throw invalid();
        for (Material material : palette) {
            if (material == null || material.kind == null || material.kind.length() > 256 ||
                material.name == null || material.name.length() > 256 ||
                (material.item != null && (!material.item.matches("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.:/-]+") ||
                    material.item.length() > 256)) || material.damage < 0 || material.damage > 32767) throw invalid();
        }
        BitSet positions = new BitSet();
        for (int[] block : blocks) {
            if (block == null || block.length != 6 || block[3] < 0 || block[3] >= palette.length ||
                block[4] < -1 || block[4] > 5 || block[5] < 0 || block[5] > 63) throw invalid();
            for (int axis = 0; axis < size.length; axis++) {
                if (block[axis] < 0 || block[axis] >= size[axis]) throw invalid();
            }
            int position = (block[1] * size[2] + block[2]) * size[0] + block[0];
            if (positions.get(position)) throw invalid();
            positions.set(position);
        }
    }

    private static IOException invalid() {
        return new IOException("Некорректный код или неподдерживаемый размер чертежа");
    }
}
