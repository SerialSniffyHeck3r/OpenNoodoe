# 순정 연결 연구노트

기존 연구 기록을 현재 Java 구현과 대조해 개발자가 사용할 형태로 다시 정리했습니다. 휴대전화 연결과 내부 UART·펌웨어 연구를 구분합니다. `protocol/`의 예시는 개인 식별자가 없는 합성 데이터입니다.

| 날짜 / 원래 기록 | 분석과 결과 | 현재 문서 |
|---|---|---|
| 2026-08-30 official-app-static-audit, PROTOCOL, DOSSIER-ERRATA | 공식 Android 2.1.14/code 579의 SPP 초기 연결, 시퀀스·명령 직렬화, 명령, 콘텐츠 변환. 요청 속성·체크섬 벡터 정정 | [프레임](../../docs/ko/framing.md), [명령](../../docs/ko/commands.md), [정정](corrections.md) |
| 2026-08-31 opennoodoe-live-validation | 순정 AK550 5.16 / 리소스 5.14에서 기기·주행·생산 정보, 시간, 알림, 시동별 웰컴 라이트 응답 | [순정 세션](stock-session.md) |
| 2026-09-01 official-app-runtime | 실행 중 creation 컴파일·전송 산출물 분석. 원본 appData와 전송 CFG·이미지 번들 구분 | [테마](../../docs/ko/themes.md) |
| 2026-09-02 field-gallery-theme-media | 슬롯 suffix·갤러리 ID, 11816바이트 청크, 늦은 DONE, 배경만 있는 테마의 분류 실패 | [콘텐츠 분석](content-findings.md) |
| 2026-09-02 measured-transfer-state-machine | 명령·task 대응, 진행량, 전송 단계, 로그 한계 | [파일 전송](../../docs/ko/file-transfer.md) |
| 2026-09-03 s24-v061-live-validation | 내비게이션 DATA task 유지, 레이더 렌더러 인식. POI 마커 의미는 부분 해석 | [확장 프로토콜](../../docs/ko/extended-protocol.md) |
| 2026-09 제품 범위·이후 소스 | 갤러리 확정 바이트 보존, 시동 이벤트 즉시 갱신과 직렬 조회, 테마 가져오기 정책 | [구조](../../docs/ko/architecture.md) |

## 연구에 있는 것과 없는 것

휴대전화 연결은 **순정 펌웨어를 상대로 구현**했습니다. 자체 펌웨어 전용 링크로 우회한 것이 아닙니다. `F5` UART 연구는 계기판↔모듈 내부 통신입니다. 순정 휴대전화 연결은 별도의 `5A FF` 시퀀스 / `A5 5A` 명령 계층입니다.

초기 연결, 명령 직렬화·파서, 파일 전송 상태, CRC·MD5, 사진 변환, 컴파일된 위젯 구조, 실행 로그와 실제 응답 기록이 있습니다. APK의 BLE UUID 문자열은 정리했지만 실제 순정 데이터 서비스 속성을 보여주는 nRF Connect GATT 덤프는 찾지 못했습니다. 보존된 Android btsnooz에는 연결·HCI 이벤트가 있으며 사진·테마 payload 전체를 담은 RFCOMM 캡처는 아닙니다. payload 근거는 앱 프로토콜 로그이며 큰 프레임 일부는 앞부분·CRC만 기록합니다.

내비게이션 전송·세션 동작은 분석했으나 경로 생성이나 내비게이션 제품을 제공하지 않습니다. POI 마커 식별·좌표는 완전히 해석하지 않았습니다. C6 배터리 필드와 정지 시간 단위는 미해석입니다. 구형 SR1.0 및 범용 순정 GATT 클라이언트도 구현하지 않았습니다. 펌웨어·플래시·파일시스템·부트 체인 연구는 별도의 [noodoe_cfw](https://github.com/SerialSniffyHeck3r/noodoe_cfw)에 속합니다.

[English](../en/README.md)
