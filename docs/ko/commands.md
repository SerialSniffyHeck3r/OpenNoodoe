# 명령과 payload

아래 오프셋은 10바이트 내부 헤더 다음의 **명령 payload 시작점** 기준입니다. `u16/u32`는 리틀 엔디언입니다. 응답 status는 일반적으로 offset 0의 `u16`입니다. 외부 ACK는 수신 확인이며 실제 작업 상태는 대응하는 명령 응답에서 읽습니다.

`0C READ`는 현재 속도·ODO·시동 상태를 반환합니다. `C2`는 주행/정지 이벤트이지 숫자 속도가 아니므로 상태 조회를 예약합니다. `C3`는 시동 상태를 즉시 갱신하고 새 조회도 요청합니다. 앱은 유휴 상태에서 1초마다 조회하며 요청을 직렬화하고 파일 전송 중에는 폴링을 멈춥니다. 5초가 지난 실시간 값은 만료시키고 연결 종료 시 초기화합니다. 마지막 저장 ODO는 실시간 값과 별개입니다.

`02 WRITE`는 시간대 필드 없이 현지 시간을 보냅니다. 날짜 바이트는 연도−2000, 월 1–12, 일, 시, 분, 초이며 요일은 월요일=1 … 일요일=7입니다. 현재 구현은 GPS 정확도 FF, 지도 사용 가능 1을 보냅니다. `C5`를 받으면 시간을 다시 보냅니다.

`04 WRITE`는 고정 8바이트 다음 UTF-8 이름 길이·이름·내비게이션 중 알림 바이트로 구성됩니다. 이름 길이는 문자 수가 아닌 바이트 수이며 최대 58입니다. 빈 이름은 길이 0 대신 FF를 기록합니다. UI 기본값과 유지할 필드는 `sendPreferencePacket`을 참고하십시오.

`0E WRITE`는 1바이트(0 꺼짐, 1 켜짐)입니다. 응답은 u16 status와 상태 1바이트입니다. 5.16은 시동 ON에서 status 5를 반환할 수 있습니다. 웰컴 라이트 정책은 대기 중 3회 작동, 120초 후 종료, 시동 ON 시 횟수 초기화입니다. BLE 특성이 아니라 앱의 정책입니다.

앱은 아래 App 명령만 허용합니다. ODO·생산 정보는 읽기 전용입니다. 소스에 남은 연구용 인코더가 `ProductCommandPolicy`를 우회하지 않습니다.

## IDs (hex)

| ID | Attribute | Function | Scope |
|---|---|---|---|
| 02 | WRITE | Mobile status / time | App |
| 04 | WRITE | Preferences | App |
| 05 | READ | Device information | App |
| 06 / 07 | NOTIFY | POI / group | Reference |
| 08 | NOTIFY | Navigation | Reference |
| 09 | WRITE | Weather | Reference |
| 0A | WRITE | Task negotiation | App: FILE clock/speed/gallery |
| 0B | WRITE | File control | App: matching active task |
| 0C | READ | Riding / ODO | App |
| 0D | WRITE | File data | App: matching active task |
| 0E | WRITE | Welcome/breathing light | App |
| 11 | READ | Production/OQC record | App |
| 12 | WRITE | OQC test mode | Reference |
| 13 / 14 / 15 | WRITE | Call / SMS / app notification | Reference |
| 16 | READ | Meter profile | App; may return short/unsupported reply |

## 05 REPLY — 82 bytes

| Offset | Type / bytes | Field |
|---:|---|---|
| 0 | u16 | status |
| 2 / 4 | u16 each | firmware major / minor |
| 6 | u8 | protocol major high nibble, minor low nibble |
| 7 | u8 | hardware version |
| 8 | 6 bytes | MAC, reverse byte order for display |
| 14 / 16 | u16 each | boot major / minor |
| 18 / 20 | u16 each | resource major / minor |
| 22 | 16 bytes | supported language bits |
| 38 / 39 | u8 each | resource ID / language pack ID |
| 40 | ASCII[10] | model |
| 50 | u16 | maximum speed |
| 52 | ASCII[18] | bike series |
| 70 | u8 | bike type |
| 71 / 73 | u16 each | PIN / motor series |
| 75 | ASCII[6] | PCBA |
| 81 | u8 | default dashboard |

## 0C REPLY — 11 bytes

| Offset | Type | Field |
|---:|---|---|
| 0 | u16 | status |
| 2 | u8 | ignition: 0 OFF, 1 ON, other unknown |
| 3 | u32 | odometer raw counter |
| 7 | u16 | stop duration |
| 9 / 10 | u8 each | maximum / current speed |

## 02 WRITE — 12 bytes

| Offset | Type | Field |
|---:|---|---|
| 0–5 | u8 each | year−2000, month, day, hour, minute, second |
| 6 | u8 | weekday 1–7 |
| 7 / 8 / 9 | u8 each | GPS state / Internet state / GPS accuracy |
| 10 / 11 | u8 each | phone battery / map availability |

## 04 WRITE

| Offset | Type | Field |
|---:|---|---|
| 0 / 1 / 2 | u8 each | distance unit / temperature unit / time format |
| 3 / 4 | u8 each | breathing light boolean / brightness |
| 5 / 6 / 7 | u8 each | default dashboard / language / shutdown |
| 8 | u8 | UTF-8 name byte count, or FF for empty |
| 9 | N bytes | name (absent for FF) |
| 9+N | u8 | notify on navigation |

## 11 REPLY — 139 bytes

| Offset | Type / bytes | Field |
|---:|---|---|
| 0 | u16 | status |
| 2 / 18 / 36 | ASCII[16] / [18] / [6] | part number / serial / PCBA |
| 42 / 54 | ASCII[12] / [10] | MAC text / model |
| 64 | u16 | PIN |
| 66 | u32[10] | backlight thresholds |
| 106 / 108 | u16 each | maximum speed / language |
| 110 / 112 | u16 each | firmware major / minor |
| 114 | ASCII[16] | assembly number |
| 130 | u16 | panel version |
| 132 / 133 / 134 | u8 each | unit / type / language pack |
| 135 | u16 | motor series |
| 137 / 138 | u8 each | resource ID / dashboard ID |

## Device events / 기기 이벤트

| ID | Payload / action |
|---|---|
| C1 | first 2 bytes: foreground/background creation |
| C2 | moving/stopped event; schedule 0C READ |
| C3 | first byte: ignition 0/1; update immediately, request 0C |
| C4 | OQC flags:u8 + light sensor:u32; see extended protocol |
| C5 | device requests phone time; send 02 WRITE |
| C6 | battery data bytes; this app logs them without a field decoder |

Source: [`DeviceInfo`](../../android/app/src/main/java/io/opennoodoe/app/protocol/DeviceInfo.java), [`RidingStatus`](../../android/app/src/main/java/io/opennoodoe/app/protocol/RidingStatus.java), [`OqcData`](../../android/app/src/main/java/io/opennoodoe/app/protocol/OqcData.java), [`CandidatePayloads`](../../android/app/src/main/java/io/opennoodoe/app/protocol/CandidatePayloads.java), [`VehicleTelemetry`](../../android/app/src/main/java/io/opennoodoe/app/VehicleTelemetry.java), [`WelcomeLightPolicy`](../../android/app/src/main/java/io/opennoodoe/app/WelcomeLightPolicy.java).


[English](../en/commands.md) · [목차](../../README.ko.md)
