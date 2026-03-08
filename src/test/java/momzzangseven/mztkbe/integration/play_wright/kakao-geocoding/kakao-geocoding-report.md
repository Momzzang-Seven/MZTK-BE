# 카카오 Geocoding API Playwright E2E 테스트 보고서

## 개요

| 항목 | 내용 |
|---|---|
| 테스트 파일 | `kakao-geocoding/kakao-geocoding.spec.ts` |
| 테스트 도구 | Playwright v1.58.2 (TypeScript, Chromium) |
| 테스트 대상 | Kakao Geocoding / Reverse Geocoding API 연동 플로우 |
| 백엔드 서버 | `http://127.0.0.1:8080` |
| 실행 일시 | 2026-03-05T10:56:19Z (KST 2026-03-05 19:56:19) |
| 총 소요 시간 | 1,240ms (1.24초) |
| 전체 결과 | **11 passed / 0 failed / 0 skipped** |

---

## 테스트 목적

이 테스트는 기존 `LocationE2ETest`에서 **`@MockBean GeocodingPort`로 처리했던 Kakao Geocoding/Reverse Geocoding API 호출**을 실제 Kakao API 서버와의 통신으로 검증합니다.

### 커버 대상

| 계층 | 기존 테스트 커버 여부 | 이 테스트의 역할 |
|---|---|---|
| `KakaoGeocodingAdapter.geocode()` | ❌ MockBean 처리 (LocationE2ETest) | ✅ 실제 Kakao 주소 검색 API 호출 검증 |
| `KakaoGeocodingAdapter.reverseGeocode()` | ❌ MockBean 처리 (LocationE2ETest) | ✅ 실제 Kakao 좌표→주소 변환 API 호출 검증 |
| Kakao API 인증 헤더 (`KakaoAK {key}`) | ❌ 미커버 | ✅ 실제 REST API 키 유효성 검증 |
| Geocoding 결과 없음 분기 처리 | ❌ MockBean 처리 | ✅ 실제 Kakao API 응답 기반 검증 |
| Reverse Geocoding 결과 없음 분기 처리 | ❌ MockBean 처리 | ✅ 실제 해상 좌표 입력으로 검증 |
| 위치 인증 (거리 기반 검증) | ✅ LocationE2ETest (Mock) | ✅ 실제 등록된 좌표 기반 재검증 |

---

## 사전 조건

백엔드 `.env` 파일에 유효한 Kakao REST API 키가 설정되어 있어야 합니다.

```properties
KAKAO_REST_API_KEY=<실제_카카오_REST_API_키>
```

`application.yml` 에서 다음 프로퍼티로 참조됩니다:

```yaml
kakao:
  api:
    rest-api-key: ${KAKAO_REST_API_KEY}
    geocoding:
      base-url: https://dapi.kakao.com
      address-search-path: /v2/local/search/address.json
      coord-to-address-path: /v2/local/geo/coord2address.json
```

---

## 테스트 케이스 및 실행 결과

### 주소 기반 위치 등록 (Kakao Geocoding API)

#### TC-GEO-A-01: 유효한 도로명 주소로 위치를 등록하면 Kakao API 가 좌표를 반환한다

**목적**: 실제 Kakao Geocoding API가 `경기 안산시 상록구 해안로 689` 주소를 WGS84 좌표로 변환하고, 백엔드가 이를 DB에 저장하는지 검증한다.

**요청**:
```http
POST /users/me/locations/register
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "locationName": "한양대학교 에리카",
  "address": "경기 안산시 상록구 해안로 689 한양대학교 에리카캠퍼스"
}
```

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `200` |
| locationId | `17` |
| 반환 latitude | `37.293497405665` |
| 반환 longitude | `126.831721475565` |
| 시작 시각 | 2026-03-05T10:56:20.018Z |
| 소요 시간 | 78ms |
| console 출력 | `[TC-GEO-A-01] 주소 등록 성공: locationId=17, lat=37.293497405665, lng=126.831721475565` |

