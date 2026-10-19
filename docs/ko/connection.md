# 순정 기기 연결

구현 경로는 순정 AK550 Noodoe 펌웨어 5.16 / 리소스 5.14 → Bluetooth Classic → 보안 RFCOMM/SPP → 초기 요청 → SR1.5 프레임입니다. SPP UUID는 `00001101-0000-1000-8000-00805F9B34FB`입니다. 계기판 내부 UART는 별개입니다.

기기 이름은 보통 `KYMCO Noodoe`와 기기별 접미사로 구성됩니다. 선택 기준은 페어링된 기기의 주소이며 iBeacon 식별자를 사용하지 않습니다. `BOND_BONDED`를 확인하고 검색을 취소한 뒤 작업 스레드에서 `createRfcommSocketToServiceRecord(SPP_UUID)`와 `connect()`를 호출합니다. 페어링은 Android 시스템 창에서 진행하며 앱이 직접 패스키를 처리하거나 공통 PIN을 가정하지 않습니다.

이 경로에는 BLE MTU 협상이 없습니다. RFCOMM은 바이트 스트림이므로 한 번의 read가 프레임 일부 또는 여러 프레임일 수 있습니다. 구현에는 Noodoe 계정 토큰이나 클라우드 challenge 전송이 없습니다.

## 초기 연결 순서

1. 선택한 페어링 주소로 보안 RFCOMM 소켓을 연결합니다.
2. 시퀀스 래퍼 없이 `05 00 00 00 00`을 전송합니다.
3. `85 <length:u32le>` 5바이트 헤더와 이어지는 `length`바이트를 읽습니다. 첫 payload 바이트를 건너뛴 나머지가 초기 기기 정보입니다.
4. 펌웨어·프로토콜·하드웨어를 파싱합니다. `supportsFramedProtocol()`은 protocol major ≥1 또는 major 0이면서 hardware가 0이 아니거나 firmware major ≥4인 경우를 허용합니다. 구형 SR1.0 명령 경로는 구현하지 않았습니다.
5. 시퀀스 수신을 시작하고 `DEVICE_INFO`(`05 READ`)를 보냅니다. 이후 시간·상태·설정은 직렬 명령 큐를 사용합니다.

초기 body의 오프셋: 펌웨어 major/minor 0/2, 프로토콜 4, 하드웨어 5, 역순 MAC 6, 선택적인 부트·리소스 버전 12/14/16/18, 언어 바이트 20. 프레임 응답의 status 2바이트는 여기에 없습니다.

## BLE 분석 결과

APK 정적 분석에서 아래 UUID를 확인했습니다. 라이브러리 후보이며 순정 AK550의 GATT 덤프가 아닙니다. 실제 특성 속성, Write Without Response, 페어링 방식, 협상 ATT MTU를 이 문자열만으로 확정하지 않습니다. 동작하는 Android 구현은 SPP입니다.

| Library symbol | UUID |
|---|---|
| SPPLE service | `14839ac4-7d7e-415c-9a42-167340cf2339` |
| Output | `8b00ace7-eb0b-49b0-bbe9-9aee0a26e1a3` |
| Input | `0734594a-a8e7-4b1a-a6b1-cd5243059a57` |
| InputCredit | `ba04c4b2-892b-43be-b69c-5d13f2195392` |
| OutputCredit | `e06d5efb-4f4a-45c0-9eb1-371ae5a14ad4` |

Source: [`NoodoeService.java`](../../android/app/src/main/java/io/opennoodoe/app/NoodoeService.java), [`DeviceInfo.java`](../../android/app/src/main/java/io/opennoodoe/app/protocol/DeviceInfo.java).


[English](../en/connection.md) · [목차](../../README.ko.md)
