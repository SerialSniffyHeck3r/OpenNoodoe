# 실행 예제

Python 3.9+ 표준 라이브러리만 사용합니다. Java 대조는 JDK 17+와 `JAVA_HOME` 또는 PATH가 필요합니다. 저장소 루트에서 실행합니다.

```sh
python examples/protocol.py
python examples/protocol.py --decode "5a ff 14 00 00 00 00 00 00 a5 5a 05 01 00 00 00 00 00 00 fb"
python -m unittest discover -s examples -v
python examples/check_java.py
```

`protocol.py`는 내부 명령·외부 시퀀스·ACK·패딩 CRC32·협상/제어/데이터 payload·사진 suffix를 제공합니다. 무선 연결은 하지 않으며 실제 소켓·수신·재전송·작업 구현은 Android 서비스를 참고합니다. JSON/TSV에는 시간·주행·사진 전송 단계·기기 정보 벡터가 있습니다. 모든 벡터와 통신 예시는 **합성 데이터**이며 HCI 캡처가 아닙니다. `abc` 파일은 코덱 확인용으로 실제 사진이 아닙니다.

`ProtocolCheck.java`는 앱의 실제 Java 클래스로 12개 벡터·CRC·협상·ACK 전용 프레임·내비게이션 길이를 대조합니다. Python 예제와 Android 직렬화의 차이를 검출합니다. Python 테스트는 손상 프레임·슬롯 해시·재현 가능한 패키지·변조 ZIP 거부도 확인합니다.

## 직접 만든 테마 패키징

`configuration.json`에는 위젯 설정 객체(`widgets`가 있는 객체, 바깥 `files` 래퍼 제외)를 작성합니다. 자신의 JPG/PNG를 한 디렉터리에 넣고 위젯 필드에서 파일명을 참조합니다.

```sh
python examples/pack_theme.py pack configuration.json my-assets my-theme.zip
python examples/pack_theme.py check my-theme.zip
```

출력 파일은 새 경로여야 합니다. 생성 ZIP을 OpenNoodoe에서 가져옵니다. 도구는 해시·참조를 생성하며 숫자 이미지를 대신 만들거나 배경만으로 디자인을 추론하거나 공식 컴파일러를 사용하거나 에셋을 다운로드하지 않습니다. [위젯 필드·제한](../docs/ko/themes.md)을 참고하십시오. 테스트의 작은 PNG는 패키지 검증용이며 실제 계기판 테마가 아닙니다.

[English](README.md)
