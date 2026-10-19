# 프레임과 수신 확인

다중 바이트 정수는 리틀 엔디언입니다. 오프셋에 magic을 포함합니다. 외부 length는 헤더·payload·체크섬 전체, 내부 length는 명령 payload만 셉니다. 내부 UART의 `F5 | CMD | LEN | ... | XOR`와는 별개입니다.

ACK 전용 프레임은 9바이트이며 체크섬이 없습니다. 휴대전화 순번은 0–127, 기기는 128–255입니다. 재전송은 동일 순번·바이트를 사용합니다. 일치하는 ACK를 받으면 증가시키며 127→0 / 255→128로 순환합니다. 중복 수신은 ACK만 다시 보내고 명령은 재처리하지 않습니다. 응답에 ACK가 함께 올 수 있습니다. 송신기는 ACK 대기 3초, 총 2회 전송합니다.

요청에는 `READ=01`, `WRITE=02` 자체를 씁니다. `READ|REPLY`, `WRITE|REPLY`를 쓰지 않습니다. 외부 ACK와 명령 응답은 별도로 대조합니다. 하나의 외부 payload에 여러 내부 명령이 이어질 수 있습니다. 디코더는 분할 수신을 보관하고 `5A FF`를 찾은 뒤 길이·체크섬을 검사합니다.

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


[English](../en/framing.md) · [목차](../../README.ko.md)
