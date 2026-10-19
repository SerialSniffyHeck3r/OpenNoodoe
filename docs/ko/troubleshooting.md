# 문제 해결

| 증상 | 확인할 내용 |
|---|---|
| 기기가 안 보임 | Android에서 페어링하고 Bluetooth/주변 기기 권한을 허용한 뒤 주소 선택. BLE 스캐너와 SPP 기기 선택은 다름. |
| 소켓 연결 실패 | 다른 휴대전화·공식 앱의 연결 종료. bond와 주소 확인. 초기 정보 파싱 실패와 소켓 실패를 구분. |
| 연결됐지만 프레임 응답 없음 | raw 05 요청과 85 응답부터 확인. 초기 요청을 5A FF로 감싸지 않음. SR1.5 판별 확인. |
| 모든 명령 거부 | READ=01/WRITE=02, 리틀 엔디언 길이, payload만 합산한 체크섬 확인. |
| ACK 후 timeout | 외부 ACK는 명령 응답이 아님. 명령·task·transfer ID와 지연 응답 확인. |
| 속도·ON/OFF가 고정됨 | 0C 응답과 C3 확인. 5초 지난 값은 만료되어야 함. 전송 중에는 폴링 중지. |
| 웰컴 라이트 status 5 | 시동 상태 확인. 5.16은 ON에서 이 명령을 거부. |
| 기존 사진 교체 실패 | 마지막 슬롯 바이트, 새 MD5, 순서가 반영된 갤러리 ID, 확정 payload와 초안 분리 확인. |
| ZIP 거부 | MD5 CFG 1개, 선언된 MD5 이미지, basename 중복 없음, 위젯 타입 확인. Creation/appData ZIP은 다른 포맷. |
| 전송됐지만 시계가 안 나옴 | 배경만 있는 CFG는 시계가 아님. ClockDigitWidget/WatchHandWidget과 참조 확인. |
| 0B status 21 | 이미 존재하는 파일. 데이터·terminate를 생략하고 task DONE은 수행. |
| APK 업데이트 거부 | 표시 이름이 아닌 패키지·서명 인증서 불일치를 확인. |

로그 확인 순서: raw 초기 연결 → DEVICE_INFO → TX/RX 순번 → 명령·task·transfer ID → TRANSFER_MANIFEST/TRANSFER_FILE_ORDER → 파일별 진행량 → DONE. 큰 프레임 로그는 전체가 아니라 앞부분·CRC만 남길 수 있습니다. 이를 raw HCI 캡처로 부르지 않습니다.

이슈에는 앱 버전, 휴대전화·Android 버전, 계기판 펌웨어·리소스 버전, 명령·status와 주변 작업 로그를 넣으면 됩니다. MAC·시리얼·PIN·주소·개인 파일명은 공개 전에 대체하십시오.


[English](../en/troubleshooting.md) · [목차](../../README.ko.md)
