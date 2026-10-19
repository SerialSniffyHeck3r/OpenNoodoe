# 구조와 소스 위치

`MainActivity`가 `NoodoeService`에 바인딩하고 `HomeScreen`이 스냅샷을 표시하며 사용자 동작을 전달합니다. 서비스가 Bluetooth 소켓·수신 루프·직렬 명령 실행기·스케줄러를 소유합니다. 연결 기기용 포그라운드 알림으로 연결 상태를 표시하며 UI가 직접 소켓을 읽지 않습니다.

연결 generation으로 이전 소켓용 대기 작업을 무효화합니다. 출력은 공통 lock으로 보호합니다. 시퀀스 ACK 대기와 명령 응답 대기는 별개입니다. `SequenceStreamDecoder`가 스트림 분할을 처리하고 `processCommand`가 상태를 즉시 갱신하며 `signalCommandReply`가 해당 응답 대기를 해제합니다. 파일 전송은 명령 경로를 점유해 상태 폴링이 중간에 끼지 않도록 합니다.

`VehicleTelemetry`는 monotonic clock으로 신선도를 계산하고 시동 전환 시 이전 속도를 버립니다. 마지막 ODO는 별도로 저장합니다. 이전 연결의 ON/OFF·속도를 현재 상태처럼 표시하지 않도록 하기 위한 구조입니다. 유휴 상태에서는 주행 정보 1초, 기기 정보 30초 주기로 조회하며 C3 수신은 즉시 처리합니다.

`ProductCommandPolicy`가 송신 경계입니다. 02/04/0E WRITE, 05/0C/11/16 빈 READ, 0200/0400/0600의 FILE 협상용 27바이트 0A WRITE, 현재 허용 FILE 작업에 대응하는 0B/0D WRITE만 허용합니다. 과거 내비게이션·알림·순정 유지보수 보조 함수는 참고용으로 남아 있지만 제품 UI·정책은 그 쓰기 기능을 노출하지 않습니다. CFW 설치기 구현은 포함하지 않습니다.

런타임 외부 라이브러리·분석 SDK·계정 백엔드·인터넷 권한이 없습니다. 사진·ZIP은 Android 파일 선택기를 사용합니다. 다섯 UI 언어는 XML 리소스로 제공하며 `LicenseDialog`와 `PrivacyPolicyDialog`는 MIT 고지와 개인정보처리방침을 오프라인으로 표시합니다.

기존 검증에는 코덱·응답 대조·명령 정책·실시간 값 만료·사진 보존·모델 목록·웰컴 라이트 테스트가 포함됩니다. 독립 실행 예제는 Android 없이 같은 Java 프로토콜 클래스로 바이트 벡터를 추가 대조합니다.

| Component | Source |
|---|---|
| Lifecycle / user actions | [MainActivity](../../android/app/src/main/java/io/opennoodoe/app/MainActivity.java) |
| Product UI | [HomeScreen](../../android/app/src/main/java/io/opennoodoe/app/HomeScreen.java) |
| Connection / queues / transactions | [NoodoeService](../../android/app/src/main/java/io/opennoodoe/app/NoodoeService.java) |
| Wire primitives / parsers / policy | [protocol/](../../android/app/src/main/java/io/opennoodoe/app/protocol/) |
| Photo / theme conversion | [ContentGenerator](../../android/app/src/main/java/io/opennoodoe/app/ContentGenerator.java) |
| Theme persistence | [ThemeRepository](../../android/app/src/main/java/io/opennoodoe/app/ThemeRepository.java) |
| Confirmed gallery payloads | [GallerySlotStore](../../android/app/src/main/java/io/opennoodoe/app/GallerySlotStore.java) |
| Current vs stale vehicle state | [VehicleTelemetry](../../android/app/src/main/java/io/opennoodoe/app/VehicleTelemetry.java) |
| Language / appearance | [AppLanguageSettings](../../android/app/src/main/java/io/opennoodoe/app/AppLanguageSettings.java), [UiThemeSettings](../../android/app/src/main/java/io/opennoodoe/app/UiThemeSettings.java) |
| Tests | [src/test](../../android/app/src/test/) |


[English](../en/architecture.md) · [목차](../../README.ko.md)
