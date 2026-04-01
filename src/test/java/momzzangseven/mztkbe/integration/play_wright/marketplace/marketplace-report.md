# 마켓플레이스 트레이너 상점 API E2E 테스트 결과 보고서

- **실행일시**: 2026-04-02 01:34:34 KST
- **테스터**: gimgyuwon
- **대상 서버**: http://127.0.0.1:8080 (로컬 Spring Boot)
- **DB 동작 방식**: PostgreSQL Native Upsert (`ON CONFLICT`)
- **API 도메인**: `/marketplace/trainer/store`
- **Playwright 버전**: 1.58.2
- **총 소요 시간**: 약 1.5초 (전체 8개 테스트 흐름)

---

## 테스트 시나리오 결과

| # | 시나리오 | API Endpoint | Method | 결과 | 소요 시간 | 비고 |
|---|---|---|---|---|---|---|
| TC-MP-01 | 일반 사용자는 상점을 등록할 수 없다 (403) | `/marketplace/trainer/store` | `PUT` | ✅ PASS | 15ms | 일반 유저 권한 접근 거부 확인 |
| TC-MP-01-A | 미등록 상태에서 단건 조회 시 에러 반환 (404) | `/marketplace/trainer/store` | `GET` | ✅ PASS | 27ms | `StoreNotFoundException` 캐치 |
| TC-MP-02 | 트레이너가 정상적으로 신규 상점을 등록한다 | `/marketplace/trainer/store` | `PUT` | ✅ PASS | 24ms | Native Upsert 로 신규 생성 (200 OK) |
| TC-MP-03 | 상점을 등록한 후 단건 조회가 정상 동작한다 | `/marketplace/trainer/store` | `GET` | ✅ PASS | 20ms | DB 매핑된 응답값(이름, 주소, URL 등) 검증 |
| TC-MP-04 | 등록된 상점의 정보를 PUT 방식으로 수정한다 | `/marketplace/trainer/store` | `PUT` | ✅ PASS | 16ms | 덮어쓰기 완료, 수정된 데이터 응답 교차 검증 |
| TC-MP-05 | 필수 파라미터(이름, 좌표 등) 누락 시 에러 반환 | `/marketplace/trainer/store` | `PUT` | ✅ PASS | 15ms | `NotBlank` 등 도메인 검증 룰 작동 (400) |
| TC-MP-06 | 위경도 범위(-90~90, -180~180) 이탈 값 입력 | `/marketplace/trainer/store` | `PUT` | ✅ PASS | 27ms | 잘못된 유도탄 좌표 Validation 블락 확인 (400) |
| TC-MP-07 | 올바르지 않은 URL(미지원 프로토콜 등) 입력 | `/marketplace/trainer/store` | `PUT` | ✅ PASS | 24ms | URI 파싱 및 `http`/`https` 프로토콜 체크 반환 (400) |

**총계: 8/8 PASS · 실패 0 · 스킵 0 · Flaky 0**

---

## 실패 항목

없음

---

## 비고

- **권한 격리 (RBAC)**: `ROLE_TRAINER` 권한이 부여되지 않은 일반 사용자 토큰 요청에 대해 `403 Forbidden` 필터링이 안전하게 작동함을 확인 (TC-MP-01).
- **멱등성 및 원자성 보장**: 기존 `SELECT` -> 로직 판단 -> `INSERT/UPDATE` 로 일어날 수 있는 데이터 교차 오염을 방지하고자 도입된 프레임워크 밖의 `INSERT ... ON CONFLICT DO UPDATE` 네이티브 쿼리가 문제 없이 동작함을 입증 (TC-MP-02 생성, TC-MP-04 수정).
- **예외 처리 (Domain Validation)**: 위경도 한계치 초과(TC-MP-06), URL 스키마 불합치(TC-MP-07), 파라미터 누락(TC-MP-05) 등 올바르지 않은 트래픽 유입 시 `500 Server Error` 붕괴로 번지지 않고, 정확하게 도메인 객체 단위에서 방어되어 HTTP `400 Bad Request` 로 깔끔하게 반환됨.
- **커맨드-쿼리 분담 보장**: 생성/수정 요청 후 응답 페이로드가 오직 식별자(`storeId`)만 반환하는 스펙이 이행됨을 확인했으며, 모든 검증은 다시 `GET` 요청을 거치도록 설계하여 실 사용 생태계 흐름을 완벽하게 재현.
- **테스트 계정 독립성 보장**: `trainer-{timestamp}@mztk-test.com`, `user-{timestamp}@mztk-test.com` 등 실행 시마다 매번 새로운 회원가입, 역할 승급, 인증 JWT 토큰 발급의 독립적 E2E 사이클을 유지하고 있음.