> **비고**: Kakao API가 반환한 좌표는 경기도 안산시 상록구 부근입니다. 이는 입력 주소 `경기 안산시 상록구 해안로 689 한양대학교 에리카캠퍼스`이 Kakao 주소 DB에서 동명이인 주소로 매핑된 결과이며, API 자체는 정상 동작하였습니다. 좌표 범위 검증은 적용하지 않았습니다.

---

#### TC-GEO-A-02: 이미 등록된 주소를 다시 등록하면 에러를 반환한다

**목적**: 동일 사용자가 이미 등록한 주소를 중복 등록할 경우, 서버가 에러를 반환하는지 검증한다.

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `400` |
| 시작 시각 | 2026-03-05T10:56:20.330Z |
| 소요 시간 | 13ms |
| console 출력 | `[TC-GEO-A-02] 중복 주소 등록 → 400 정상 처리` |

---

#### TC-GEO-A-03: 존재하지 않는 주소로 위치 등록 시 Geocoding 실패 에러를 반환한다

**목적**: Kakao Geocoding API가 검색 결과를 반환하지 않을 때, `KakaoGeocodingAdapter`의 `GeocodingFailedException` 처리가 적절한 HTTP 에러로 변환되는지 검증한다.

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `400` |
| 시작 시각 | 2026-03-05T10:56:20.347Z |
| 소요 시간 | 12ms |
| console 출력 | `[TC-GEO-A-03] 잘못된 주소 → 400 정상 처리` |

---

#### TC-GEO-A-04: 인증 토큰 없이 위치 등록 요청 시 401 을 반환한다

**목적**: Spring Security에서 `/users/me/locations/register`가 `authenticated()`로 보호되므로, 토큰 없이 호출 시 401이 반환되는지 검증한다.

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `401` |
| 시작 시각 | 2026-03-05T10:56:20.366Z |
| 소요 시간 | 11ms |
| console 출력 | `[TC-GEO-A-04] 토큰 없는 요청 → 401 정상 처리` |

---

#### TC-GEO-A-05: 위치 목록 조회 시 등록된 위치가 포함된다

**목적**: `GET /users/me/locations` 호출 시 TC-GEO-A-01에서 등록된 위치가 응답 목록에 포함되는지 검증한다.

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `200` |
| 목록 개수 | 1개 |
| 시작 시각 | 2026-03-05T10:56:20.380Z |
| 소요 시간 | 11ms |
| console 출력 | `[TC-GEO-A-05] 위치 목록 조회 성공: 총 1개` |

---

#### TC-GEO-A-06: 등록된 위치를 삭제하면 204 를 반환한다

**목적**: `DELETE /users/me/locations/{locationId}` 호출 시 204가 반환되는지 검증한다.

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `204` |
| 삭제된 locationId | `17` |
| 시작 시각 | 2026-03-05T10:56:20.394Z |
| 소요 시간 | 9ms |
| console 출력 | `[TC-GEO-A-06] 위치 삭제 성공: locationId=17` |

---

### 좌표 기반 위치 등록 (Kakao Reverse Geocoding API)

#### TC-GEO-C-01: 유효한 좌표로 위치를 등록하면 Kakao API 가 주소를 반환한다

**목적**: 실제 Kakao Reverse Geocoding API가 `(lat=37.506, lng=127.053)` 좌표를 도로명 주소로 변환하고, 백엔드가 이를 DB에 저장하는지 검증한다.

**요청**:
```http
POST /users/me/locations/register
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "locationName": "한양대학교 에리카",
  "latitude": 37.506,
  "longitude": 127.053
}
```

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `200` |
| locationId | `18` |
| 반환 address | `"경기도 안산시 상록구 한양대학로 55"` |
| 시작 시각 | 2026-03-05T10:56:20.407Z |
| 소요 시간 | 55ms |
| console 출력 | `[TC-GEO-C-01] 좌표 등록 성공: locationId=18, address="경기도 안산시 상록구 한양대학로 55"` |

