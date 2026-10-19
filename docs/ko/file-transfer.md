# 파일 전송

순서는 `0A BEGIN → 파일별 [0B UPDATE → 0D 청크 → 0B UPDATE_TERMINATE] → 0A DONE`입니다. FILE type 2와 대상 location을 사용합니다. task ID는 전체 작업 동안 유지하고 transfer ID로 파일을 구분합니다. `transferFiles`는 1부터 번호를 부여합니다. 제어 명령은 외부 session 0, 파일 데이터는 session 1입니다.

total size는 원본 파일 길이의 합입니다. `0A BEGIN/DONE`은 최대 20초, 파일 제어·데이터는 최대 6초를 기다립니다. 시퀀스 ACK 제한 시간과 별개입니다. 지연된 DONE 응답을 새 작업의 응답으로 처리하면 안 되므로 명령 ID·task ID·0B/0D의 transfer ID를 대조합니다. UPDATE의 status 21은 파일 존재를 뜻하며 데이터·terminate를 생략하고 마지막 task DONE까지 진행합니다.

현재 청크는 **11816바이트**이고 마지막을 제외하면 4바이트 정렬입니다. 프레임의 이론상 데이터 상한은 `11834−10−6=11818`이지만 앱이 선택한 청크 크기와 다릅니다. 어느 쪽도 ATT MTU가 아닙니다.

CRC는 표준 reflected CRC-32/ISO-HDLC(`java.util.zip.CRC32`, polynomial 0xEDB88320, 초기·최종 XOR FFFFFFFF)입니다. 파일 뒤에 길이가 4의 배수가 될 때까지 0바이트를 붙인 것으로 계산합니다. 패딩은 CRC 계산에만 들어가며 전송 데이터와 선언한 크기는 원본 그대로입니다. MD5는 원본 바이트 전체의 해시이며 ASCII 32자 대신 raw digest 16바이트를 보냅니다. 정렬·비정렬 입력 벡터를 예제에 제공합니다.

전송 ACK, 명령 성공, task DONE, 위젯 렌더링 성공은 서로 다른 단계입니다. 로그의 BEGIN/FILE/COMMIT과 응답으로 실패 지점을 구분할 수 있습니다. 실패하면 task 정리를 시도합니다. timeout만으로 마지막 명령이 기기에 도착하지 않았다고 단정하지 않습니다.

## 0A negotiation — 27 bytes

| Offset | Type | Field |
|---:|---|---|
| 0 / 2 / 4 | u16 each | task ID / type (DATA=1, FILE=2) / location |
| 6 | u32 | total size |
| 10 | u8 | BEGIN=1, CONTINUE=2, DONE=3, REMOVE=4, RESET=5, CANCEL=6 |
| 11 | bytes[16] | content ID |

Locations: clock `0200`, weather `0300`, speedometer `0400`, POI `0500`, gallery `0600`, group `0700` (hex). App permits clock, speedometer and gallery FILE tasks only.

## 0B control — 32 + UTF-8 path length

| Offset | Type | Field |
|---:|---|---|
| 0 / 2 / 4 | u16 each | task / operation / transfer ID |
| 6 | bytes[16] | MD5 identity, or file ID u16 + 14 zeros in DATA tasks |
| 22 / 26 | u32 each | padded CRC32 / original file size |
| 30 | u16 | UTF-8 target path byte count |
| 32 | bytes[N] | target path; no terminator |

Operations: UPDATE=1, UPDATE_TERMINATE=2, DELETE=3.

## 0D data / reply

| Direction | Offset | Type | Field |
|---|---:|---|---|
| TX | 0 / 2 / 4 | u16 each | task / transfer ID / constant 1 |
| TX | 6 | bytes[N] | file slice; no separate offset field |
| RX | 0 / 2 / 4 | u16 each | status / task / transfer ID |
| RX | 6 | 2 bytes | reserved |
| RX | 8 / 12 | u32 each | chunk/progress word / cumulative bytes |

FILE responses are checked against sent chunk length and expected cumulative progress. Some historical DATA tasks use the first progress word differently; cumulative progress remains the relevant count.

FILE 응답은 청크 길이·누적 진행량을 대조합니다. 과거 DATA 작업에서는 첫 progress word의 의미가 다를 수 있으므로 누적 진행량과 구분합니다.

| Status (decimal) | Meaning |
|---:|---|
| 0 | success |
| 1 / 2 / 3 | no memory / not found / not supported |
| 5 / 6 / 8 | invalid state / invalid length / invalid data |
| 10 / 13 / 14 | timeout / busy / device error |
| 15 / 16 | delete error / cancelled |
| 21 / 23 / 24 / 25 | exists / invalid ID / corrupt / moving |
| 255 | unknown |

Source: [`FileTransferPayloads`](../../android/app/src/main/java/io/opennoodoe/app/protocol/FileTransferPayloads.java), `transferFiles`, `transferOne`, `sendAndAwait` in [`NoodoeService`](../../android/app/src/main/java/io/opennoodoe/app/NoodoeService.java).


[English](../en/file-transfer.md) · [목차](../../README.ko.md)
