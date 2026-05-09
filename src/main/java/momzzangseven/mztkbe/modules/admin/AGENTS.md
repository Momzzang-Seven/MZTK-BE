# admin/ — 관리자 모듈 SSoT

> 본 파일은 admin 본체 + 4 sub-module 의 책임 / API 청사진. 상위 module 개요는
> `../AGENTS.md`. 모든 응답은 `ApiResponse<T>` 래퍼.

## 구성

```
admin/
├── api/  application/  domain/  infrastructure/   ← admin 본체 (account/auth/recovery)
├── board/        ← 게시글·댓글 모더레이션
├── common/       ← admin 모듈 내부 공용 application 코드 (controller 없음)
├── dashboard/    ← 통계 카드
└── user/         ← 사용자 관리 (차단/해제)
```

권한: `/admin/**` 는 SecurityConfig 에서 ADMIN role 로 제한 (단, `/admin/recovery/**` 만 예외 —
JWT principal 없이 호출 가능한 break-glass 경로).

## 1. admin (본체) — 관리자 계정 / 인증 / break-glass

책임: 관리자 계정 CRUD (peer-create / peer-password-reset), 본인 비밀번호 rotation,
자격 증명 분실 시 reseed (catastrophic recovery).

컨트롤러: `AdminAccountController`, `AdminAuthController`, `AdminRecoveryController`.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/admin/accounts` | (auth principal only) | 201 `CreateAdminAccountResponseDTO` | username + 임시 password |
| GET | `/admin/accounts` | — | `ListAdminAccountsResponseDTO` | 활성 admin summary list |
| POST | `/admin/accounts/{userId}/password/reset` | path | `ResetPeerAdminPasswordResponseDTO` | peer-reset, 임시 password 재발급 |
| POST | `/admin/auth/password` | `RotateAdminPasswordRequestDTO{currentPassword, newPassword}` | `Void` | 본인 rotation |
| POST | `/admin/recovery/reseed` | `RecoveryReseedRequestDTO` (+ `X-Forwarded-For`) | `RecoveryReseedResponseDTO` | JWT principal 미사용, IP 기반 audit |

주의: `infrastructure/` 에 `bootstrap`, `credential`, `delivery`, `recovery`, `sm` 다섯 서브패키지.
KMS / Secrets Manager / 이메일 발송 등 외부 시스템 호출 집중. `delivery/` 는 복구 코드 전달 채널.

## 2. admin/board — 게시판 모더레이션

책임: 게시글·댓글 admin 뷰 조회, 차단(ban). 댓글 ban 은 soft-delete + 모더레이션 기록 저장,
post ban 은 정책 미확정 (현재 reject 응답).

컨트롤러: `AdminBoardController` (`/admin/boards`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/admin/boards/posts` | `@ModelAttribute GetAdminBoardPostsRequestDTO` | `Page<AdminBoardPostResponseDTO>` | filter/sort |
| GET | `/admin/boards/posts/{postId}/comments` | `@ModelAttribute GetAdminBoardPostCommentsRequestDTO` | `Page<AdminBoardCommentResponseDTO>` | |
| POST | `/admin/boards/posts/{postId}/ban` | `AdminBoardBanRequestDTO{reason, ...}` | `AdminBoardModerationResponseDTO` | 정책 미확정, 현재 거부 |
| POST | `/admin/boards/comments/{commentId}/ban` | `AdminBoardBanRequestDTO` | `AdminBoardModerationResponseDTO` | soft-delete + 기록 |

## 3. admin/dashboard — 통계 카드

책임: MOM-239 사용자 통계, MOM-240 board 모더레이션 통계.

컨트롤러: `AdminDashboardController` (`/admin/dashboard`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/admin/dashboard/user-stats` | — | `AdminUserStatsResponseDTO` | 가입자/활성/차단/role 분포 |
| GET | `/admin/dashboard/post-stats` | — | `AdminBoardStatsResponseDTO` | post·comment·ban 건수 |

주의: `infrastructure/` 만 존재 (`domain/` 없음). 통계는 read-only projection, write port 없음.

## 4. admin/user — 사용자 관리

책임: 사용자 목록 조회 (filter/sort), ACTIVE ↔ BLOCKED 상태 변경.

컨트롤러: `AdminUserController` (`/admin/users`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/admin/users` | `@ModelAttribute GetAdminUsersRequestDTO` (search/role/status) | `Page<AdminUserListItemResponseDTO>` | |
| PATCH | `/admin/users/{userId}/status` | `AdminUserStatusChangeRequestDTO{status, reason?}` | `AdminUserStatusChangeResponseDTO` | ACTIVE ↔ BLOCKED |

## 5. admin/common — 내부 공용

`application/` 만 존재. 다른 admin sub-module 들이 공유하는 application service / DTO.
HTTP API 없음 — 외부 노출 금지.

## Cross-cutting 규칙 (admin 전반)

- operator 식별은 `@AuthenticationPrincipal Long operatorUserId`. null → `UserNotAuthenticatedException`.
- 단, `/admin/recovery/**` 는 예외: JWT principal 자체가 없을 수 있음. IP 기반 audit.
- 모든 operation 은 `audit/` 로깅 (누가·언제·무엇을). 변경은 가능한 한 idempotent.
- TRAINER 권한은 admin 관할이 아님 — `marketplace/` 의 SecurityConfig 가 별도 처리.
