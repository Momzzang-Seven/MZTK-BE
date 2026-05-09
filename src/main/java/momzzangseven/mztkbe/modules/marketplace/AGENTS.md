# marketplace/ — PT 거래소 SSoT

> 본 파일은 marketplace 4 sub-module 의 책임 / API 청사진. 상위 module 개요는 `../AGENTS.md`.
> 모든 응답은 `ApiResponse<T>` 래퍼.

## 구성

```
marketplace/
├── classes/      ← 클래스 등록·조회·예약 진입점
├── reservation/  ← 예약 도메인 (트레이너 측 처리)
├── sanction/     ← TRAINER 제재 (controller 없음, application only)
└── store/        ← TRAINER 스토어 (소개 페이지)
```

권한
- `/marketplace/trainer/**` 는 SecurityConfig 에서 TRAINER role 로 제한.
- `/marketplace/classes/**` 의 `GET` 일부는 public (anonymous 허용).
- `/marketplace/me/**` 는 인증된 일반 사용자.

## 1. marketplace/classes — 클래스 등록 & 예약 진입

책임: TRAINER 가 PT 클래스를 등록·수정·active 토글하고, USER 는 검색/상세/4주 가용 슬롯 조회.
USER 의 예약 생성·취소·완료 진입점도 이 sub-module — 비즈니스 로직은 `reservation/` UseCase 호출.

컨트롤러: `ClassController`, `ClassReservationController`.

### TRAINER (클래스 운영)

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/marketplace/trainer/classes` | `RegisterClassRequestDTO` | 201 `RegisterClassResponseDTO` | |
| PUT | `/marketplace/trainer/classes/{classId}` | `UpdateClassRequestDTO` | `UpdateClassResponseDTO` | |
| PATCH | `/marketplace/trainer/classes/{classId}/status` | path only | `ToggleClassStatusResponseDTO` | active ↔ inactive |
| GET | `/marketplace/trainer/classes` | `?page=` | `GetTrainerClassesResponseDTO` | active + inactive 모두 |

### Public (검색·열람)

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/marketplace/classes` | `?lat=&lng=&category=&sort=&trainerId=&startTime=&endTime=&page=` | `GetClassesResponseDTO` | lat/lng 없이 sort=DISTANCE 시 RATING fallback |
| GET | `/marketplace/classes/{classId}` | path | `GetClassDetailResponseDTO` | |
| GET | `/marketplace/classes/{classId}/reservation-info` | path | `GetClassReservationInfoResponseDTO` | 4주 슬롯별 잔여 capacity |

### USER (예약 진입점)

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/marketplace/classes/{classId}/reservations` | `CreateReservationRequestDTO` | `CreateReservationResponseDTO` | PENDING + 토큰 hold |
| GET | `/marketplace/me/reservations` | `?status=` | `List<ReservationSummaryResponseDTO>` | |
| GET | `/marketplace/reservations/{id}` | path | `ReservationDetailResponseDTO` | USER + TRAINER 공용 |
| PATCH | `/marketplace/me/reservations/{id}/cancel` | path | `CancelPendingReservationResponseDTO` | PENDING 만 취소 가능 |
| PATCH | `/marketplace/me/reservations/{id}/complete` | path | `CompleteReservationResponseDTO` | 완료 확인 → 토큰 정산 |

## 2. marketplace/reservation — 예약 도메인 (TRAINER 측)

책임: 예약 도메인 모델 (PENDING/APPROVED/REJECTED/CANCELED/COMPLETED) 의 SSoT, 트레이너의
승인·거절. USER 측 진입은 `classes/` controller 에서 호출.

컨트롤러: `ReservationTrainerController` (`/marketplace/trainer/reservations`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/marketplace/trainer/reservations` | `?status=` | `List<ReservationSummaryResponseDTO>` | TRAINER 본인에게 들어온 예약 |
| GET | `/marketplace/trainer/reservations/{id}` | path | `ReservationDetailResponseDTO` | |
| PATCH | `/marketplace/trainer/reservations/{id}/approve` | path | `ApproveReservationResponseDTO` | |
| PATCH | `/marketplace/trainer/reservations/{id}/reject` | `RejectReservationRequestDTO{reason}` | `RejectReservationResponseDTO` | token 환불 트리거 |

## 3. marketplace/sanction — TRAINER 제재

책임: 환불 정책 위반 등 트레이너 자동/수동 제재 (예: 일시 정지, 신규 클래스 등록 제한).
Application 계층만 존재 — HTTP API 없음. admin 또는 시스템 이벤트가 호출.

## 4. marketplace/store — TRAINER 스토어

책임: 트레이너 자기소개 페이지 (storeName, intro, banner 등) upsert / 조회.

컨트롤러: `StoreController` (`/marketplace/trainer/store`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| PUT | `/marketplace/trainer/store` | `UpsertStoreRequestDTO` | `UpsertStoreResponseDTO` | create/update 모두 200 |
| GET | `/marketplace/trainer/store` | — | `GetStoreResponseDTO` | TRAINER 본인 store 1건 |

## Cross-cutting 규칙 (marketplace 전반)

- 인증: `@AuthenticationPrincipal Long trainerId | userId`. null → `UserNotAuthenticatedException`.
- 결제는 ERC-20 토큰 hold/release 패턴: 예약 생성 시 hold, 완료/거절/취소 시 release/refund.
  실제 on-chain 호출은 `web3/transfer/` 위임.
- TRAINER 본인 자원 검증은 UseCase 가 책임 — controller 는 path/role 검증까지만.
- 응답 DTO 는 `XxxResponseDTO.from(result)` 정적 팩토리 변환을 강제 (3-step 패턴).
