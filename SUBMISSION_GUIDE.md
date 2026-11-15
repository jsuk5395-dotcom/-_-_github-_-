# 부릉이 · 앗차! — 공모전 GitHub 제출 안내

이 ZIP은 GitHub 업로드를 위한 **단일 압축본**입니다. 안에 ZIP이 또 있는 구조를 정리했으며 파일 경로는 `/` 형식으로 정돈했습니다.

## GitHub 업로드 방법

1. 이 ZIP 파일을 **압축 해제**합니다.
2. 새 GitHub 저장소를 만들거나 기존 저장소를 엽니다.
3. 압축 해제된 파일/폴더(`app`, `backend`, `gradle`, `apk`, `README.md`, `gradlew` 등)를 **저장소 최상위 경로**에 올립니다. ZIP 파일 자체만 업로드하면 안 됩니다.
4. GitHub 웹페이지에서 `README.md`가 바로 보이는지, `apk/부릉이_앗차!_release.apk`가 존재하는지 확인합니다.
5. GitHub 저장소 URL과 **업로드 후 최종 커밋의 전체 SHA**를 복사해 공모전 제출 페이지 및 메일에 입력합니다.
6. 비공개 저장소라면 안내문에 따라 **2026-11-16(월)까지 Public 전환**, **2026-11-23(월)까지 공개 유지**해야 합니다.

## 실제 포함 파일

- `README.md`: 앱 소개·실행 방법·개발 환경·Pleos Connect 에뮬레이터 설정·화면 규격·안전상 한계
- `app/`: Android/Kotlin/Jetpack Compose 앱 소스 및 단위·UI 테스트
- `backend/hazard-relay/`: 주변 안전 알림 시연용 로컬 중계 서버
- `apk/부릉이_앗차!_release.apk`: release buildType에서 생성된 설치용 APK
- `SHA256SUMS.txt`: APK 무결성 확인 SHA-256

**참고:** 제공받은 APK는 release buildType을 **디버그 인증서로 서명한 시연용 설치 파일**이라고 원본 README에 기재되어 있습니다. 정식 상용 배포용 서명 APK라는 뜻은 아닙니다.

APK SHA-256: `34FF7E18850632E6AC0C54B810BD07616083E3A9FEF323A5F06F5823390D16D1`

## 7번 참고자료에 붙여넣을 내용

- GitHub 저장소: `여기에 GitHub 업로드 후 실제 저장소 URL 입력`
- 최종 커밋 SHA: `여기에 GitHub 업로드 후 생성된 최종 커밋 전체 SHA 입력`
- APK 위치: `apk/부릉이_앗차!_release.apk`
- 제공 자료: Android 앱 소스코드, 실행 가능한 시연용 APK, 테스트 코드, 사용 방법 및 Pleos Connect 환경 설명, 안전 알림 중계 서버 소스코드

시연 영상·화면 시안은 **이 ZIP에 포함되어 있지 않으며**, 별도로 준비한 경우에만 실제 링크를 추가하세요.
