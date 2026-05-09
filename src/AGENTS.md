# src/ — Source Root

Production code in `main/`, test code in `test/`. Both share the same package root:
`momzzangseven/mztkbe/`

→ Production code patterns & DB profiles: `main/AGENTS.md`
→ Testing conventions & E2E rules: `test/AGENTS.md`

## Module Map (`main/java/momzzangseven/mztkbe/modules/`)

| Module | Responsibility |
|--------|----------------|
| `account/` | Auth & account lifecycle — login, OAuth2, soft/hard delete, JWT refresh |
| `user/` | User profile — nickname, role (USER/TRAINER), profile image |
| `level/` | XP ledger, level-up detection, reward status (PENDING → COMPLETED) |
| `verification/` | Workout photo verification — EXIF check, AI analysis, approval flow |
| `post/` | Question & free board — status machine (OPEN → RESOLVED), answer acceptance |
| `answer/` | Answers to questions — accept logic, cascade delete |
| `comment/` | Comments on posts/answers |
| `image/` | S3 upload, orphan image cleanup |
| `marketplace/` | PT class listings — TRAINER-only, token payment |
| `location/` | Gym location check-in data |
| `tag/` | Tag normalization for posts and classes |
| `admin/` | User block/unblock, refund initiation |
| `web3/` | Blockchain layer — see sub-modules below |

## Web3 Sub-modules (`modules/web3/`)

| Sub-module | Responsibility |
|------------|----------------|
| `wallet/` | User wallet registration/unlinking (EIP-4361 flow entry) |
| `challenge/` | Sign-In with Ethereum — nonce generation, 15 min expiry, signature verify |
| `signature/` | EIP-712 typed data verification |
| `transaction/` | TX lifecycle: CREATED → SIGNED → BROADCASTED → CONFIRMED / FAILED |
| `transfer/` | Token transfer intents — question reward, verification reward |
| `execution/` | Scheduler-driven batch TX dispatch and retry |
| `qna/` | On-chain reward completion sync for QnA |
| `eip7702/` | Fee sponsoring (Account Abstraction) with daily sponsor quota |
| `treasury/` | Protocol wallet KMS key management (AWS KMS + BouncyCastle fallback) |
| `shared/` | Shared VOs (wallet address, amount), crypto utils |
| `admin/` | Admin-triggered TX retry and refund |

## Key Event Flows

```
PostCreatedEvent        → level/     (XP grant)
AnswerDeletedEvent      → image/     (orphan cleanup)
UserSoftDeletedEvent    → account/   (cascade soft-delete)
QuestionRewardIntentRequestedEvent → web3/transaction/ (TX creation)
Web3TransactionSucceededEvent      → web3/qna/         (state sync)
```

## Global (`main/java/momzzangseven/mztkbe/global/`)

`security/` JWT+OAuth2 · `error/` BusinessException+ErrorCode · `config/` Flyway/S3/QueryDSL ·
`audit/` change history · `pagination/` keyset cursor · `response/` ApiResponse wrapper ·
`secret/` AES-GCM for OAuth tokens · `time/` KST zone management

@AGENTS.local.md
