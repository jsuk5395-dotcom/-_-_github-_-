# 앗차! — GitHub 제출 패키지

## 앱 이름 및 한 줄 소개

- 팀명: **부릉이**
- 앱명: **앗차!**

**앗차!**는 차량의 이상 주행 단서를 설명 가능한 규칙으로 분석하고, 탑승자의 확인을 거쳐 안전한 정차·신고 준비·주변 차량 및 보행자 주의 알림을 돕는 Pleos Connect 차량용 안전 보조 PoC입니다.

> 본 앱은 음주 여부를 판정하거나 운전 가능 여부를 보증하지 않습니다. 자동 신고, 실제 차량 제어, 상용 V2X 송신 기능도 제공하지 않습니다.

## 제출 파일 구성

```text
.
├─ app/                         # Android 앱 소스와 테스트
├─ backend/hazard-relay/        # 공모전 시연용 메모리 기반 안전 알림 중계 서버
├─ gradle/                      # 버전 카탈로그와 Gradle Wrapper
├─ apk/
│  └─ 부릉이_앗차!_release.apk   # 설치 가능한 release buildType APK
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ gradlew
├─ gradlew.bat
└─ README.md
```

공식 등록 팀명 **부릉이**를 반영해 APK 파일명을 다음 형식으로 확정했습니다.

```text
부릉이_앗차!_release.apk
```

## 실행 방법

### 1. 준비 환경

- Android Studio
- Android Studio 내장 JDK
- Android SDK Platform API 37
- Android SDK Build Tools 36.0.0 이상
- Android SDK Platform-Tools
- Gradle Wrapper 9.6.0
- 테스트 대상: Android Automotive 또는 공식 Pleos Connect AVD

### 2. 소스 빌드 및 검사

Windows PowerShell에서 저장소 루트를 기준으로 실행합니다.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Gradle release 산출물: `app/build/outputs/apk/release/app-release-unsigned.apk`
- 제출용 사전 서명 APK: `apk/부릉이_앗차!_release.apk`

보안상 서명키는 저장소에 포함하지 않았습니다. 제출용 APK는 **release buildType을 Android 디버그 인증서로 서명한 공모전 시연용 APK**이므로 설치·실행은 가능하지만 Google Play 배포용 인증서로 사용하면 안 됩니다.

### 3. APK 설치와 실행

연결된 기기 또는 에뮬레이터의 serial을 확인합니다.

```powershell
adb devices -l
adb -s <serial> install -r "apk\부릉이_앗차!_release.apk"
adb -s <serial> shell am start -W -n com.example.pnu_me/.MainActivity
```

기존에 다른 인증서로 설치한 `com.example.pnu_me`가 있으면 서명 충돌이 발생할 수 있습니다. 이 경우 기존 앱의 데이터를 보존할 필요가 없는지 확인한 뒤 제거하고 다시 설치해야 합니다.

### 4. 주변 안전 알림 중계 시연

JDK만 사용해 로컬 중계 서버를 실행합니다.

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" backend/hazard-relay/HazardRelayServer.java 8787
```

Android Emulator의 앱은 `http://10.0.2.2:8787`로 PC의 서버에 접근합니다. 앱의 중계 기능은 기본적으로 꺼져 있으며 정차 상태에서 사용자가 수신 모드를 선택해야 네트워크를 사용합니다.

## 테스트 환경 및 필요한 설정

### 공식 Pleos Connect 시험 환경

- Pleos Connect: **v2.1.0**
- Android API: **34**
- 공식 시스템 이미지: `system-images;android-34;pleos_car_emulator;x86_64`
- AVD 예시 이름: `Pleos_Connect_v2.1.0_2`
- 화면 규격: **2560×1440**
- RAM: **4GB**
- 부팅 방식: **Cold Boot**
- Vehicle Service 확인 버전: **2.3.22**
- Virtual Console: `adb forward tcp:8088 tcp:8088`

AVD 이름에 Pleos가 포함되어도 Google 일반 AAOS 이미지일 수 있으므로 시스템 이미지 공급자와 `image.sysdir.1`을 확인해야 합니다.

### 앱 설정

- Application ID: `com.example.pnu_me`
- minSdk: 28
- compileSdk/targetSdk: 37
- Kotlin: 2.2.10
- Jetpack Compose Material 3
- Android Car `PERF_VEHICLE_SPEED` 사용 시 `android.car.permission.CAR_SPEED` 런타임 권한 필요
- 신고자 위치 1회 조회 시 위치 권한 필요
- 안전 알림 중계 사용 시 로컬 서버 실행 필요

### 화면 검증 규격

- 차량 가로 화면: 2560×1440
- 좁은 대체 화면: 800×1280
- 상태는 색상만이 아니라 문자로 함께 구분
- MOCK과 VEHICLE 출처를 화면에서 구분
- 주행 제한 또는 상태 확인 불가 시 복잡한 조작 차단

## APK 위치

GitHub 저장소에 반드시 다음 경로를 유지하세요.

```text
apk/부릉이_앗차!_release.apk
```

APK가 GitHub 단일 파일 업로드 제한을 초과하면 GitHub Releases에 등록하고, 이 README와 접수 페이지에 해당 Release URL을 적으세요.

## 현재 소스 기준

- 패키징 브랜치: `feature/hazard-relay-server`
- 패키징 시점 HEAD: `a8d530687f632268ecdef9620ce39017baa65dc2`
- 이 ZIP은 위 커밋 이후의 **현재 작업 트리 변경 내용도 포함**합니다.
- GitHub 업로드 담당자는 ZIP을 푼 뒤 최종 diff를 확인하고 새 커밋을 생성해야 합니다.
- 접수 메일에는 GitHub에 실제 업로드한 뒤 생성된 **최종 커밋 전체 SHA**를 적어야 합니다.

## 제출 담당자 체크리스트

1. ZIP을 풀고 내용물을 GitHub 저장소 루트에 업로드합니다.
2. APK 파일명이 `부릉이_앗차!_release.apk`이고 README의 경로와 일치하는지 확인합니다.
3. `local.properties`, `.idea/`, `.gradle/`, `build/`, 서명키, 토큰, 참가신청서 원본을 추가하지 않습니다.
4. GitHub에서 최종 커밋을 만든 뒤 전체 SHA를 복사합니다.
5. 저장소 주소와 `apk/부릉이_앗차!_release.apk` 경로를 접수 페이지·메일에 기재합니다.
6. 비공개 저장소를 사용한다면 공모전 안내에 따라 2026-11-16까지 공개로 전환하고, 1차 결과 발표일인 2026-11-23까지 공개 상태를 유지합니다.
7. GitHub 업로드가 어렵다면 이 ZIP 자체의 내려받기 링크를 접수 메일로 제출할 수 있습니다.

## 안전 및 구현 범위

- 이상 주행 패턴은 연구용 규칙 기반 분석이며 음주를 판정하지 않습니다.
- 112 연계는 사용자 승인 후 외부 앱에 초안을 인계하며 신고 완료를 보장하지 않습니다.
- 주변 차량·보행자 알림은 약 100m 격자 위치, 반경 1km, 90초 TTL을 사용합니다.
- 실제 SMS·재난문자·OEM 지도 주입·ADAS 제어는 포함하지 않습니다.
- 중계 서버는 공모전 시연용 단일 프로세스 메모리 서버입니다. 실서비스 전 HTTPS, 기기 인증, 위치정보·개인정보 법무 검토가 필요합니다.
