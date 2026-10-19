package io.opennoodoe.app.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProductCommandPolicyTest {
    private boolean allows(int id, int attr, byte[] data) {
        return ProductCommandPolicy.allows(id, attr, data, -1, 0, -1);
    }

    @Test public void systemDataIsReadOnly() {
        for (int id : new int[]{0x05, 0x0C, 0x11, 0x16}) {
            assertTrue(allows(id, CommandFrame.READ, new byte[0]));
            assertFalse(allows(id, CommandFrame.WRITE, new byte[137]));
        }
    }

    @Test public void researchAndNotificationCommandsAreBlocked() {
        for (int id : new int[]{0x06, 0x07, 0x08, 0x09, 0x0F, 0x10, 0x12, 0x13, 0x14, 0x15}) {
            for (int attr : new int[]{CommandFrame.READ, CommandFrame.WRITE, CommandFrame.NOTIFY}) {
                assertFalse(allows(id, attr, new byte[0]));
            }
        }
    }

    @Test public void onlyPhotoAndClockSpeedFileTasksMayBegin() {
        for (int location : new int[]{0x0200, 0x0400, 0x0600}) {
            assertTrue(allows(0x0A, CommandFrame.WRITE,
                    FileTransferPayloads.negotiate(9, 2, location, 100, 1, null)));
        }
        for (int location : new int[]{0x0100, 0x0300, 0x0500, 0x0700, 0x0800, 0x0900, 0x0605}) {
            assertFalse(allows(0x0A, CommandFrame.WRITE,
                    FileTransferPayloads.negotiate(9, 2, location, 100, 1, null)));
        }
        assertFalse(allows(0x0A, CommandFrame.WRITE,
                FileTransferPayloads.negotiate(9, 1, 0x0200, 100, 1, null)));
    }

    @Test public void fileDataRequiresMatchingAllowedActiveTask() {
        byte[] data = FileTransferPayloads.data(9, 1, new byte[]{1}, 0, 1);
        assertTrue(ProductCommandPolicy.allows(0x0D, 2, data, 9, 2, 0x0600));
        assertFalse(ProductCommandPolicy.allows(0x0D, 2, data, 8, 2, 0x0600));
        assertFalse(ProductCommandPolicy.allows(0x0D, 2, data, 9, 2, 0x0800));
        assertFalse(ProductCommandPolicy.allows(0x0D, 2, data, 9, 1, 0x0600));
        assertFalse(ProductCommandPolicy.allows(0x0D, 2, data, -1, 0, -1));
        assertFalse(allows(0x0A, 2, new byte[3]));
    }
}
