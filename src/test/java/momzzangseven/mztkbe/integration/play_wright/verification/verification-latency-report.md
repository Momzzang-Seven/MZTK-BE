# Workout Verification Latency Report

- Generated at: 2026-04-20T17:30:10.846Z
- Backend URL: http://127.0.0.1:8080
- Record count: 4

## Summary

| Endpoint | Count | Min ms | Avg ms | Max ms |
|---|---:|---:|---:|---:|
| photo | 3 | 18 | 19 | 20 |
| record | 1 | 25 | 25 | 25 |

## Measurements

| Scenario | Endpoint | Ext | HTTP | API status | API code | Verification status | Elapsed ms | Recorded at |
|---|---|---|---:|---|---|---|---:|---|
| TC-V-A-01 | photo | jpg | 400 | FAIL | VERIFICATION_001 | - | 20 | 2026-04-20T17:30:09.559Z |
| TC-V-A-02 | photo | png | 400 | FAIL | VERIFICATION_002 | - | 20 | 2026-04-20T17:30:09.826Z |
| TC-V-A-03 | record | gif | 400 | FAIL | VERIFICATION_002 | - | 25 | 2026-04-20T17:30:10.081Z |
| TC-V-A-04 | photo | jpg | 404 | FAIL | VERIFICATION_003 | - | 18 | 2026-04-20T17:30:10.327Z |
