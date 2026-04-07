## 사전 준비 — 인증 및 테스트 컨텍스트

1. **트레이너(TRAINER) 토큰 준비**
    - 테스트용 계정을 임시 생성한 후, `/users/me/role` API를 통해 `TRAINER` 권한으로 승급합니다.
    - 권한 갱신을 위해 재로그인을 수행하고, 반환된 `AccessToken`을 `Authorization: Bearer {Trainer_Token}` 형식으로 셋팅합니다.
    - 상점 생성, 수정, 단건 조회 등 메인 기능 API 호출에는 이 트레이너 토큰을 사용합니다.
2. **일반 사용자(USER) 토큰 준비**
    - 트레이너와 **다른 사용자 계정**으로 신규 가입 및 로그인하여 일반 `AccessToken`을 발급받습니다.
    - 권한 제어(403 Forbidden) 테스트 API 호출에는 이 일반 사용자 토큰을 사용합니다.
3. **테스트 데이터 전제**
    - 트레이너 토큰과 일반 사용자 토큰은 식별자가 서로 달라야 합니다.
    - `POST`나 `PATCH`가 아닌, 단일 `PUT` API 엔드포인트로 멱등성을 지닌 생성/수정(Upsert) 흐름을 전제합니다.

---

## 1. 권한 기반 통제 확인

### TC-MP-01. 일반 사용자 상점 등록 시도 (403)

- **Endpoint**: `PUT http://127.0.0.1:8080/marketplace/trainer/store`
- **Header**: `Authorization: Bearer {User_Token}`
- **Payload**: JSON

```json
{
  "storeName": "권한없는 상점",
  "address": "테스트 주소",
  "latitude": 37.5,
  "longitude": 127.0
}
```

- **기대 응답**: `401 Unauthorized` 또는 `403 Forbidden`

- **확인 포인트**
    - `ROLE_TRAINER` 권한이 분리되어 일반 유저의 마켓플레이스 무단 등록 시도가 차단된다.

---

## 2. 미등록 상점 조회 방어 확인

### TC-MP-01-A. 트레이너가 미등록 상태에서 상점 조회 (404)

- **Endpoint**: `GET http://127.0.0.1:8080/marketplace/trainer/store`
- **Header**: `Authorization: Bearer {Trainer_Token}`
- **Body**: 없음
- **기대 응답**: `404 Not Found`

- **확인 포인트**
    - DB 상에 `user_id` 매칭 레코드가 없을 시, `StoreNotFoundException`이 정상적으로 발생하여 클라이언트 측에 HTTP 404 상태로 안전하게 전달된다.

---

## 3. 상점 데이터 생성 (Upsert - Create)

### TC-MP-02. 트레이너가 신규 상점 등록

- **Endpoint**: `PUT http://127.0.0.1:8080/marketplace/trainer/store`
- **Header**: `Authorization: Bearer {Trainer_Token}`
- **Payload**: JSON

```json
{
  "storeName": "몸짱 트레이닝 센터",
  "address": "서울시 강남구 테헤란로 123",
  "detailAddress": "지하 1층",
  "latitude": 37.501,
  "longitude": 127.039,
  "phoneNumber": "02-1234-5678",
  "instagramUrl": "https://instagram.com/mztk_trainer"
}
```

- **기대 응답**: `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "storeId": 1
  }
}
```

---

## 4. 단건 조회 확인 (Read)

### TC-MP-03. 상점을 등록한 후 데이터 정상 조회

- **Endpoint**: `GET http://127.0.0.1:8080/marketplace/trainer/store`
- **Header**: `Authorization: Bearer {Trainer_Token}`
- **Body**: 없음
- **기대 응답**: `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "storeId": 1,
    "storeName": "몸짱 트레이닝 센터",
    "address": "서울시 강남구 테헤란로 123",
    "detailAddress": "지하 1층",
    "latitude": 37.501,
    "longitude": 127.039,
    "phoneNumber": "02-1234-5678",
    "instagramUrl": "https://instagram.com/mztk_trainer"
  }
}
```

- **확인 포인트**
    - `storeName`, `address`, `instagramUrl` 객체 매핑 정보가 정상적으로 저장됐으며, CQRS 관례상 조회 요청의 반환형 구조와 일치한다.

