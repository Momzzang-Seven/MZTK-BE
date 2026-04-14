# 레벨업 보상 Playwright E2E 테스트

## 개요

`level-reward.spec.ts` 는 **레벨업 및 온체인 토큰 보상 플로우**를 Playwright + HTTP API 레이어에서 검증하는 E2E 테스트입니다.

레벨업은 충분한 XP와 ACTIVE 상태의 지갑을 보유한 사용자에게 MZTK 토큰 보상을 지급합니다.  
보상 트랜잭션은 백그라운드 워커(`TransactionIssuerWorker`, `TransactionReceiptWorker`)가 비동기로 처리하므로,  
Suite D는 `rewardTxStatus`가 `SUCCEEDED`로 전이될 때까지 폴링합니다.

**테스트 범위:**
- Suite A — 사전 조건 오류 (RPC 불필요, 블록체인 독립)
- Suite B — 정상 레벨업 응답 및 이력/레벨 반영 확인
- Suite C — 중복 레벨업 멱등 처리
- Suite D — 온체인 토큰 지급 확인 (워커 처리 완료까지 폴링)

---

## 외부 의존성

| 테스트 대상 | 외부 의존성 | 비고 |
|---|:---:|---|
| Suite A, B, C — API 기능 검증 | ✗ | RPC 불필요, 블록체인 독립 |
| Suite D — 온체인 지급 확인 | ✅ | Optimism Sepolia RPC 연결 필요 |
| EIP-712 지갑 서명 (전 스위트) | ✗ | `ethers.js` 로컬 서명, 온체인 호출 없음 |

> Suite D는 실제 Optimism Sepolia 네트워크와 연동하여 `txHash`를 검증합니다.  
> `TransactionIssuerWorker` 및 `TransactionReceiptWorker` 스케줄러가 활성화되어 있어야 합니다.

---

## 전제 조건 (서버 설정)

| 설정 키 | 필요 값 | 비고 |
|---|---|---|
| `web3.reward-token.enabled` | `true` | 토큰 보상 지급 활성화 |
| `web3.eip7702.enabled` | `true` | EIP-7702 배치 트랜잭션 활성화 |
| `web3_treasury_keys` DB | 레코드 존재 | Admin API(`/admin/web3/treasury-keys/provision`)로 사전 등록 필요 |
| 워커 스케줄러 | 활성화 | Suite D에서 SUCCEEDED 전이에 필요 |
| Optimism Sepolia RPC | 연결 가능 | Suite D에서 온체인 확인에 필요 |

---

## 테스트 플로우

### Suite A — 사전 조건 오류

```
미인증 요청 → 401 차단

회원가입 + 로그인 + 레벨 초기화
    ↓
(XP 충족, 지갑 미연결) 레벨업 요청 → 400 WALLET_003

회원가입 + 로그인 + 레벨 초기화 + 지갑 등록
    ↓
(XP 미충족) 레벨업 요청 → 409 LEVEL_001
```

### Suite B — 정상 레벨업 응답 검증

```
회원가입 + 로그인 + 레벨 초기화
    ↓
지갑 등록 (EIP-712 서명) + XP DB 직접 설정
    ↓
POST /users/me/level-ups → 200 OK
    → rewardTxStatus=CREATED, rewardTxPhase=PENDING 확인
    → GET /users/me/level-up-histories → 이력 항목 존재 확인
    → GET /users/me/level → currentLevel=2 확인
```

### Suite C — 중복 레벨업 멱등 처리

```
레벨업 성공 (1회차) → 200 OK
    ↓
추가 XP 없이 재요청 → 409 LEVEL_001

──────────────────────────────────────────────────
XP 설정(Lv1→2) → 레벨업 성공 → levelUpHistoryId=id1
    ↓
XP 설정(Lv2→3) → 레벨업 성공 → levelUpHistoryId=id2
    ↓
이력 조회 → id1(Lv1→2), id2(Lv2→3) 각각 독립 이력 확인
```

### Suite D — 온체인 지급 확인 (폴링)

