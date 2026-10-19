# 컴파일된 테마와 제작

계기판 테마는 **JSON 위젯 배치와 이미지 리소스**로 구성됩니다. 반드시 한 장의 평면 이미지인 것은 아닙니다. 전송 패키지는 `<MD5>.cfg` 1개와 선언된 `<MD5>.jpg` / `<MD5>.png`를 포함합니다. CFG는 `files`, `configuration.widgets`를 가진 UTF-8 JSON입니다. `files`에는 이미지만 열거하며 CFG 자체는 넣지 않습니다. creation content ID는 CFG의 정확한 바이트에 대한 raw MD5 16바이트입니다.

CFG 공백만 바꿔도 식별자가 달라집니다. 파일명만 바꾸고 참조를 그대로 두면 깨집니다. 이미지 변경 시 이미지 MD5·파일명·위젯 참조·`files`·CFG 직렬화·CFG MD5를 모두 다시 생성해야 합니다. MD5는 기기의 콘텐츠 식별자이지 보안 서명이 아닙니다.

## 가져오기 규칙

`importCreationZip`는 .cfg/.jpg/.png를 읽고 디렉터리를 제거한 파일명 기준으로 중복을 거부합니다. 32자리 hex 파일명과 바이트 MD5를 대조하고 CFG가 정확히 1개인지, 모든 `files[]`가 존재하는지 확인합니다. 선언되지 않은 파일과 중복 선언도 실패합니다. 허용 파일 256개, 해당 파일의 압축 해제 크기 64 MiB 제한입니다. 원본 Creation/appData ZIP은 직접 설치하지 못합니다. APK에 공식 에셋 컴파일러를 넣지 않았습니다.

타입 판별은 ClockDigitWidget/WatchHandWidget을 시계(0200), SpeedDigitWidget/SpeedNeedleWidget/SpeedBarWidget/OdometerWidget/BatteryWidget을 속도계(0400)로 분류합니다. 혼합 타입은 거부합니다. BackgroundWidget만으로는 시계로 판별되지 않습니다. 날씨·POI 판별 코드는 남아 있지만 제품 정책에서 전송 위치를 차단합니다.

## 직접 제작하기

위젯 설정과 자신의 이미지를 만든 다음 [pack_theme.py](../../examples/pack_theme.py)를 사용합니다. 로컬 이미지 해시, 재귀적인 파일명 참조 교체, `files`, CFG 해시, 재현 가능한 ZIP을 생성합니다. 테마를 다운로드하거나 임의의 그림을 완성된 시계로 바꾸는 도구는 아닙니다. 디자인에 필요한 위젯 이미지 배열과 배치를 작성해야 합니다.

아래는 컴파일된 시계·속도계 번들에서 파악한 필드 이름과 배열 구조이며 추출 그림은 포함하지 않습니다. 전체 배경은 480×480입니다. 글리프 크기는 위젯에 따라 다르므로 모든 이미지를 480×480으로 만들면 안 됩니다.

앱에서는 가져온 템플릿의 JPEG 배경(품질 92), 글리프 색상, 오전/오후·요일 표시, 속도 막대 색상·타입을 편집하고 해시와 참조를 다시 만듭니다. 템플릿과 생성 테마는 앱 내부에 저장합니다. 기본 테마를 번들하지 않으며 인터넷 권한도 없습니다.

전송 순서는 원래 Java HashMap 구성(CFG 키 `creation`, 이미지 키는 파일명 stem)을 따릅니다. CFG가 항상 첫 파일인 것은 아닙니다. `officialCreationOrder`와 `TRANSFER_FILE_ORDER` 로그를 기준으로 다른 구현과 비교하십시오.

| Widget | Fields / shape |
|---|---|
| BackgroundWidget | `imageType: "single"`, `images: [filename]` |
| ClockDigitWidget | `format`, `digitImages` (10), `punctuationImages`, `monthImages`, `periodImages`, `weekdayImages` |
| DateWidget | `format`, `centerX`, `centerY`, same glyph arrays; period 2, weekday 7 |
| SpeedDigitWidget | `images` (10 digit images) |
| SpeedBarWidget | `speedBarColor`, `speedBarType` |
| OdometerWidget | `images` (10), `positions` (6 objects with integer x/y) |

```json
{
  "files": ["<image-md5>.jpg", "<digit-md5>.png"],
  "configuration": {
    "widgets": [
      {"name": "BackgroundWidget", "imageType": "single", "images": ["<image-md5>.jpg"]}
    ]
  }
}
```

This fragment illustrates the container, not a complete clock. / 이 조각은 컨테이너 구조 예시이며 완성된 시계가 아닙니다.

Source: [`ContentGenerator`](../../android/app/src/main/java/io/opennoodoe/app/ContentGenerator.java), [`ThemeRepository`](../../android/app/src/main/java/io/opennoodoe/app/ThemeRepository.java), [`ThemeAuthoringOptions`](../../android/app/src/main/java/io/opennoodoe/app/ThemeAuthoringOptions.java).


[English](../en/themes.md) · [목차](../../README.ko.md)
