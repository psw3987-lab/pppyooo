# 사진 분류 (안드로이드)

카메라 폴더 등에 쌓인 사진과 동영상을 폴더별로 빠르게 정리하는 앱입니다.

## APK 받는 방법 (GitHub)

1. github.com에서 **New repository**를 누릅니다. 이름은 예: `photo-sorter`, Public/Private 아무거나 → Create
2. 저장소 화면에서 **uploading an existing file** 링크를 누릅니다
3. ZIP을 푼 `PhotoSorter` 폴더 **안의 내용 전부**(`.github`, `app`, `build.gradle.kts` 등)를 끌어다 놓고 → **Commit changes**
4. 상단 **Actions** 탭에서 빌드가 끝날 때까지 기다립니다 (약 5~8분, 초록 체크 ✓)
5. 저장소 첫 화면 오른쪽의 **Releases**를 눌러 `PhotoSorter.apk`를 **폰에서** 받아 설치합니다
   - "출처를 알 수 없는 앱" 설치 허용을 물으면 허용해 주세요

### Actions 탭에 아무것도 안 뜰 때
Mac 등에서는 `.github` 폴더가 숨김 폴더라 업로드에서 빠질 수 있습니다.
- 저장소에서 **Add file → Create new file**을 누릅니다
- 파일 이름 칸에 `.github/workflows/build.yml`을 입력합니다
- `workflow_복사용.yml`의 내용을 그대로 붙여 넣고 → Commit

### 앱을 고친 뒤 다시 받을 때
파일을 다시 올리면 자동으로 새로 빌드되고, Releases에 새 버전이 생깁니다.
서명키가 고정되어 있어서 지우지 않고 그대로 덮어 설치하면 됩니다.

## 처음 실행
'모든 파일 접근' 권한을 켜야 사진을 옮기고 삭제할 수 있습니다.
