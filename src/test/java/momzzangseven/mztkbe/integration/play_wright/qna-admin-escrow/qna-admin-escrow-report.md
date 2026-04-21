# QnA Admin Escrow Playwright Report

## Scope

- `[P-1]` admin settlement review/settle creates and broadcasts `QNA_ADMIN_SETTLE`
- `[P-2]` admin refund review/refund creates and broadcasts `QNA_ADMIN_REFUND`

## Preconditions

- Backend server is running
- `.env` contains `BACKEND_URL`, DB connection, `WEB3_RPC_URL`, `WEB3_ESCROW_QNA_CONTRACT_ADDRESS`
- Backend is configured with `web3.execution.internal.enabled=true`
- Internal issuer scheduler is active and points to the same RPC / signer as the running backend

## Run

```bash
npx playwright test qna-admin-escrow/qna-admin-escrow.spec.ts
```

## Result

- Status: Blocked on 2026-04-21
- Notes:
  - `./gradlew --no-daemon bootRun` attempted with repo `.env` + Playwright `.env`
  - startup failed in `QnaAdminExecutionConfigurationValidator`
  - failure reason: current internal signer `0xd799CD2B5258eDC2157beC7E2CD069f31f2678c2` is not a registered relayer for `WEB3_ESCROW_QNA_CONTRACT_ADDRESS`
  - direct on-chain check of `isRelayer(address)` returned `false`
  - `npx playwright test qna-admin-escrow/qna-admin-escrow.spec.ts` then failed at `globalSetup` because backend never became healthy
