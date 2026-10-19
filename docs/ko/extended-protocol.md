# 확장 프로토콜 참고

연구 내용과 독립 클라이언트 구현을 위한 인코딩 자료입니다. 현재 OpenNoodoe 제품 정책은 내비게이션·날씨·POI·그룹·전화·SMS·알림 전달·OQC 쓰기/시험을 전송하지 않습니다.

## 내비게이션

location 0100의 DATA task를 유지하면서 숫자 파일 ID로 올린 이미지들을 `08 NOTIFY`에서 참조합니다. 내비게이션 프레임마다 task를 종료하지 않습니다. 2026-09-03 연구에서 BEGIN/DONE을 매번 호출하면 세션 문맥이 사라지는 문제가 확인됐습니다. 도로명·지도·회전 화면 등을 이미지로 보내며 `ContentGenerator.navigationRoads`는 251×77 및 265×92 투명 PNG를 만듭니다. 배포 앱에는 지도 공급자·경로 탐색·실시간 길 안내 서비스가 없습니다.

## 날씨

`09 WRITE`는 51바이트입니다. UTF-8 지역명 길이 1바이트 + 0으로 채운 32바이트 지역명, AQI u16, 현재 기온 i16, 상태 u16, 이후 예보 3개의 (기온 i16, 상태 u16) 쌍입니다. 화면이 섭씨여도 프로토콜 온도는 **화씨**입니다. 상태 범위는 0–8입니다. 지역명 제한은 32문자가 아닌 UTF-8 32바이트입니다.

## POI와 그룹

POI `06 NOTIFY`: type u8(1–9), X/Y 부호 있는 i16, place ID u32입니다. type 1–3은 입력 place ID를 유지하고 나머지는 0을 기록합니다. 그룹 멤버 `07 NOTIFY`: member ID u16(0–64), X/Y i16입니다. 그룹 DATA BEGIN은 content ID의 앞 4바이트에 timeout u32를 넣습니다. 좌표 해석은 렌더러에 의존하며 전송 수락만으로 모든 POI 표시가 구현된 것은 아닙니다.

## 알림과 버튼

`15 WRITE`는 길이 접두 문자열 3개(u16 바이트 길이 + 데이터): ASCII 앱 ID, UTF-8 앱 이름, UTF-8 알림 본문입니다. `13 WRITE`는 status u8 + UTF-8 caller string16, `14 WRITE`는 UTF-8 caller와 message string16입니다.

`C4`는 OQC 진단 샘플이며 일반 모드에서 항상 발생하는 버튼 스트림으로 확인된 것은 아닙니다. flags u8 + 조도 u32로 구성되고 비트는 아래 표와 같습니다. 제품은 버튼을 받기 위해 OQC 시험 모드로 들어가지 않습니다. `C1/C2/C3/C5/C6`은 명령 문서를 참고하십시오.

## 08 NOTIFY — 42 bytes

| Offset | Type | Field |
|---:|---|---|
| 0 | u32 | distance |
| 4 / 6 | u16 / u8 | block count / icon |
| 7 / 9 / 11 / 13 / 15 | u16 each | next turn / current road / next road / overall / best-fit file IDs |
| 17 / 19 | u16 each | best-fit X / Y |
| 21 / 23 / 25 | u16 each | 2D map file ID / X / Y |
| 27 / 29 | u16 each | next-turn X / Y |
| 31 / 32 | u8 each | left-driving / night-mode booleans |
| 33 / 35 | u16 / u8 | speed limit / unit |
| 36 / 37 | u8 / u16 | near-camera boolean / camera distance |
| 39 / 41 | u16 / u8 | angle / icon after turn |

The final field is at offset 41. / 마지막 필드의 오프셋은 41입니다.

## C4 flags

| Bit | Meaning |
|---:|---|
| 0 | POWER_ON |
| 1 | POWER_OFF |
| 2 | UP |
| 3 | ENTER |
| 4 | DOWN |
| 5 | MFI_ENABLED |

Source: [`CandidatePayloads`](../../android/app/src/main/java/io/opennoodoe/app/protocol/CandidatePayloads.java), [`OqcTestSample`](../../android/app/src/main/java/io/opennoodoe/app/protocol/OqcTestSample.java).


[English](../en/extended-protocol.md) · [목차](../../README.ko.md)