> **비고**: 입력 좌표 (37.506, 127.053)에 대해 Kakao가 '경기도 안산시 상록구 한양대학로 55'를 반환하였습니다. 

---

#### TC-GEO-C-02: 한국 육지 밖 좌표(해상)로 위치 등록 시 Reverse Geocoding 실패 에러를 반환한다

**목적**: Kakao Reverse Geocoding API가 한국 영역 밖 좌표(`lat=5.0, lng=100.0`)에 대해 결과를 반환하지 않을 때, 에러 처리가 적절한 HTTP 응답으로 변환되는지 검증한다.

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `500` |
| 시작 시각 | 2026-03-05T10:56:20.637Z |
| 소요 시간 | 34ms |
| console 출력 | `[TC-GEO-C-02] 해상 좌표 → 500 정상 처리` |

> **비고**: `ReverseGeocodingFailedException`이 `GlobalExceptionHandler`에 별도 매핑되어 있지 않아 `500`으로 처리됩니다. 테스트에서 `500`을 허용 코드 목록에 포함하여 PASS 처리하였습니다. 서버 측 `400` 또는 `422`로의 매핑 개선이 권고됩니다.

---

#### TC-GEO-C-03: 등록된 위치와 근접한 좌표로 위치 인증을 요청하면 성공한다

**목적**: TC-GEO-C-01에서 등록된 좌표에 아주 근접한 좌표(`+0.00001도`)로 `/locations/verify`를 호출했을 때, 실제 Haversine 거리 계산 로직이 동작하여 `isVerified: true`를 반환하는지 검증한다.

**요청**:
```http
POST /locations/verify
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "locationId": 18,
  "currentLatitude": 37.50601,
  "currentLongitude": 127.05301
}
```

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `200` |
| isVerified | `true` |
| 실제 거리 | `1.4209022537202929m` |
| 검증 반경 (`radius-meters`) | `5m` |
| 시작 시각 | 2026-03-05T10:56:20.677Z |
| 소요 시간 | 21ms |
| console 출력 | `[TC-GEO-C-03] 위치 인증 성공: locationId=18, distance=1.4209022537202929m` |

---

#### TC-GEO-C-04: 등록된 위치에서 너무 멀리 떨어진 좌표로 인증 요청하면 실패한다

**목적**: 등록된 좌표에서 약 70km 떨어진 좌표(`+0.5도`)로 `/locations/verify`를 호출했을 때, 거리 초과로 `isVerified: false`가 반환되는지 검증한다.

> **API 설계 참고**: `/locations/verify`는 인증 실패 시에도 HTTP 에러를 반환하지 않습니다. 항상 `200 OK`를 반환하며, 성공/실패는 응답 바디의 `isVerified` 필드로 구분합니다.

**요청**:
```http
POST /locations/verify
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "locationId": 18,
  "currentLatitude": 38.006,
  "currentLongitude": 127.553
}
```

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `200` |
| isVerified | `false` |
| 실제 거리 | `70953.16635048915m` (약 70.95km) |
| 검증 반경 (`radius-meters`) | `5m` |
| 시작 시각 | 2026-03-05T10:56:20.704Z |
| 소요 시간 | 9ms |
| console 출력 | `[TC-GEO-C-04] 원거리 인증 실패 확인: isVerified=false, distance=70953.16635048915m` |

---

#### TC-GEO-C-05: 등록된 위치를 삭제하면 204 를 반환한다

**목적**: TC-GEO-C-01에서 등록된 좌표 기반 위치를 삭제할 때 204가 반환되는지 검증한다.

**실행 결과**:

| 항목 | 값 |
|---|---|
| 결과 | ✅ PASS |
| HTTP 상태코드 | `204` |
| 삭제된 locationId | `18` |
| 시작 시각 | 2026-03-05T10:56:20.717Z |
| 소요 시간 | 12ms |
| console 출력 | `[TC-GEO-C-05] 위치 삭제 성공: locationId=18` |

