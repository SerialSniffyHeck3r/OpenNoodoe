package io.opennoodoe.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Keeps confirmed dashboard payloads separate from editable local previews. */
final class GallerySlotStore {
    private final File directory;

    GallerySlotStore(File directory) {
        this.directory = directory;
    }

    byte[] load(int slot) throws IOException {
        File file = file(slot);
        if (!file.isFile()) return null;
        try (InputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            byte[] data = output.toByteArray();
            if (data.length < 2 || (data[data.length - 1] & 255) != slot) {
                throw new IOException("invalid saved gallery payload for slot " + slot);
            }
            return data;
        }
    }

    void save(int slot, byte[] payload) throws IOException {
        if (payload.length < 2 || (payload[payload.length - 1] & 255) != slot) {
            throw new IOException("gallery payload slot mismatch");
        }
        try (InputStream input = new ByteArrayInputStream(payload)) {
            replace(file(slot), input);
        }
    }

    private File file(int slot) {
        if (slot < 0 || slot >= 6) throw new IllegalArgumentException("invalid gallery slot");
        return new File(directory, "slot-" + slot + ".jpg");
    }

    static void replace(File target, InputStream input) throws IOException {
        if (input == null) throw new IOException("cannot open selected image");
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create gallery directory");
        File staging = File.createTempFile("gallery-", ".tmp", parent);
        try {
            try (FileOutputStream output = new FileOutputStream(staging)) {
                byte[] buffer = new byte[16384];
                int count;
                long length = 0;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    length += count;
                }
                if (length == 0) throw new IOException("selected image is empty");
                output.getFD().sync();
            }
            // Same-directory rename is atomic on Android, including self-copy callers.
            if (!staging.renameTo(target)) throw new IOException("cannot replace gallery image");
        } finally {
            if (staging.exists()) staging.delete();
        }
    }
}