```
회원가입 + 로그인 + 레벨 초기화 + 지갑 등록 + XP 설정
    ↓
레벨업 → rewardTxStatus=CREATED (워커 처리 전)
    ↓
TransactionIssuerWorker: CREATED → SIGNED → PENDING
TransactionReceiptWorker: PENDING → SUCCEEDED (온체인 receipt 확인)
    ↓
GET /users/me/level-up-histories 폴링 (15초 간격, 최대 5분)
    ↓
rewardTxStatus=SUCCEEDED, rewardTxHash(0x...) 수신 확인
explorerUrl = https://sepolia-optimism.etherscan.io/tx/{txHash}
```

---

## 사전 조건

### 1. 백엔드 서버 실행

```bash
./gradlew bootRun
```

### 2. Playwright `.env` 설정

`play_wright/.env.example` 을 복사하여 `.env` 를 생성합니다.

```dotenv
# 백엔드 서버 URL
BACKEND_URL=http://127.0.0.1:8080

# DB 접속 정보 (XP를 user_progress 테이블에 직접 설정할 때 사용)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=momzzangcoin
DB_USER=mztk
DB_PASSWORD=mztk_pw

# EIP-712 도메인 설정 (백엔드 application.yml의 web3.eip712.* 와 일치해야 함)
WEB3_EIP712_DOMAIN_NAME=MomzzangSeven
WEB3_EIP712_DOMAIN_VERSION=1
WEB3_EIP712_CHAIN_ID=11155420
WEB3_EIP712_VERIFYING_CONTRACT=0x815B53fD2D56044BaC39c1f7a9C7d3E67322f0F5
```

> Suite A, B, C는 `BACKEND_URL` + DB 설정만 있으면 실행됩니다.  
> Suite D는 추가로 Optimism Sepolia RPC에 연결되는 서버 환경이 필요합니다.  
> 지갑은 `ethers.Wallet.createRandom()` 으로 테스트마다 고유하게 생성합니다.

### 3. XP 설정 전략

레벨업에 필요한 XP를 API로 직접 획득하면 시간이 오래 걸리므로,  
`node-postgres(pg)` 로 `user_progress` 테이블을 직접 수정하여 XP를 설정합니다.

```sql
UPDATE user_progress
   SET available_xp = $1,
       lifetime_xp  = GREATEST(lifetime_xp, $1),
       updated_at   = NOW()
 WHERE user_id = $2
```

### 4. Playwright 패키지 설치

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright
npm install
npx playwright install chromium
```

---

## 실행 방법

```bash
# play_wright/ 디렉터리에서 실행
npx playwright test level-reward/level-reward.spec.ts

# Suite A~C만 실행 (블록체인 독립, 빠름)
npx playwright test level-reward/level-reward.spec.ts --grep "Suite [A-C]"

# Suite D 온체인 테스트만 실행 (최대 6분 소요)
npx playwright test level-reward/level-reward.spec.ts --grep "Suite D"

# UI 모드로 실행 (디버깅 시 권장)
npx playwright test level-reward/level-reward.spec.ts --ui

