# 사진 슬롯과 교체

화면 슬롯 1–6은 payload의 슬롯 바이트 0–5에 대응합니다. `ContentGenerator.gallery`는 선택 이미지를 디코딩하고 RGB565의 480×480 중앙 크롭으로 만든 뒤 JPEG 품질 80으로 압축합니다. 그 다음 **JPEG 끝에 0부터 시작하는 슬롯 번호 1바이트**를 붙입니다. 전송 파일은 JPEG+슬롯이며 raw RGB565 프레임버퍼가 아닙니다.

MD5는 **슬롯 바이트까지 포함한** 전체 데이터로 계산합니다. 파일명은 `<md5>.jpg`입니다. CRC32 역시 슬롯 바이트를 포함하며 계산에만 0 패딩을 추가합니다. 같은 사진도 슬롯이 다르면 식별자가 달라집니다.

`installGallery`는 로컬에 보존한 전송 완료 슬롯들 중 선택한 슬롯만 새 payload로 바꿔 매니페스트를 만듭니다. 갤러리 content ID는 슬롯 순서대로 연결한 파일명 문자열의 MD5입니다. location 0600에서 일반 MD5 파일명 방식의 FILE 작업을 수행합니다. transfer ID는 이번 작업의 파일 순번이며 JPEG의 슬롯 번호와 다릅니다.

편집 중인 미리보기는 초안입니다. `GallerySlotStore`가 `gallery-committed`에 전송 완료 바이트를 별도로 보존합니다. 업로드 완료 전에는 변경된 미리보기로 확정 payload를 바꾸지 않습니다. 따라서 교체 실패가 다른 슬롯의 매니페스트까지 바꾸는 일을 막습니다. task DONE 성공 후 해당 payload와 전송 상태를 로컬에 확정하고, 이전 버전의 저장 슬롯은 필요할 때 이 저장소로 이관합니다.

다른 휴대전화에서 설치한 사진이 로컬에 없으면 이 앱은 그 사진을 재구성할 수 없습니다. 로컬 매니페스트는 이 앱이 알고 있는 사진만 기록하며 계기판 갤러리 전체 다운로드는 구현하지 않았습니다.

예를 들어 화면 슬롯 6을 바꾸면 `05`를 붙여 해시하고, 슬롯 순서로 갤러리 식별자를 다시 만든 다음 BEGIN/control/data/terminate/DONE을 보냅니다. 실차 로그에는 11816 + 11816 + 883바이트 청크가 있으며 마지막 바이트가 슬롯 번호입니다. DONE이 약 6.8초 걸린 사례 때문에 task 제한 시간을 20초로 두었습니다.

Source: [`ContentGenerator.gallery`](../../android/app/src/main/java/io/opennoodoe/app/ContentGenerator.java), [`GallerySlotStore`](../../android/app/src/main/java/io/opennoodoe/app/GallerySlotStore.java), `installGallery` in [`NoodoeService`](../../android/app/src/main/java/io/opennoodoe/app/NoodoeService.java).


[English](../en/gallery.md) · [목차](../../README.ko.md)
