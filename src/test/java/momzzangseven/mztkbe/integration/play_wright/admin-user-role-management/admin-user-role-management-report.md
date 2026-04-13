# Admin User Role Management — Playwright E2E Test Report

> Feature: MOM-330 Admin User Role Management
> Spec file: `admin-user-role-management.spec.ts`
> Last run: —

## Test Cases

| ID | Description | Status | Notes |
|----|-------------|--------|-------|
| P-1 | Admin login via API and access admin dashboard | ⬜ Not run | Requires ADMIN_LOGIN_ID, ADMIN_PASSWORD |
| P-2 | Application bootstrap creates seed admin accounts on first startup | ⬜ Not run | Verified via account list |
| P-3 | Application bootstrap skips when sufficient admins already exist | ⬜ Not run | Idempotency check (2x list compare) |
| P-4 | Recovery reseed delivers credentials to AWS Secrets Manager | ⬜ Not run | Requires RECOVERY_ANCHOR + AWS SM access |
| P-5 | Recovery anchor loaded from AWS Secrets Manager is validated correctly | ⬜ Not run | Requires RECOVERY_ANCHOR |
| P-6 | Complete admin lifecycle: bootstrap → login → create → rotate → peer-reset → list | ⬜ Not run | Multi-step lifecycle test |
| P-7 | Complete recovery lifecycle: lock-out → reseed → re-establish | ⬜ Not run | Requires RECOVERY_ANCHOR |
| P-8 | Recovery rate limiting under sustained attack simulation | ⬜ Not run | 61s wait included; use test.slow() |
| P-9 | Rate limiting uses X-Forwarded-For for IP identification behind proxy | ⬜ Not run | IP-based bucket isolation |
| P-10 | Admin JWT token from before recovery reseed is rejected after reseed | ⬜ Not run | Token invalidation after reseed |
| P-11 | Concurrent recovery reseed requests — only one succeeds | ⬜ Not run | Concurrent request consistency |

## Prerequisites

Add the following variables to `.env` before running:

```
# Admin credentials (delivered by bootstrap via LOG or AWS Secrets Manager)
ADMIN_LOGIN_ID=<seed admin loginId>
ADMIN_PASSWORD=<seed admin password>

# Recovery anchor (configured in application.properties or AWS Secrets Manager)
RECOVERY_ANCHOR=<recovery anchor value>
```

## Running the tests

```bash
# From the play_wright directory
npx playwright test admin-user-role-management/admin-user-role-management.spec.ts

# Run a specific test by title
npx playwright test --grep "\[P-1\]"

# Run with extended timeout for rate-limit tests (P-8 takes ~61s)
npx playwright test admin-user-role-management/ --timeout 120000
```

## Important Notes

- **P-4, P-5, P-7, P-10, P-11** trigger a real recovery reseed. Each reseed soft-deletes all existing admin accounts and creates new ones. Run these tests in an isolated test environment.
- **P-8** waits 61 seconds for the Bucket4j rate-limit bucket to refill. The Playwright timeout must be extended to at least 120 seconds.
- **P-9** relies on the server accepting `X-Forwarded-For` for IP extraction. Verify `RecoveryRateLimitFilter` uses this header.
- After running any recovery test (P-4, P-5, P-7, P-10, P-11), update `ADMIN_LOGIN_ID` and `ADMIN_PASSWORD` in `.env` with the newly delivered credentials.
