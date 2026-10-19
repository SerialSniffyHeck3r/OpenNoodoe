package io.opennoodoe.app.protocol;

import java.util.Locale;

/** Correlates file-transfer replies with the request that is currently waiting. */
public final class ReplyExpectation {
    private final int commandId;
    private final int taskId;
    private final int transferId;

    private ReplyExpectation(int commandId, int taskId, int transferId) {
        this.commandId = commandId;
        this.taskId = taskId;
        this.transferId = transferId;
    }

    public static ReplyExpectation fromRequest(int commandId, byte[] payload) {
        int task = -1;
        int transfer = -1;
        if ((commandId == 0x0A || commandId == 0x0B || commandId == 0x0D)
                && payload != null && payload.length >= 2) {
            task = ByteCodec.u16le(payload, 0);
        }
        if (commandId == 0x0B && payload != null && payload.length >= 6) {
            transfer = ByteCodec.u16le(payload, 4);
        } else if (commandId == 0x0D && payload != null && payload.length >= 4) {
            transfer = ByteCodec.u16le(payload, 2);
        }
        return new ReplyExpectation(commandId, task, transfer);
    }

    public boolean matches(CommandFrame reply) {
        if (reply == null || reply.getCommandId() != commandId) {
            return false;
        }
        byte[] payload = reply.getPayload();
        if (taskId >= 0 && (payload.length < 4 || ByteCodec.u16le(payload, 2) != taskId)) {
            return false;
        }
        return transferId < 0
                || (payload.length >= 6 && ByteCodec.u16le(payload, 4) == transferId);
    }

    @Override
    public String toString() {
        String value = String.format(Locale.US, "command=0x%02X", commandId);
        if (taskId >= 0) {
            value += " task=" + taskId;
        }
        if (transferId >= 0) {
            value += " transfer=" + transferId;
        }
        return value;
    }
}
