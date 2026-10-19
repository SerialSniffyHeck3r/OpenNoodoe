# OpenNoodoe

[English](README.md) · [APK 다운로드](https://github.com/SerialSniffyHeck3r/OpenNoodoe/releases/latest) · [MIT 라이선스](LICENSE)

**Veritas Studio / SerialSniffyHeck3r**가 개발한 순정 KYMCO Noodoe 계기판용 Android 앱입니다. Noodoe 계정이나 서버 없이 Bluetooth로 직접 연결합니다. 펌웨어 교체는 필요하지 않습니다.

- 사진 슬롯 6개: 자르기, 업로드, 기존 사진 교체.
- 컴파일된 시계·속도계 테마 ZIP 가져오기, 지원하는 설정·배경 편집.
- 차량 설정, 휴대전화·수동 시간 전송, 웰컴 라이트, 시스템·생산 정보, ODO 읽기.
- 현재 속도와 시동 상태 표시. 영어·한국어·일본어·중국어 간체·번체 UI.

**0.10.1 (37)**에서 ReNudo를 OpenNoodoe로 변경했습니다. 화면뿐 아니라 코드 내부 브랜드와 빌드 설정도 변경했으며 패키지 ID `io.opennoodoe.app`은 유지합니다. 순정 테마·추출 이미지·펌웨어·개인 테마는 포함하지 않습니다. 내비게이션·날씨·알림 프로토콜은 연구 문서로 제공하며 앱의 명령 허용 범위에는 들어가지 않습니다.

## 시작하기

1. Releases의 APK를 설치하고 Android Bluetooth 설정에서 계기판을 페어링합니다.
2. OpenNoodoe에서 페어링된 기기를 선택하고 연결합니다.
3. 사진 슬롯을 사용하거나 컴파일된 테마 ZIP을 가져옵니다. 원본 Creation/appData ZIP은 전송 파일로 컴파일해야 합니다.

## 개발자 문서

여기서 구현한 순정 AK550 연결은 BLE GATT가 아닌 **Bluetooth Classic RFCOMM/SPP**입니다. `5A FF` / `A5 5A` 프레임은 차량 내부 UART의 `F5` 프레임과 별개입니다.

| 항목 | 문서 |
|---|---|
| 빌드·설치·서명 | [시작하기](docs/ko/quickstart.md) |
| 검색·페어링·초기 연결·BLE 분석 | [연결](docs/ko/connection.md) |
| 프레임, ACK, 체크섬, 순번 | [프레이밍](docs/ko/framing.md) |
| 명령 ID, 오프셋, 시간, 설정, ODO, 이벤트 | [명령](docs/ko/commands.md) |
| BEGIN, 제어, 청크, CRC32, 응답, DONE | [파일 전송](docs/ko/file-transfer.md) |
| JPEG 슬롯 바이트, 사진 교체, 로컬 상태 | [갤러리](docs/ko/gallery.md) |
| CFG, 이미지, 해시, ZIP 가져오기·제작 | [테마](docs/ko/themes.md) |
| 내비게이션·날씨·POI·버튼 프로토콜 | [확장 프로토콜](docs/ko/extended-protocol.md) |
| 스레드, 명령 정책, 소스 위치 | [구조](docs/ko/architecture.md) |
| 증상별 진단 | [문제 해결](docs/ko/troubleshooting.md) |
| 연구 목록·진행 과정·정정된 해석 | [연구노트](research/ko/README.md) |

[실행 도구·Java 대조](examples/README.ko.md) · [합성 벡터](protocol/test-vectors/stock.json) · [합성 통신 예시](protocol/traces/device-info.json) · [개인정보처리방침](docs/ko/privacy.md) · [변경 기록](CHANGELOG.md)

## 저장소 범위

`android/`는 순정용 앱 소스입니다. `docs/`, `research/`, `examples/`, `protocol/`은 구현 자료입니다. CFW/NewDoe는 별도 소스·릴리즈를 사용하며 이 저장소에 설치기나 펌웨어를 포함하지 않습니다.

MIT 라이선스는 수정·재배포·상업적 이용을 허용하고 저작권·허락 고지의 보존을 요구합니다. [LICENSE](LICENSE), [NOTICE](NOTICE)를 참고하십시오. 문의: **shin6361@gmail.com**.
