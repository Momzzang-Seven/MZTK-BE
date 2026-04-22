# QnA Admin Escrow Playwright Report

## Scope

- `[P-1]` admin settlement review/settle broadcasts and mines `QNA_ADMIN_SETTLE`
- `[P-2]` admin refund review/refund broadcasts and mines `QNA_ADMIN_REFUND`

## Environment

- Run date: `2026-04-22`
- Backend: `http://127.0.0.1:8080`
- Env source:
  - `src/test/java/momzzangseven/mztkbe/integration/play_wright/.env`
  - `src/main/resources/application.yml`
  - `src/main/resources/application-dev.yml`
- Wallets used:
  - asker: `QNA_TEST_ASKER_ADDRESS` / `QNA_TEST_ASKER_PRIVATE_KEY`
  - responder: `QNA_TEST_RESPONDER_ADDRESS` / `QNA_TEST_RESPONDER_PRIVATE_KEY`
- Chain:
  - `WEB3_CHAIN_ID=11155420`
  - `WEB3_RPC_URL=https://opt-sepolia.g.alchemy.com/v2/M2Cc-3ejatKn-9ps2vsx3`
  - `WEB3_ESCROW_QNA_CONTRACT_ADDRESS` loaded from local `.env`
  - `WEB3_REWARD_TOKEN_CONTRACT_ADDRESS` loaded from local `.env`

## Test Flow

- `[P-1]` settlement case
  - create local admin account and login with `LOCAL_ADMIN`
  - sign up asker / responder
  - register funded asker wallet
  - approve reward token allowance
  - create question through real user execution flow and wait for confirmation
  - register funded responder wallet
  - create answer through real user execution flow and wait for confirmation
  - call admin settlement review endpoint
  - call admin settle endpoint
  - wait until execution intent is `PENDING_ONCHAIN` or `CONFIRMED`
  - verify mined transaction receipt

- `[P-2]` refund case
  - create local admin account and login with `LOCAL_ADMIN`
  - sign up asker
  - register funded asker wallet
  - approve reward token allowance
  - create question through real user execution flow and wait for confirmation
  - call admin refund review endpoint
  - call admin refund endpoint
  - wait until execution intent is `PENDING_ONCHAIN` or `CONFIRMED`
  - verify mined transaction receipt

## Run

```bash
npx playwright test qna-admin-escrow/qna-admin-escrow.spec.ts
```

## Result

- Status: Passed
- Summary: `2 passed (41.1s)`
- Backend health check: `UP`

## Verified Artifacts

- Settlement
  - execution intent public id: `12b12a27-7831-4e36-bdac-3ee3052096d7`
  - resource id: `87`
  - final status: `CONFIRMED`
  - tx hash: masked in report, prefix/suffix `0x8974...175f`

- Refund
  - execution intent public id: `1d5face3-baee-45c5-b2f5-de252c3f3418`
  - resource id: `88`
  - final status: `CONFIRMED`
  - tx hash: masked in report, prefix/suffix `0x795a...29d4`

## State Verification

- Local post state
  - `post:87` -> `RESOLVED`
  - `post:88` -> local question deleted after confirmed refund flow

- QnA projection state
  - `post:87` -> `ADMIN_SETTLED (4000)`
  - `post:88` -> `DELETED (5000)`

- Admin audit
  - `QNA_ADMIN_SETTLE`, `target_id=post:87`, `success=true`
  - `QNA_ADMIN_REFUND`, `target_id=post:88`, `success=true`

## Notes

- The first real refund attempt failed with HTTP `409 INTERNAL_004`, but the cause was not backend business logic.
- Root cause was local dev DB schema drift:
  - `application-dev.yml` has `spring.flyway.enabled=false`
  - the running DB still had `posts_status_check` that allowed only `OPEN`, `PENDING_ACCEPT`, `RESOLVED`
  - `PENDING_ADMIN_REFUND` was rejected by the database check constraint
- After updating the local `posts_status_check` constraint to include `PENDING_ADMIN_REFUND`, the same Playwright run passed end-to-end.
- Full contract addresses and full transaction hashes are intentionally omitted from this report to avoid gitleaks false positives in committed artifacts.

## Conclusion

- The current QnA admin Playwright scenario now validates real question creation, real answer creation, admin settle, and admin refund against the live Optimism Sepolia environment.
- In the current local dev environment, keeping schema and code aligned is required for refund verification because `ddl-auto=update` with Flyway disabled does not reliably evolve existing check constraints.