---

## 5. 상점 정보 수정 확인 (Upsert - Update)

### TC-MP-04. 등록된 상점의 정보를 부분 수정

- **Endpoint**: `PUT http://127.0.0.1:8080/marketplace/trainer/store`
- **Header**: `Authorization: Bearer {Trainer_Token}`
- **Payload**: JSON

```json
{
  "storeName": "우주최강 트레이닝 센터 (수정됨)",
  "address": "서울시 강남구 테헤란로 456",
  "latitude": 37.511,
  "longitude": 127.049,
  "phoneNumber": "010-9999-8888"
}
```

- **기대 응답**: `200 OK`

```json
{
  "status": "SUCCESS",
  "data": {
    "storeId": 1
  }
}
```

- **확인 포인트**
    - 수정 후 조회를 동일하게 수행하여 데이터베이스 상의 값이 중복 생성 없이 온전히 덮어씌워짐(Update)을 교차 검증한다.

---

## 6. 도메인 한계 및 유효성(Validation) 방어 확인

### TC-MP-05, TC-MP-06, TC-MP-07. 비정상 데이터 악의적 입력

- **Endpoint**: `PUT http://127.0.0.1:8080/marketplace/trainer/store`
- **Header**: `Authorization: Bearer {Trainer_Token}`
- **비정상 Payload 예시**: 
  - 필수 파라미터(이름, 좌표) 누락
  - 위도(Latitude) `999.0` 범위 이탈 (정상: -90~90)
  - 올바르지 않은 스키마(`ftp://instagram.weird`) 등 잘못된 URL 포맷
- **기대 응답**: `400 Bad Request`

- **확인 포인트**
    - DB 진입 전 500 에러 붕괴 없이 깔끔하게 도메인 객체 단위에서 생성 제약을 방어 후 클라이언트에게 400 반환 여부 검증.

---

## 7. DB 반영 확인 구조 흐름 요약

- **적용 기술**: PostgreSQL Native 쿼리 (`INSERT INTO ... ON CONFLICT (user_id) DO UPDATE SET ... RETURNING id`)
- **확인 메커니즘**:
    - **TC-MP-02 (최초 등록)** 에서는 기존에 존재하지 않는 `user_id`이므로 새 데이터 행이 `INSERT` 됩니다.
    - **TC-MP-04 (내용 수정)** 에서는 완전히 동일한 사용자 토큰(`user_id`)으로 접근했기 때문에, DB 레벨 PK/Unique 충돌 판정을 통해 추가 행 삽입 없이 기존 행 필드 덮어씌움(`UPDATE`)만 일어납니다.
    - Spring Security 인증 주체를 식별값으로 사용하기 때문에 타 트레이너의 데이터를 침범하거나 조회할 수 없습니다.

---

## 8. 상점 관리 테스트 요약

### TC-MP-SUM-1. 정상 흐름 요약

1. 일반 사용자가 상점 등록을 시도하면 거절당한다 (403).
2. 트레이너 사용자가 미등록 상태에서 상점 조회를 시도하면 404가 응답된다.
3. 트레이너 사용자가 신규 상점 정보를 입력해 생성(Upsert API) 응답 200 처리된다.
4. 응답으로 부여받은 `storeId`를 확인한다.
5. 단건 조회(GET) API를 통해 실제 정보가 매핑 되었음을 검증한다.
6. 다시 상점 정보 수정(Upsert API)을 통해 내용을 동일하게 PUT 날려 변경 응답을 받는다.
7. 다시 단건 조회를 해 데이터 교차 수정이 안전하게 증명됨을 판단한다.
8. 이상한 값이 주입되면 도메인 측에서 차단됨(400)을 확인한다.

---

## 9. 테스트 검증 종합 결과

- 권한 및 무단접근 필터링 검증: **성공 (PASS)**
- 멱등성을 지닌 상점 레코드 생성: **성공 (PASS)**
- 저장된 상점 정보 단건 조회: **성공 (PASS)**
- 멱등성을 지닌 상점 레코드 수정: **성공 (PASS)**
- 비정상 데이터 객체 생성 방어 로직: **성공 (PASS)**

---