# 결과 리포트 확인
npx playwright show-report
```

---

## 테스트 케이스 목록

### Suite A — 사전 조건 오류

| TC ID | 제목 | 예상 결과 |
|---|---|---|
| TC-LR-A-01 | 미인증 레벨업 요청 → 401 Unauthorized | `401 Unauthorized` |
| TC-LR-A-02 | 지갑 미연결 상태에서 레벨업 → 400 WALLET_NOT_CONNECTED | `400 Bad Request` / `code: WALLET_003` |
| TC-LR-A-03 | XP 부족 상태에서 레벨업 → 409 NOT_ENOUGH_XP | `409 Conflict` / `code: LEVEL_001` |

### Suite B — 정상 레벨업 응답 검증

| TC ID | 제목 | 예상 결과 |
|---|---|---|
| TC-LR-B-01 | 지갑 등록 + XP 충족 → 레벨업 200 OK, rewardTxStatus 초기 상태 반환 | `200 OK` / `rewardTxStatus ∈ {CREATED, SIGNED, PENDING, SUCCEEDED, UNCONFIRMED}` |
| TC-LR-B-02 | 레벨업 후 GET /users/me/level-up-histories 에서 이력 확인 | 이력 항목 존재, `fromLevel=1`, `toLevel=2` |
| TC-LR-B-03 | 레벨업 후 GET /users/me/level 에서 레벨 2로 반영 확인 | `currentLevel=2` |

### Suite C — 중복 레벨업 멱등 처리

| TC ID | 제목 | 예상 결과 |
|---|---|---|
| TC-LR-C-01 | 레벨업 성공 후 재요청 → 409 (XP 소진) | `409 Conflict` / `code: LEVEL_001` |
| TC-LR-C-02 | 충분한 XP로 두 번 연속 레벨업 (Lv1→2→3) 각각 독립 이력 생성 | 두 개의 독립 `levelUpHistoryId`, `fromLevel` 각각 1, 2 |

### Suite D — 온체인 토큰 지급 확인

| TC ID | 제목 | 예상 결과 |
|---|---|---|
| TC-LR-D-01 | 레벨업 보상 → 워커 처리 완료 후 rewardTxStatus=SUCCEEDED, txHash 수신 | `SUCCEEDED`, `txHash` 형식 `0x[0-9a-f]{64}`, `explorerUrl` 포함 |
| TC-LR-D-02 | 레벨업 보상 → 워커 처리 중 상태 전이 스냅샷 확인 | 초기 `CREATED`, 최종 `SUCCEEDED` / `UNCONFIRMED` / `FAILED_ONCHAIN` |

---

## 주요 API 엔드포인트

### `POST /auth/signup` + `POST /auth/login` — 회원가입·로그인

테스트마다 `playwright-lr-{suffix}-{timestamp}@test.com` 형태의 고유 계정을 생성하여 테스트 간 격리를 보장합니다.

---

### `GET /users/me/level` — 레벨 조회

**Request Header:** `Authorization: Bearer {accessToken}`

`user_progress` row가 없어도 서버는 초기 레벨 스냅샷을 read-only로 반환합니다.

**Response:** `200 OK`
```json
{
  "data": {
    "level": 1,
    "availableXp": 0
  }
}
```

---

### `GET /levels/policies` — 레벨 정책 조회

**Request Header:** `Authorization: Bearer {accessToken}`

**Response:** `200 OK`
```json
{
  "data": {
    "levelPolicies": [
      {
        "currentLevel": 1,
        "toLevel": 2,
        "requiredXp": 100,
        "rewardMztk": 20
      }
    ]
  }
}
```

---

### `POST /web3/challenges` → `POST /web3/wallets` — 지갑 등록

테스트에서 `ethers.Wallet.createRandom()` 으로 매회 고유한 지갑을 생성하고  
EIP-712 서명(`AuthRequest { content, nonce }`)을 생성하여 등록합니다.

---

### `POST /users/me/level-ups` — 레벨업

**Request Header:** `Authorization: Bearer {accessToken}`

**Response:** `200 OK`
```json
{
  "data": {
    "levelUpHistoryId": 32,
    "fromLevel": 1,
    "toLevel": 2,
    "spentXp": 100,
    "rewardMztk": 20,
    "rewardStatus": "PENDING",
    "rewardTxStatus": "CREATED",
    "rewardTxPhase": "PENDING",
    "rewardTxHash": null,
    "rewardExplorerUrl": null
  }
}
```

**사전 조건 오류:**
| 조건 | HTTP 상태 | 에러 코드 |
|---|:---:|---|
| 미인증 요청 | `401` | — |
| ACTIVE 지갑 없음 | `400` | `WALLET_003` |
| XP 부족 | `409` | `LEVEL_001` |

---

### `GET /users/me/level-up-histories` — 레벨업 이력 조회

**Query Params:** `page=0&size=10`

**Response:** `200 OK`
```json
{
  "data": {
    "histories": [
      {
        "levelUpHistoryId": 32,
        "fromLevel": 1,
        "toLevel": 2,
        "rewardTxStatus": "SUCCEEDED",
        "rewardTxPhase": "SUCCESS",
        "rewardTxHash": "0xcc9b...",
        "rewardExplorerUrl": "https://sepolia-optimism.etherscan.io/tx/0xcc9b..."
      }
    ]
  }
}
```

---

## rewardTxStatus 상태 전이

```
CREATED  →  SIGNED  →  PENDING  →  SUCCEEDED
                                 ↘  FAILED_ONCHAIN
                    ↘  UNCONFIRMED  (receipt 미도착)
