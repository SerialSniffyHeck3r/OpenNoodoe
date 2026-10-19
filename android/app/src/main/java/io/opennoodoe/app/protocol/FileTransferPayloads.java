package io.opennoodoe.app.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

public final class FileTransferPayloads {
    public static final int TYPE_DATA = 1;
    public static final int TYPE_FILE = 2;
    public static final int BEGIN = 1;
    public static final int CONTINUE = 2;
    public static final int DONE = 3;
    public static final int REMOVE = 4;
    public static final int RESET = 5;
    public static final int CANCEL = 6;
    public static final int UPDATE = 1;
    public static final int UPDATE_TERMINATE = 2;
    public static final int DELETE = 3;

    private FileTransferPayloads() {
    }

    public static final class DataReply {
        public final int status;
        public final int taskId;
        public final int transferId;
        public final long chunkSize;
        public final long cumulativeSize;

        private DataReply(int status, int taskId, int transferId, long chunkSize,
                long cumulativeSize) {
            this.status = status;
            this.taskId = taskId;
            this.transferId = transferId;
            this.chunkSize = chunkSize;
            this.cumulativeSize = cumulativeSize;
        }

        public static DataReply parse(byte[] payload) {
            ByteCodec.require(payload, 0, 16);
            return new DataReply(ByteCodec.u16le(payload, 0), ByteCodec.u16le(payload, 2),
                    ByteCodec.u16le(payload, 4), ByteCodec.u32le(payload, 8),
                    ByteCodec.u32le(payload, 12));
        }
    }

    public static long paddedCrc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        int padding = (-data.length) & 3;
        for (int i = 0; i < padding; i++) {
            crc.update(0);
        }
        return crc.getValue();
    }

    public static byte[] negotiate(int taskId, int type, int location, long totalSize,
            int attribute, byte[] contentId) {
        byte[] payload = new byte[27];
        ByteCodec.putU16le(payload, 0, taskId);
        ByteCodec.putU16le(payload, 2, type);
        ByteCodec.putU16le(payload, 4, location);
        ByteCodec.putU32le(payload, 6, totalSize);
        payload[10] = (byte) attribute;
        byte[] id = contentId == null ? new byte[0] : contentId;
        System.arraycopy(id, 0, payload, 11, Math.min(16, id.length));
        return payload;
    }

    public static byte[] groupTask(int taskId, int location, int attribute,
            int timeoutSeconds) {
        byte[] payload = negotiate(taskId, TYPE_DATA, location, 0, attribute, new byte[16]);
        if (attribute == BEGIN) {
            ByteCodec.putU32le(payload, 11, timeoutSeconds);
        }
        return payload;
    }

    public static byte[] controlByMd5(int taskId, int operation, int transferId, byte[] md5,
            long crc32, long size, String targetName) {
        return control(taskId, operation, transferId, md5, crc32, size, targetName);
    }

    public static byte[] controlByFileId(int taskId, int operation, int transferId, int fileId,
            long crc32, long size) {
        byte[] identity = new byte[16];
        ByteCodec.putU16le(identity, 0, fileId);
        return control(taskId, operation, transferId, identity, crc32, size, "");
    }

    public static byte[] data(int taskId, int transferId, byte[] source, int offset, int length) {
        ByteCodec.require(source, offset, length);
        byte[] payload = new byte[6 + length];
        ByteCodec.putU16le(payload, 0, taskId);
        ByteCodec.putU16le(payload, 2, transferId);
        ByteCodec.putU16le(payload, 4, 1);
        System.arraycopy(source, offset, payload, 6, length);
        return payload;
    }

    private static byte[] control(int taskId, int operation, int transferId, byte[] identity,
            long crc32, long size, String targetName) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeU16(output, taskId);
        writeU16(output, operation);
        writeU16(output, transferId);
        byte[] padded = Arrays.copyOf(identity == null ? new byte[0] : identity, 16);
        output.write(padded, 0, padded.length);
        writeU32(output, crc32);
        writeU32(output, size);
        byte[] path = targetName == null ? new byte[0] : targetName.getBytes(StandardCharsets.UTF_8);
        writeU16(output, path.length);
        output.write(path, 0, path.length);
        return output.toByteArray();
    }

    private static void writeU16(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream output, long value) {
        output.write((int) value & 0xFF);
        output.write((int) (value >>> 8) & 0xFF);
        output.write((int) (value >>> 16) & 0xFF);
        output.write((int) (value >>> 24) & 0xFF);
    }
}
