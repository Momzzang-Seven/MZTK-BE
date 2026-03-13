# Workout Verification Latency Report

- Generated at: 2026-03-13T15:51:36.422Z
- Backend URL: http://127.0.0.1:8080
- Record count: 37

## Summary

| Endpoint | Count | Min ms | Avg ms | Max ms |
|---|---:|---:|---:|---:|
| photo | 25 | 5 | 61 | 1204 |
| record | 12 | 5 | 20 | 78 |

## Measurements

| Scenario | Endpoint | Ext | HTTP | API status | API code | Verification status | Elapsed ms | Recorded at |
|---|---|---|---:|---|---|---|---:|---|
| TC-V-A-01 | photo | jpg | 400 | FAIL | VERIFICATION_001 | - | 9 | 2026-03-13T15:51:25.204Z |
| TC-V-A-02 | photo | png | 400 | FAIL | VERIFICATION_002 | - | 8 | 2026-03-13T15:51:25.428Z |
| TC-V-A-03 | record | gif | 400 | FAIL | VERIFICATION_002 | - | 8 | 2026-03-13T15:51:25.648Z |
| TC-V-A-04 | photo | jpg | 404 | FAIL | VERIFICATION_003 | - | 13 | 2026-03-13T15:51:25.873Z |
| TC-V-A-05 | photo | jpg | 403 | FAIL | VERIFICATION_004 | - | 8 | 2026-03-13T15:51:26.323Z |
| TC-V-A-06 | photo | jpg | 409 | FAIL | VERIFICATION_005 | - | 10 | 2026-03-13T15:51:26.575Z |
| TC-V-A-07 | photo | jpg | 409 | FAIL | VERIFICATION_006 | - | 7 | 2026-03-13T15:51:26.804Z |
| TC-V-A-P-jpg | photo | jpg | 200 | SUCCESS | - | REJECTED | 9 | 2026-03-13T15:51:27.241Z |
| TC-V-A-P-jpeg | photo | jpeg | 200 | SUCCESS | - | REJECTED | 7 | 2026-03-13T15:51:27.461Z |
| TC-V-A-P-heif | photo | heif | 200 | SUCCESS | - | REJECTED | 14 | 2026-03-13T15:51:27.726Z |
| TC-V-A-P-heic | photo | heic | 200 | SUCCESS | - | REJECTED | 6 | 2026-03-13T15:51:27.950Z |
| TC-V-A-R-jpg | record | jpg | 200 | SUCCESS | - | REJECTED | 6 | 2026-03-13T15:51:28.174Z |
| TC-V-A-R-jpeg | record | jpeg | 200 | SUCCESS | - | REJECTED | 5 | 2026-03-13T15:51:28.392Z |
| TC-V-A-R-png | record | png | 200 | SUCCESS | - | REJECTED | 8 | 2026-03-13T15:51:28.614Z |
| TC-V-A-R-heif | record | heif | 200 | SUCCESS | - | REJECTED | 10 | 2026-03-13T15:51:28.845Z |
| TC-V-A-R-heic | record | heic | 200 | SUCCESS | - | REJECTED | 8 | 2026-03-13T15:51:29.068Z |
| TC-V-B-01 | photo | jpg | 200 | SUCCESS | - | PENDING | 5 | 2026-03-13T15:51:29.283Z |
| TC-V-B-02 | photo | jpg | 200 | SUCCESS | - | ANALYZING | 10 | 2026-03-13T15:51:29.504Z |
| TC-V-B-03 | photo | jpg | 200 | SUCCESS | - | REJECTED | 5 | 2026-03-13T15:51:29.714Z |
| TC-V-B-04 | photo | jpg | 200 | SUCCESS | - | VERIFIED | 9 | 2026-03-13T15:51:29.937Z |
| TC-V-B-05 | photo | jpg | 200 | SUCCESS | - | FAILED | 8 | 2026-03-13T15:51:30.156Z |
| TC-V-C-P-01 | photo | jpg | 200 | SUCCESS | - | REJECTED | 5 | 2026-03-13T15:51:30.853Z |
| TC-V-C-P-02 | photo | jpg | 200 | SUCCESS | - | REJECTED | 7 | 2026-03-13T15:51:31.086Z |
| TC-V-C-P-03 | photo | jpg | 200 | SUCCESS | - | REJECTED | 10 | 2026-03-13T15:51:31.320Z |
| TC-V-C-P-04 | photo | jpg | 200 | SUCCESS | - | REJECTED | 12 | 2026-03-13T15:51:31.554Z |
| TC-V-C-P-05 | photo | jpg | 200 | SUCCESS | - | REJECTED | 10 | 2026-03-13T15:51:31.792Z |
| TC-V-C-P-06 | photo | jpg | 200 | SUCCESS | - | REJECTED | 9 | 2026-03-13T15:51:32.041Z |
| TC-V-C-P-07 | photo | jpg | 200 | SUCCESS | - | REJECTED | 6 | 2026-03-13T15:51:32.309Z |
| TC-V-C-R-01 | record | png | 200 | SUCCESS | - | REJECTED | 14 | 2026-03-13T15:51:32.609Z |
| TC-V-C-R-02 | record | png | 200 | SUCCESS | - | REJECTED | 10 | 2026-03-13T15:51:32.855Z |
| TC-V-C-R-03 | record | png | 200 | SUCCESS | - | REJECTED | 8 | 2026-03-13T15:51:33.116Z |
| TC-V-C-R-04 | record | png | 200 | SUCCESS | - | REJECTED | 9 | 2026-03-13T15:51:33.357Z |
| TC-V-D-01 | photo | jpg | 200 | SUCCESS | - | REJECTED | 1204 | 2026-03-13T15:51:35.036Z |
| TC-V-D-02 | record | jpg | 200 | SUCCESS | - | FAILED | 75 | 2026-03-13T15:51:35.457Z |
| TC-V-D-03#1 | photo | jpg | 200 | SUCCESS | - | ANALYZING | 47 | 2026-03-13T15:51:35.885Z |
| TC-V-D-03#2 | photo | jpg | 200 | SUCCESS | - | REJECTED | 87 | 2026-03-13T15:51:35.926Z |
| TC-V-D-04 | record | jpg | 200 | SUCCESS | - | FAILED | 78 | 2026-03-13T15:51:36.385Z |
