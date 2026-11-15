# 주변 안전 알림 중계 서버 (hazard-relay)

차량 A가 만든 **익명 주의 알림**을 주변 차량·보행자 기기에 전달하는 최소 중계 서버다.
외부 계정·클라우드·추가 라이브러리 없이 JDK만으로 실행한다.

## 실행

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" backend/hazard-relay/HazardRelayServer.java 8787
# 선택: 토큰 요구
$env:RELAY_TOKEN = "<임의 문자열>"   # 실제 값은 저장소에 기록하지 않는다
```

- 에뮬레이터 앱은 `http://10.0.2.2:8787` 로 접속한다(`RelayEndpoint.DEFAULT_BASE_URL`).
- 앱 우측 하단 칩을 눌러(정차 P 상태) `꺼짐 → 차량 수신 → 보행자 수신` 으로 바꾼다. 기본값은 꺼짐(MOCK 전용).
- 중계가 켜진 상태에서 신고 초안의 "주변 차량에 주의 알림 보내기" / "보행자 알림"을 누르면 앱 내부 시연과 함께 서버에도 발행한다.

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/health` | 상태 확인 |
| POST | `/v1/vehicle-alerts` | `{alertId, latitude, longitude, headingDegrees, level="STRONG", isDemo}` |
| GET | `/v1/vehicle-alerts?cells=lat:lon,...` | 약 1km 격자(최대 25칸)의 유효 알림 |
| POST | `/v1/pedestrian-alerts` | `{alertId, latitude, longitude, isDemo}` |
| GET | `/v1/pedestrian-alerts?cells=...` | 위와 동일 |

응답의 알림에는 절대 시각 대신 `ageMillis`, `remainingMillis` 를 넣어 기기 간 시계 차이를 피한다.

## 안전·개인정보 장치

- 메모리 저장만 사용, 재시작하면 모두 삭제. 디스크·로그에 위치를 남기지 않는다.
- 필드 화이트리스트: 차량번호·차종·신고자 식별자 등 다른 키가 있으면 400으로 거절한다.
- 서버 시계 기준 TTL 90초(클라이언트 값을 믿지 않음), 같은 IP 발행 30초 1회, 본문 2KB 제한, 저장 1,000건 제한.
- 발행 위치는 앱에서 약 100m 격자로 낮춘 값이다. 수신 기기는 약 1km 격자 9칸만 조회하고 정밀 거리·방향·만료·중복·빈도 판단은 기기 안에서 한다.
- 수신 문구는 "이상 주행 차량 제보"이며 "음주"라고 단정하지 않는다.

## 실서비스 전 반드시 필요한 것 (현재 미충족)

1. HTTPS 도메인 배포와 `network_security_config.xml` 의 개발용 HTTP 예외 제거
2. 기기 인증(App Check 등)과 토큰 발급, 사용자·기기 단위 발행 제한, 허위 제보 신고·정정·취소 절차
3. 위치정보법상 위치기반서비스사업 신고, 개인위치정보 수집·제공 동의 및 처리방침
4. 보행자용 별도 앱과 푸시(FCM 등) — 현재는 같은 앱의 "보행자 수신" 모드가 화면 안 배너로 표시
5. 다중 서버·영속 저장이 필요하면 외부 저장소 선택(현재는 단일 프로세스 메모리)
