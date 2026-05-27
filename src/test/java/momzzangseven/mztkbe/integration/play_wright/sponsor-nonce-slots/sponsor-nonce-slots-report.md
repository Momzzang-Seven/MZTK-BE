# MOM-458 Sponsor Nonce Slots Playwright Report

## Scope

- `GET /admin/web3/nonce-slots`
- Base Sepolia sponsor nonce slot 조회 계약
- Admin / anonymous / USER 권한 경계

## Preconditions

- Backend server is running and reachable at `BACKEND_URL`.
- `ADMIN_LOGIN_ID` and `ADMIN_PASSWORD` are set for admin-positive coverage.
- `USER_ACCESS_TOKEN` is optional. If omitted, USER-negative coverage is skipped.
- `SPONSOR_NONCE_CHAIN_ID` defaults to `84532`.
- `SPONSOR_NONCE_ADDRESS` is required at runtime and must not be hardcoded in the spec.

## Command

```bash
cd src/test/java/momzzangseven/mztkbe/integration/play_wright
npx playwright test sponsor-nonce-slots/sponsor-nonce-slots.spec.ts
```

## Cases

- `[P-MOM458-1]` Admin can read sponsor nonce slots.
- `[P-MOM458-2]` Anonymous request cannot read sponsor nonce slots.
- `[P-MOM458-3]` USER token cannot read sponsor nonce slots.

## Validation Points

- Response envelope returns `status: SUCCESS` for admin.
- `chainId` is `84532` unless overridden.
- `fromAddress` is normalized to lowercase.
- `slots` is an array and is sorted by nonce ascending.
- Slot fields match the frontend-facing API contract.
- Anonymous request returns `401`.
- USER request returns `403`.
