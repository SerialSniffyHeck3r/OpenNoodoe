package io.opennoodoe.app.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class ProtocolCodecTest {
    @Test
    public void deviceInfoRequestMatchesOfficialVector() {
        byte[] inner = new CommandFrame(0x05, CommandFrame.READ, new byte[0]).encode();
        assertEquals("A5 5A 05 01 00 00 00 00 00 00", ByteCodec.hex(inner));

        byte[] outer = new SequenceFrame(0, 0, 0, 0, inner).encode();
        assertEquals("5A FF 14 00 00 00 00 00 00 A5 5A 05 01 00 00 00 00 00 00 FB",
                ByteCodec.hex(outer));
    }

    @Test
    public void deviceInfoClassifiesProtocolFamilyFromReportedVersion() {
        byte[] sr15 = new byte[12];
        ByteCodec.putU16le(sr15, 0, 5);
        ByteCodec.putU16le(sr15, 2, 16);
        sr15[4] = 0x00;
        sr15[5] = 0x01;
        assertEquals("SR1.5", DeviceInfo.fromRawBootstrap(sr15).protocolFamilyLabel());

        byte[] sr20 = sr15.clone();
        sr20[4] = 0x20;
        assertEquals("SR2.x", DeviceInfo.fromRawBootstrap(sr20).protocolFamilyLabel());

        byte[] deprecated = sr15.clone();
        ByteCodec.putU16le(deprecated, 0, 3);
        deprecated[5] = 0x00;
        assertEquals("SR1.0", DeviceInfo.fromRawBootstrap(deprecated).protocolFamilyLabel());
    }

    @Test
    public void framedDeviceInfoReadsCommonResourceAndLanguageFields() {
        byte[] payload = new byte[82];
        payload[6] = 0x20;
        for (int i = 0; i < 16; i++) {
            payload[22 + i] = (byte) (0x80 + i);
        }
        payload[38] = 5;
        payload[39] = 14;
        putAscii(payload, 40, 10, "SAA1AA(KR)");
        putAscii(payload, 52, 18, "SYNTHETIC000000001");

        DeviceInfo info = DeviceInfo.fromFramedReply(payload);
        assertEquals("SR2.x", info.protocolFamilyLabel());
        assertEquals(0x80, info.supportedLanguageBits[0]);
        assertEquals(0x8F, info.supportedLanguageBits[15]);
        assertEquals(5, info.resourceId);
        assertEquals(14, info.languagePackId);
        assertEquals("SAA1AA(KR)", info.model);
        assertEquals("SYNTHETIC000000001", info.bikeSeries);
    }

    @Test
    public void sequenceRoundTripPreservesAckAndPayload() {
        SequenceFrame original = new SequenceFrame(SequenceFrame.CONTROL_ACK,
                7, 130, 0, new byte[]{1, 2, 3});
        SequenceFrame decoded = SequenceFrame.decode(original.encode());
        assertTrue(decoded.isAck());
        assertEquals(7, decoded.getPacketIndex());
        assertEquals(130, decoded.getAckIndex());
        assertArrayEquals(new byte[]{1, 2, 3}, decoded.getPayload());
    }

    @Test
    public void dashboardOpaqueHeaderByteIsIgnored() {
        byte[] dashboardAck = hex("5A FF 09 00 40 80 00 00 DE");
        SequenceFrame decoded = SequenceFrame.decode(dashboardAck);
        assertTrue(decoded.isAck());
        assertEquals(128, decoded.getPacketIndex());
        assertEquals(0, decoded.getAckIndex());
        assertEquals(0, decoded.getPayload().length);
    }

    @Test
    public void fragmentedStreamAndConcatenatedCommandsDecode() {
        byte[] first = new CommandFrame(0x05, CommandFrame.READ, new byte[0]).encode();
        byte[] second = new CommandFrame(0x02, CommandFrame.WRITE, new byte[]{9}).encode();
        byte[] payload = new byte[first.length + second.length];
        System.arraycopy(first, 0, payload, 0, first.length);
        System.arraycopy(second, 0, payload, first.length, second.length);
        byte[] encoded = new SequenceFrame(0, 128, 0, 0, payload).encode();

        SequenceStreamDecoder decoder = new SequenceStreamDecoder();
        assertTrue(decoder.feed(encoded, 6).isEmpty());
        List<SequenceFrame> frames = decoder.feed(slice(encoded, 6), encoded.length - 6);
        assertEquals(1, frames.size());
        List<CommandFrame> commands = CommandFrame.decodeMany(frames.get(0).getPayload());
        assertEquals(2, commands.size());
        assertEquals(0x05, commands.get(0).getCommandId());
        assertEquals(0x02, commands.get(1).getCommandId());
        assertFalse(commands.get(1).getPayload().length == 0);
    }

    @Test
    public void appNotificationMatchesOfficialStringEncoding() {
        byte[] payload = CandidatePayloads.appNotification(
                "io.opennoodoe.app", "OpenNoodoe", "OpenNoodoe notification test");
        assertEquals("11 00 69 6F 2E 6F 70 65 6E 6E 6F 6F 64 6F 65 2E 61 70 70 "
                        + "0A 00 4F 70 65 6E 4E 6F 6F 64 6F 65 "
                        + "1C 00 4F 70 65 6E 4E 6F 6F 64 6F 65 20 6E 6F 74 69 66 69 63 "
                        + "61 74 69 6F 6E 20 74 65 73 74",
                ByteCodec.hex(payload));

        byte[] command = new CommandFrame(0x15, CommandFrame.WRITE, payload).encode();
        assertEquals(0x15, command[2] & 0xFF);
        assertEquals(CommandFrame.WRITE, command[3] & 0xFF);
        assertEquals(payload.length, (int) ByteCodec.u32le(command, 6));
    }

    @Test
    public void weatherUsesFixedThirtyTwoByteLocationAndThreeForecasts() {
        byte[] payload = CandidatePayloads.weather("Seoul", 50, 77, 0,
                new int[]{78, 75, 73}, new int[]{0, 2, 4});
        assertEquals(51, payload.length);
        assertEquals(5, payload[0]);
        assertEquals(50, ByteCodec.u16le(payload, 33));
        assertEquals(77, ByteCodec.u16le(payload, 35));
        assertEquals(4, ByteCodec.u16le(payload, 49));
    }

    @Test
    public void weatherRejectsInvalidConditionCode() {
        assertThrows(IllegalArgumentException.class, () -> CandidatePayloads.weather(
                "Seoul", 50, 77, 9, new int[]{78, 75, 73}, new int[]{0, 2, 4}));
    }

    @Test
    public void navigationPayloadUsesCurrentFixedLayout() {
        byte[] payload = CandidatePayloads.navigation(350, 1, 4,
                1, 2, 3, 4, 5, 10, 11, 6, 12, 13, 14, 15,
                false, true, 60, 0, true, 500, 90, 5);
        assertEquals(42, payload.length);
        assertEquals(350, ByteCodec.u32le(payload, 0));
        assertEquals(4, payload[6] & 0xFF);
        assertEquals(60, ByteCodec.u16le(payload, 33));
        assertEquals(500, ByteCodec.u16le(payload, 37));
        assertEquals(5, payload[41] & 0xFF);
    }

    @Test
    public void poiPayloadUsesOfficialTypeCoordinatesAndConditionalPlaceId() {
        byte[] numbered = CandidatePayloads.poi(2, -120, 450, 7);
        assertEquals("02 88 FF C2 01 07 00 00 00", ByteCodec.hex(numbered));

        byte[] fixed = CandidatePayloads.poi(4, 10, -20, 99);
        assertEquals("04 0A 00 EC FF 00 00 00 00", ByteCodec.hex(fixed));
    }

    @Test
    public void groupMemberPayloadUsesThreeSignedLittleEndianWords() {
        assertEquals("01 00 88 FF C2 01",
                ByteCodec.hex(CandidatePayloads.groupMember(1, -120, 450)));
    }

    @Test
    public void fileTransferPayloadsMatchStaticLayout() {
        byte[] id = new byte[16];
        id[0] = 0x55;
        byte[] negotiate = FileTransferPayloads.negotiate(7, FileTransferPayloads.TYPE_FILE,
                0x600, 1234, FileTransferPayloads.BEGIN, id);
        assertEquals(27, negotiate.length);
        assertEquals(7, ByteCodec.u16le(negotiate, 0));
        assertEquals(FileTransferPayloads.TYPE_FILE, ByteCodec.u16le(negotiate, 2));
        assertEquals(0x600, ByteCodec.u16le(negotiate, 4));
        assertEquals(1234, ByteCodec.u32le(negotiate, 6));
        assertEquals(FileTransferPayloads.BEGIN, negotiate[10] & 0xFF);
        assertEquals(0x55, negotiate[11] & 0xFF);

        byte[] reset = FileTransferPayloads.negotiate(7, FileTransferPayloads.TYPE_FILE,
                0x600, 1234, FileTransferPayloads.RESET, id);
        assertEquals(5, reset[10] & 0xFF);

        byte[] control = FileTransferPayloads.controlByFileId(7,
                FileTransferPayloads.UPDATE, 1, 9, 0x12345678L, 100);
        assertEquals(32, control.length);
        assertEquals(FileTransferPayloads.UPDATE, ByteCodec.u16le(control, 2));
        assertEquals(9, ByteCodec.u16le(control, 6));
        assertEquals(0x12345678L, ByteCodec.u32le(control, 22));
        assertEquals(100, ByteCodec.u32le(control, 26));
        assertEquals(0, ByteCodec.u16le(control, 30));

        byte[] group = FileTransferPayloads.groupTask(8, 0x700,
                FileTransferPayloads.BEGIN, 300);
        assertEquals(8, ByteCodec.u16le(group, 0));
        assertEquals(FileTransferPayloads.TYPE_DATA, ByteCodec.u16le(group, 2));
        assertEquals(0x700, ByteCodec.u16le(group, 4));
        assertEquals(0, ByteCodec.u32le(group, 6));
        assertEquals(FileTransferPayloads.BEGIN, group[10] & 0xFF);
        assertEquals(300, ByteCodec.u32le(group, 11));
    }

    @Test
    public void groupDataReplyCarriesOpaqueProgressWordAndValidCumulativeSize() {
        byte[] payload = new byte[]{
                0, 0, 3, 0, 1, 0, 1, 0,
                0x50, (byte) 0xDC, 0, 0x20, 0x7F, 0x12, 0, 0
        };
        FileTransferPayloads.DataReply reply = FileTransferPayloads.DataReply.parse(payload);
        assertEquals(0x2000DC50L, reply.chunkSize);
        assertEquals(4735L, reply.cumulativeSize);
    }

    @Test
    public void fileTransferCrcPadsFinalWordLikeOfficialApp() {
        assertEquals(0xB1513FD4L,
                FileTransferPayloads.paddedCrc32(new byte[]{1, 2, 3}));
        assertEquals(0xB63CFBCDL,
                FileTransferPayloads.paddedCrc32(new byte[]{1, 2, 3, 4}));
    }

    @Test
    public void fileDataReplySeparatesChunkAndCumulativeProgress() {
        byte[] payload = new byte[16];
        ByteCodec.putU16le(payload, 2, 2);
        ByteCodec.putU16le(payload, 4, 1);
        ByteCodec.putU32le(payload, 8, 3342);
        ByteCodec.putU32le(payload, 12, 15158);

        FileTransferPayloads.DataReply reply = FileTransferPayloads.DataReply.parse(payload);
        assertEquals(0, reply.status);
        assertEquals(2, reply.taskId);
        assertEquals(1, reply.transferId);
        assertEquals(3342, reply.chunkSize);
        assertEquals(15158, reply.cumulativeSize);
    }

    @Test
    public void transferReplyExpectationRejectsLateReplyFromPreviousTask() {
        byte[] request = FileTransferPayloads.negotiate(4, FileTransferPayloads.TYPE_FILE,
                0x200, 100, FileTransferPayloads.BEGIN, new byte[16]);
        ReplyExpectation expectation = ReplyExpectation.fromRequest(0x0A, request);

        assertFalse(expectation.matches(reply(0x0A, "00 00 03 00 03")));
        assertTrue(expectation.matches(reply(0x0A, "15 00 04 00 01")));
    }

    @Test
    public void transferReplyExpectationMatchesTaskAndTransferIds() {
        byte[] request = FileTransferPayloads.data(6, 2, new byte[]{1, 2, 3}, 0, 3);
        ReplyExpectation expectation = ReplyExpectation.fromRequest(0x0D, request);

        assertFalse(expectation.matches(reply(0x0D,
                "00 00 06 00 01 00 01 00 03 00 00 00 03 00 00 00")));
        assertTrue(expectation.matches(reply(0x0D,
                "00 00 06 00 02 00 01 00 03 00 00 00 03 00 00 00")));
    }

    @Test
    public void ridingStatusReplyUsesCurrentV15Offsets() {
        byte[] payload = hex("00 00 01 78 56 34 12 2A 00 63 20");
        RidingStatus status = RidingStatus.fromReply(payload);
        assertEquals(0, status.status);
        assertTrue(status.keyOn);
        assertEquals(0x12345678L, status.odometer);
        assertEquals(42, status.stopDuration);
        assertEquals(99, status.maxSpeed);
        assertEquals(32, status.currentSpeed);
    }

    @Test
    public void oqcReadReplyUsesOfficialFixedOffsets() {
        byte[] payload = new byte[139];
        putAscii(payload, 2, 16, "PARTIAL");
        putAscii(payload, 18, 18, "SERIAL");
        putAscii(payload, 36, 6, "SR0701");
        putAscii(payload, 42, 12, "A1B2C3D4E5F6");
        putAscii(payload, 54, 10, "SAA1AA(KR)");
        ByteCodec.putU16le(payload, 64, 123456);
        ByteCodec.putU16le(payload, 110, 5);
        ByteCodec.putU16le(payload, 112, 16);
        ByteCodec.putU32le(payload, 66, 1234);
        ByteCodec.putU16le(payload, 106, 180);
        ByteCodec.putU16le(payload, 108, 12);
        putAscii(payload, 114, 16, "ASSEMBLY");
        ByteCodec.putU16le(payload, 130, 3);
        payload[132] = 1;
        payload[133] = 2;
        payload[134] = 12;
        ByteCodec.putU16le(payload, 135, 77);
        payload[137] = 5;
        payload[138] = 14;

        OqcData data = OqcData.fromReply(payload);
        assertEquals("SR0701", data.pcbaVersion);
        assertEquals("SAA1AA(KR)", data.model);
        assertEquals(5, data.firmwareMajor);
        assertEquals(16, data.firmwareMinor);
        assertEquals(1234, data.backlightThresholds[0]);
        assertEquals(180, data.maxSpeed);
        assertEquals(12, data.language);
        assertEquals(3, data.panelVersion);
        assertEquals(77, data.motorSeries);
        assertEquals(5, data.resourceId);
        assertEquals(14, data.dashboardId);
        assertArrayEquals(slice(payload, 2), data.toWritePayload());
    }

    @Test(expected = IllegalArgumentException.class)
    public void oqcWriteRejectsWrongBacklightCount() {
        OqcData.create("PART", "SERIAL", "PCBA", "A1B2C3D4E5F6", "MODEL", 1234,
                new long[9], 180, 1, 5, 16, "ASSEMBLY", 1,
                0, 0, 1, 1, 1, 1);
    }

    @Test
    public void oqcTestSampleDecodesOfficialInputBitsAndLightSensor() {
        byte[] payload = hex("2D 78 56 34 12");
        OqcTestSample sample = OqcTestSample.fromPayload(payload);

        assertTrue(sample.has(OqcTestSample.POWER_ON));
        assertFalse(sample.has(OqcTestSample.POWER_OFF));
        assertTrue(sample.has(OqcTestSample.BUTTON_UP));
        assertTrue(sample.has(OqcTestSample.BUTTON_ENTER));
        assertFalse(sample.has(OqcTestSample.BUTTON_DOWN));
        assertTrue(sample.has(OqcTestSample.MFI_ENABLED));
        assertEquals(0x12345678L, sample.lightSensor);
        assertEquals("POWER_ON | UP | ENTER | MFI", OqcTestSample.activeNames(sample.flags));
    }

    private static byte[] slice(byte[] value, int offset) {
        byte[] result = new byte[value.length - offset];
        System.arraycopy(value, offset, result, 0, result.length);
        return result;
    }

    private static byte[] hex(String value) {
        String[] parts = value.split(" ");
        byte[] result = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return result;
    }

    private static CommandFrame reply(int commandId, String payload) {
        return new CommandFrame(commandId, CommandFrame.REPLY, hex(payload));
    }

    private static void putAscii(byte[] target, int offset, int length, String value) {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, target, offset, Math.min(length, encoded.length));
    }
}