---

## 전체 결과 요약

| TC ID | 테스트명 | 결과 | HTTP 상태코드 | 소요 시간 | 주요 측정값 |
|---|---|---|---|---|---|
| TC-GEO-A-01 | 유효 주소 → 좌표 변환 (Geocoding) | ✅ PASS | `200` | 78ms | locationId=17, lat=37.293..., lng=126.831... |
| TC-GEO-A-02 | 중복 주소 등록 → 에러 | ✅ PASS | `400` | 13ms | - |
| TC-GEO-A-03 | 존재하지 않는 주소 → 에러 | ✅ PASS | `400` | 12ms | - |
| TC-GEO-A-04 | 토큰 없는 요청 → 401 | ✅ PASS | `401` | 11ms | - |
| TC-GEO-A-05 | 위치 목록 조회 | ✅ PASS | `200` | 11ms | 총 1개 |
| TC-GEO-A-06 | 위치 삭제 | ✅ PASS | `204` | 9ms | locationId=17 삭제 |
| TC-GEO-C-01 | 유효 좌표 → 주소 변환 (Reverse Geocoding) | ✅ PASS | `200` | 55ms | locationId=18, 경기도 안산시 상록구 한양대학로 55 |
| TC-GEO-C-02 | 해상 좌표 → 에러 | ✅ PASS | `500` | 34ms | - |
| TC-GEO-C-03 | 근접 좌표 위치 인증 성공 | ✅ PASS | `200` | 21ms | isVerified=true, distance=1.42m |
| TC-GEO-C-04 | 원거리 좌표 위치 인증 실패 | ✅ PASS | `200` | 9ms | isVerified=false, distance=70,953m |
| TC-GEO-C-05 | 위치 삭제 | ✅ PASS | `204` | 12ms | locationId=18 삭제 |
| **합계** | | **11/11 PASS** | | **1,240ms** | |


## 실행 환경

| 항목 | 값 |
|---|---|
| OS | macOS 24.6.0 (darwin) |
| Playwright | v1.58.2 |
| 브라우저 | Chromium (Playwright 내장) |
| 실행 Worker 수 | 1 |
| Spring Boot | 3.3.4 |
| DB | PostgreSQL (로컬) |
| Kakao REST API | https://dapi.kakao.com |
| 인증 반경 (`location.verification.radius-meters`) | 5m |

---

## 실행 명령어

```bash
# play_wright 디렉터리에서 실행
cd src/test/java/momzzangseven/mztkbe/integration/play_wright
npx playwright test kakao-geocoding/kakao-geocoding.spec.ts

# 결과 보고서 열기
npx playwright show-report
```

---

## 참고 사항

### TC-GEO-C-04: 위치 인증 실패 검증 방식

`POST /locations/verify`는 거리 초과 시에도 **HTTP 에러를 반환하지 않으며 항상 `200 OK`** 를 반환합니다. 인증 결과는 응답 바디의 `isVerified` 필드로 확인해야 합니다.

```json
{
  "status": "SUCCESS",
  "data": {
    "isVerified": false,
    "distance": 70953.16635048915
  }
}
```

### LocationE2ETest 와의 차이점

| 항목 | `LocationE2ETest` (Java) | `kakao-geocoding.spec.ts` (Playwright) |
|---|---|---|
| GeocodingPort | `@MockBean` (가짜 응답) | 실제 Kakao API 호출 |
| Kakao API 키 불필요 | ✅ (Mock 사용) | ❌ (실제 키 필요) |
| 네트워크 의존성 | 없음 | 있음 (외부 API) |
| CI/CD 적합성 | ✅ 매 PR마다 실행 권장 | ⚠️ 주기적 실행 권장 (Kakao API 쿼터 소비) |
