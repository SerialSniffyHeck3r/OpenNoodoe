# Framing and acknowledgements

All multibyte integers are little-endian. Offsets include magic bytes. Outer length includes header, payload and checksum; inner length counts only command payload. Neither is the internal UART `F5 | CMD | LEN | ... | XOR`.

ACK-only frames are 9 bytes with no checksum. Phone indexes run 0–127, device indexes 128–255. Retransmissions retain the index and bytes. The client advances on a matching ACK and wraps 127→0 / 255→128. It ACKs duplicate received packets without dispatching commands twice. Replies can piggyback ACKs. The sender uses a 3-second ACK deadline, two attempts total.

Send request attributes `READ=01` or `WRITE=02`, not `READ|REPLY` or `WRITE|REPLY`. Match command replies separately from outer ACKs. Several inner commands can share one outer payload. The stream decoder buffers split reads, searches `5A FF`, bounds-checks length and verifies checksum before dispatch.

## Outer sequence / 외부 시퀀스

| Offset | Bytes | Field |
|---:|---:|---|
| 0 | 2 | `5A FF` |
| 2 | 2 | Total frame length; max 11844 |
| 4 | 1 | Control: ACK=40, RESET=10 (hex) |
| 5 | 1 | Sender packet index |
| 6 | 1 | Acknowledged peer index |
| 7 | 1 | Session: 0 control, 1 file data |
| 8 | 1 | Opaque/reserved; writer 0, reader accepts other values |
| 9 | N | Inner command(s); max 11834 bytes |
| 9+N | 1 | `(-sum(payload)) & 255`, present only if N > 0 |

## Inner command / 내부 명령

| Offset | Bytes | Field |
|---:|---:|---|
| 0 | 2 | `A5 5A` |
| 2 | 1 | Command ID |
| 3 | 1 | READ=01, WRITE=02, WRITE_MULTIPLE=06, REPLY=08, NOTIFY=10 (hex) |
| 4 | 2 | Reserved; writer zero |
| 6 | 4 | Payload byte length |
| 10 | N | Payload |

```text
Raw TX:     05 00 00 00 00
Inner READ: a5 5a 05 01 00 00 00 00 00 00
First TX:   5a ff 14 00 00 00 00 00 00 a5 5a 05 01 00 00 00 00 00 00 fb
ACK #128:   5a ff 09 00 40 01 80 00 00
```

Source: [`SequenceFrame`](../../android/app/src/main/java/io/opennoodoe/app/protocol/SequenceFrame.java), [`SequenceStreamDecoder`](../../android/app/src/main/java/io/opennoodoe/app/protocol/SequenceStreamDecoder.java), [`CommandFrame`](../../android/app/src/main/java/io/opennoodoe/app/protocol/CommandFrame.java), [`ReplyExpectation`](../../android/app/src/main/java/io/opennoodoe/app/protocol/ReplyExpectation.java).


[한국어](../ko/framing.md) · [Index](../../README.md)