```

| 상태 | 설명 | rewardTxPhase |
|---|---|:---:|
| `CREATED` | 트랜잭션 레코드 생성, 워커 처리 전 | `PENDING` |
| `SIGNED` | `TransactionIssuerWorker`가 서명 완료 | `PENDING` |
| `PENDING` | 온체인 broadcast 완료, receipt 대기 | `PENDING` |
| `SUCCEEDED` | receipt 수신 및 성공 확인 | `SUCCESS` |
| `UNCONFIRMED` | 15분 내 receipt 미도착 (운영 확인 필요) | `PENDING` |
| `FAILED_ONCHAIN` | `receipt.status == 0` (온체인 실패) | `FAILED` |

---

## 폴링 설정 (Suite D)

| 항목 | 값 |
|---|---|
| 폴링 간격 | 15초 (`ONCHAIN_POLL_INTERVAL_MS`) |
| 최대 대기 시간 | 5분 (`ONCHAIN_TIMEOUT_MS`) |
| 테스트 타임아웃 | 6분 (`test.setTimeout(360_000)`) |
| 목표 상태 (D-01) | `SUCCEEDED` |
| 목표 상태 (D-02) | `SUCCEEDED` / `UNCONFIRMED` / `FAILED_ONCHAIN` |

---

## 마지막 실행 결과

| 항목 | 값 |
|---|---|
| 실행일 | 2026-03-07 |
| 플랫폼 | chromium |
| Playwright 버전 | 1.58.2 |
| 전체 테스트 수 | 10 |
| 통과 | **10** |
| 실패 | 0 |
| 총 실행 시간 | 48,158 ms (약 48.2초) |

### 케이스별 결과

| TC ID | 결과 | 실행 시간 | 출력 |
|---|:---:|---:|---|
| TC-LR-A-01 | ✅ passed | 13 ms | `미인증 레벨업 차단: status=401` |
| TC-LR-A-02 | ✅ passed | 289 ms | `지갑 미연결 레벨업 차단: status=400, code=WALLET_003` |
| TC-LR-A-03 | ✅ passed | 252 ms | `XP 부족 레벨업 차단: status=409, code=LEVEL_001` |
| TC-LR-B-01 | ✅ passed | 242 ms | `레벨업 성공: levelUpHistoryId=32, rewardTxStatus=CREATED, rewardTxPhase=PENDING` |
| TC-LR-B-02 | ✅ passed | 210 ms | `이력 확인 완료: levelUpHistoryId=33, rewardTxStatus=CREATED` |
| TC-LR-B-03 | ✅ passed | 211 ms | `레벨 확인: currentLevel=2` |
| TC-LR-C-01 | ✅ passed | 191 ms | `중복 레벨업 차단: status=409, code=LEVEL_001` |
| TC-LR-C-02 | ✅ passed | 210 ms | `연속 레벨업: id1=36(Lv1→2), id2=37(Lv2→3)` |
| TC-LR-D-01 | ✅ passed | 30,246 ms | 폴링 3회 → `rewardTxStatus=SUCCEEDED` |
| TC-LR-D-02 | ✅ passed | 15,266 ms | 폴링 2회 → `rewardTxStatus=SUCCEEDED` |

### Suite D 온체인 확인 상세

**TC-LR-D-01** (`levelUpHistoryId=38`, `rewardMztk=20`)

| 폴링 | rewardTxStatus | rewardTxHash |
|:---:|---|---|
| #1 | `CREATED` | — |
| #2 | `CREATED` | — |
| #3 | `SUCCEEDED` | `0xcc9b9cb47b8b3bc8a3b42a54225b30b2835477d3e746be42083235835a7077d8` |

- Explorer: [sepolia-optimism.etherscan.io](https://sepolia-optimism.etherscan.io/tx/0xcc9b9cb47b8b3bc8a3b42a54225b30b2835477d3e746be42083235835a7077d8)

**TC-LR-D-02** (`levelUpHistoryId=39`)

| 폴링 | rewardTxStatus | rewardTxHash |
|:---:|---|---|
| #1 | `CREATED` | — |
| #2 | `SUCCEEDED` | `0xd4d5852988f1664013b35f55f76ecedf1bedd716811b580bcc6e07df4fcc9b1b` |
