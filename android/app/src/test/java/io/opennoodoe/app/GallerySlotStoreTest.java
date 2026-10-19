package io.opennoodoe.app;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class GallerySlotStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void replacingOneSlotPreservesOtherConfirmedSlotsAcrossRestart() throws Exception {
        File root = temporary.newFolder();
        GallerySlotStore store = new GallerySlotStore(root);
        store.save(0, new byte[]{10, 11, 0});
        store.save(2, new byte[]{20, 21, 2});
        store.save(0, new byte[]{30, 31, 0});
        GallerySlotStore reopened = new GallerySlotStore(root);
        assertArrayEquals(new byte[]{30, 31, 0}, reopened.load(0));
        assertArrayEquals(new byte[]{20, 21, 2}, reopened.load(2));
        assertNull(reopened.load(1));
    }

    @Test public void copyingCachedPhotoToItselfDoesNotTruncateIt() throws Exception {
        File target = temporary.newFile();
        byte[] photo = {1, 2, 3, 4};
        Files.write(target.toPath(), photo);
        try (InputStream source = new FileInputStream(target)) {
            GallerySlotStore.replace(target, source);
        }
        assertArrayEquals(photo, Files.readAllBytes(target.toPath()));
    }

    @Test public void failedReplacementKeepsPreviousPhoto() throws Exception {
        File target = temporary.newFile();
        Files.write(target.toPath(), new byte[]{10, 20});
        InputStream failing = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("source lost"); }
        };
        assertThrows(IOException.class, () -> GallerySlotStore.replace(target, failing));
        assertArrayEquals(new byte[]{10, 20}, Files.readAllBytes(target.toPath()));
        assertEquals(1, target.getParentFile().listFiles().length);
    }

    @Test public void emptyOrWrongSlotPayloadCannotReplaceConfirmedPhoto() throws Exception {
        GallerySlotStore store = new GallerySlotStore(temporary.newFolder());
        store.save(0, new byte[]{20, 0});
        assertThrows(IOException.class, () -> store.save(0, new byte[]{21, 1}));
        assertArrayEquals(new byte[]{20, 0}, store.load(0));
        File target = temporary.newFile();
        Files.write(target.toPath(), new byte[]{30});
        assertThrows(IOException.class, () -> GallerySlotStore.replace(target, new ByteArrayInputStream(new byte[0])));
        assertArrayEquals(new byte[]{30}, Files.readAllBytes(target.toPath()));
    }
}
