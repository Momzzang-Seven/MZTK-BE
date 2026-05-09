# modules/ — Business Modules SSoT

> Single source of truth for module roles, responsibilities, and HTTP API contracts.
> 본 파일은 14개 top-level module 의 개요. 하위 module 이 있는 경우 (admin, marketplace, web3)
> 각 디렉토리의 `AGENTS.md` 를 참조한다.
> 모든 module 은 hexagonal layout (`api/` · `application/` · `domain/` · `infrastructure/`).
> 모든 응답은 `ApiResponse<T>` 로 래핑된다 (`global/response/ApiResponse`).

## 1. account/ — 인증 & 계정 lifecycle

책임: LOCAL + OAuth2 (Kakao/Google) 로그인, 회원가입, refresh token rotation, soft-delete &
reactivate, withdrawal, step-up authentication. 컨트롤러: `AccountController` (`/auth/**`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/auth/login` | `LoginRequestDTO` | `LoginResponseDTO` + refresh cookie | DELETED → 409 USER_WITHDRAWN |
| POST | `/auth/reactivate` | `ReactivateRequestDTO` | `LoginResponseDTO` + refresh cookie | public, JWT filter 예외 |
| POST | `/auth/signup` | `SignupRequestDTO` | `SignupResponseDTO` | LOCAL 가입 |
| POST | `/auth/reissue` | `refreshToken` cookie | `ReissueTokenResponseDTO` | rotation |
| POST | `/auth/logout` | `refreshToken` cookie | 204 | cookie 무효화 |
| POST | `/auth/withdrawal` | (auth principal) | `Void` | step-up 후 soft-delete |
| POST | `/auth/stepup` | `StepUpRequestDTO` | `StepUpResponseDTO` | sensitive ops 직전 |

주의: refresh token 은 `HttpOnly` cookie (`SameSite=Strict`, `path=/auth`).

## 2. user/ — 프로필 & 리더보드

책임: 프로필 조회, role 변경 (USER ↔ TRAINER), public leaderboard.
컨트롤러: `UserController`, `UserLeaderboardController`.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/users/me` | — | `GetMyProfileResponseDTO` | |
| PATCH | `/users/me/role` | `UpdateUserRoleRequestDTO{role}` | `UserResponseDTO` | USER↔TRAINER |
| GET | `/users/leaderboard` | — | `GetUserLeaderboardResponseDTO` | public, no auth |

## 3. level/ — XP ledger & 출석체크

책임: XP 적립/소비 ledger, level 정책, level-up 트리거 (PENDING → COMPLETED), 출석.
컨트롤러: `LevelController`, `AttendanceController`.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/users/me/level` | — | `GetMyLevelResponseDTO` | 현재 level + 누적 XP |
| GET | `/levels/policies` | — | `GetLevelPoliciesResponseDTO` | public |
| POST | `/users/me/level-ups` | — | `LevelUpResponseDTO` | level-up + 토큰 보상 트리거 |
| GET | `/users/me/level-up-histories` | `?page=&size=` | `GetMyLevelUpHistoriesResponseDTO` | |
| GET | `/users/me/xp-ledger` | `?page=&size=` | `GetMyXpLedgerResponseDTO` | |
| POST | `/users/me/attendance` | — | `CheckInResponseDTO` | 출석 체크 |
| GET | `/users/me/attendance/status` | — | `GetAttendanceStatusResult` | |
| GET | `/users/me/attendance/weekly` | — | `GetWeeklyAttendanceResult` | |

## 4. verification/ — 운동 인증

책임: 운동 사진 / 기록 사진 인증, EXIF 검사 + AI 분석 + 승인 flow.
컨트롤러: `WorkoutVerificationController`.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/verification/photo` | `SubmitWorkoutVerificationRequestDTO{tmpObjectKey}` | `SubmitWorkoutVerificationResponseDTO` | kind=WORKOUT_PHOTO |
| POST | `/verification/record` | 동일 | 동일 | kind=WORKOUT_RECORD (3rd-party 스크린샷) |
| GET | `/verification/{verificationId}` | path | `VerificationDetailResponseDTO` | |
| GET | `/verification/today-completion` | — | `TodayWorkoutCompletionResponseDTO` | |

## 5. post/ — 게시판 (질문/자유)

책임: 질문/자유 board CRUD, 상태 머신 (OPEN → RESOLVED), 답변 채택, 좋아요, V2 cursor 페이징.
컨트롤러: `PostController`, `PostV2Controller`, `PostLikeController`, `PostLikeV2Controller`,
`MyPostV2Controller`, `PostCommentActivityV2Controller`.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/posts/question` | `CreateQuestionPostRequest` | `CreateQuestionPostResponse` | Web3 escrow payload nullable, 토큰 stake 필수 |
| POST | `/posts/free` | `CreateFreePostRequest` | `CreatePostResult` | Web3 없음 |
| GET | `/posts/{postId}` | path | `PostDetailResponse` | anonymous 허용 |
| GET | `/posts` | `?type=&tag=&search=&page=&size=` | `GetPostsResponse{posts, hasNext}` | |
| PATCH | `/posts/{postId}` | `UpdatePostRequest` | `PostMutationResponse` | |
| DELETE | `/posts/{postId}` | path | `PostMutationResponse` | 질문 삭제 시 refund payload |
| POST | `/posts/{postId}/web3/recover-create` | `RecoverQuestionCreateRequest?` | `PostMutationResponse` | escrow 재발급 |
| POST | `/posts/{postId}/answers/{answerId}/accept` | path | `AcceptAnswerResponse` | 정산 escrow |
| POST/DELETE | `/posts/{postId}/likes` | path | `PostLikeResponse` | 게시글 좋아요 |
| POST/DELETE | `/questions/{postId}/answers/{answerId}/likes` | path | `PostLikeResponse` | 답변 좋아요 |
| GET | `/v2/posts` | `?type=&tag=&search=&cursor=&size=` | `GetPostsV2Response` | cursor 페이징 |
| GET | `/v2/users/me/posts` | 동일 | `GetMyPostsV2Response` | 내가 쓴 글 |
| GET | `/v2/users/me/liked-posts` | cursor params | V2 응답 | |
| GET | `/v2/users/me/commented-posts` | cursor params | V2 응답 | |

## 6. answer/ — 질문 답변

책임: 질문 게시글 답변 CRUD, 채택 시 stake 정산, escrow 복구.
컨트롤러: `AnswerController` (`/questions/{postId}/answers`).

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/questions/{postId}/answers` | `CreateAnswerRequest` | `CreateAnswerResponse` | answer-create escrow |
| GET | `/questions/{postId}/answers` | path | `List<AnswerResponse>` | owner 행에 Web3 summary |
| PUT | `/questions/{postId}/answers/{answerId}` | `UpdateAnswerRequest` | `AnswerMutationResponse` | local-only 시 web3=null |
| DELETE | `/questions/{postId}/answers/{answerId}` | path | `AnswerMutationResponse` | delete escrow |
| POST | `…/web3/recover-create` | path | `AnswerMutationResponse` | answer-submit 재발급 |

## 7. comment/ — 댓글

책임: post / answer 댓글 CRUD + 대댓글, V2 cursor 페이징. answer 댓글의 update/delete 는 V2 에서만.
컨트롤러: `CommentController`, `CommentV2Controller`.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/posts/{postId}/comments` | `CreateCommentRequest` | `CommentMutationResponse` | V1 |
| POST | `/answers/{answerId}/comments` | `CreateCommentRequest` | `CommentMutationResponse` | V1 |
| PUT | `/comments/{commentId}` | `UpdateCommentRequest` | `CommentMutationResponse` | V1 (post 댓글) |
| DELETE | `/comments/{commentId}` | path | `String` | V1 |
| GET | `/posts/{postId}/comments` | `?page=&size=` | `Page<CommentResponse>` | V1 root |
| GET | `/answers/{answerId}/comments` | `?page=&size=` | `Page<CommentResponse>` | V1 root |
| GET | `/comments/{commentId}/replies` | `?page=&size=` | `Page<CommentResponse>` | V1 |
| GET | `/v2/posts/{postId}/comments` | `?cursor=&size=` | `GetCommentsResponse` | V2 cursor |
| POST | `/v2/posts/{postId}/comments` | `CreateCommentRequest` | 201 `CommentMutationResponse` | V2 |
| GET | `/v2/answers/{answerId}/comments` | `?cursor=&size=` | `GetCommentsResponse` | V2 |
| POST | `/v2/answers/{answerId}/comments` | `CreateCommentRequest` | 201 `CommentMutationResponse` | V2 |
| PUT | `/v2/answers/{answerId}/comments/{commentId}` | `UpdateCommentRequest` | `CommentMutationResponse` | V2 전용 |
| DELETE | `/v2/answers/{answerId}/comments/{commentId}` | path | `CommentDeleteResponse` | V2 전용 |
| GET | `/v2/comments/{commentId}/replies` | `?cursor=&size=` | `GetCommentsResponse` | V2 |

## 8. image/ — S3 이미지 라이프사이클

책임: presigned URL 발급, PENDING→READY 상태 머신, Lambda webhook 콜백, orphan cleanup (event).
컨트롤러: `ImageController`, `ImageInternalController`.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| POST | `/images/presigned-urls` | `IssuePresignedUrlRequestDTO{referenceType, filenames}` | `IssuePresignedUrlResponseDTO[{presignedUrl, tmpObjectKey}]` | |
| GET | `/images` | `?ids=&referenceType=&referenceId=` | `GetImagesByIdsResponseDTO` | 없는 id silently skip |
| GET | `/images/status` | `@ModelAttribute` | `GetImagesStatusResponseDTO` | |
| POST | `/internal/images/lambda-callback` | `LambdaCallbackRequestDTO` + `X-Lambda-Webhook-Secret` 헤더 | `Void` | 외부 호출 금지, constant-time 비교 |

## 9. location/ — 위치 인증 (체크인)

책임: 즐겨찾기 위치 등록/조회/삭제, 현재 좌표 기반 인증. 컨트롤러: `LocationController`.

| Method | Path | Request | Response | 비고 |
|--------|------|---------|----------|------|
| GET | `/users/me/locations` | — | `GetMyLocationsResponseDTO` | |
| POST | `/users/me/locations/register` | `RegisterLocationRequestDTO` | `RegisterLocationResponseDTO` | |
| POST | `/locations/verify` | `VerifyLocationRequestDTO{locationId, currentLatitude, currentLongitude}` | `VerifyLocationResponseDTO` | 좌표 기반 인증 |
| DELETE | `/users/me/locations/{locationId}` | path | `DeleteLocationResponseDTO` | |

## 10. tag/ — 태그 정규화

책임: post / class 의 tag 정규화 + 인기 태그 집계. HTTP API 없음 (다른 모듈에 in-port 제공).

## 11–13. sub-module 보유 모듈

| 모듈 | 참조 | 요약 |
|------|------|------|
| `marketplace/` | `marketplace/AGENTS.md` | classes / reservation / sanction / store. TRAINER 만 클래스 운영. 토큰 결제 + escrow |
| `admin/` | `admin/AGENTS.md` | 본체 + board / dashboard / user / common. 관리자 계정·차단·모더레이션·통계 |
| `web3/` | `web3/AGENTS.md` | wallet / challenge / signature / transaction / transfer / execution / qna / eip7702 / treasury / shared / admin |

## Cross-cutting 규칙

- 인증 추출은 `@AuthenticationPrincipal Long userId` 사용. null 이면 `UserNotAuthenticatedException`.
- 컨트롤러 → `request.toCommand()` → UseCase → `Response.from(result)` 3-step 패턴 엄수.
- 컨트롤러는 Application port 만 의존 — domain / infrastructure 직접 의존 금지.
- 이벤트 흐름: `PostCreatedEvent → level/`, `AnswerDeletedEvent → image/`,
  `QuestionRewardIntentRequestedEvent → web3/transaction/`,
  `Web3TransactionSucceededEvent → web3/qna/`. 자세한 내용은 `src/AGENTS.md`.
