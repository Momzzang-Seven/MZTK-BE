# Prod 배포 프로세스

## 1. dev → main PR 전 사전 체크리스트

### 1-1. 로컬 검증 (필수)

```bash
# 코드 포맷 / 정적 분석 / 단위 테스트 한 번에 확인
./gradlew clean spotlessCheck checkstyleMain test --no-daemon
```

실패 시 머지 금지. CI에서 동일한 검사를 수행하므로 사전에 통과해야 한다.

### 1-2. DB 마이그레이션 파일 확인

| 확인 항목 | 이유 |
|---|---|
| `V{n}__*.sql` 파일이 추가되었는가 | prod은 Flyway만으로 스키마를 관리한다 (`ddl-auto: none`) |
| 기존 마이그레이션 파일을 수정하지 않았는가 | Flyway는 체크섬을 검증하므로 기존 파일 수정 시 기동 실패 |
| `NOT NULL` 컬럼 추가 시 3단계로 작성하였는가 | nullable 추가 → 기존 row 데이터 채움 → NOT NULL 제약 추가 순서 |

### 1-3. 민감 정보 확인

- `web3_treasury_keys.sql` 등 시드 파일이 커밋에 포함되지 않았는가 (`.gitignore` 등록됨)
- 새로운 시크릿이 코드에 하드코딩되지 않았는가
  - Gitleaks가 PR에서 자동 스캔하지만 사전 확인이 우선이다

### 1-4. GitHub Secrets 등록 확인

새로운 환경 변수를 추가했다면 `deploy-prod.yml`에 `docker run -e` 항목과 GitHub Secrets에 모두 등록되어 있는지 확인한다.

---

## 2. CI 파이프라인 (자동, PR 생성/갱신 시)

| 워크플로우 | 내용 | 실패 시 |
|---|---|---|
| `security-gitleaks.yml` | 전체 히스토리 기준 민감 정보 스캔 | 머지 불가 |
| `semgrep.yml` | SAST 정적 분석 | 머지 불가 |
| `pr-main-guard.yml` | `dev` → `main` PR만 허용 | 머지 불가 |
| `ci.yml` | Spotless → Checkstyle → `./gradlew test` | 머지 불가 |

> **모든 CI 워크플로우가 초록불이어야 머지 가능하다.**

---

## 3. 배포 파이프라인 (자동, main 머지 시)

`deploy-prod.yml`이 순서대로 실행된다.

```
테스트 재실행
   → bootJar 빌드
   → Docker 이미지 빌드 & Docker Hub 푸시 (태그: latest, {git-sha})
   → EC2 SSH 접속
   → 기존 컨테이너 중지 & 제거
   → 새 컨테이너 기동 (SPRING_PROFILES_ACTIVE=prod)
   → Flyway 마이그레이션 자동 실행 (서버 기동 시)
   → 즉시 헬스체크: GET /actuator/health (20초 대기 후 최대 60초 재시도)
```

배포 성공 후 10분 뒤 `health-check-delayed.yml`이 자동 실행되어 컨테이너 상태, HTTP 200, DB 연결, 에러 로그를 종합 점검한다.

---

## 4. 신규 prod DB 최초 배포 시 추가 작업 (수동)

Flyway 마이그레이션만으로 채워지지 않는 데이터는 배포 완료 후 수동으로 등록해야 한다.

### 4-1. 트레저리 지갑 등록

```http
POST /admin/web3/treasury-keys/provision
Authorization: ROLE_ADMIN 계정 JWT
Content-Type: application/json
```

이 API를 호출해야 `web3_treasury_keys`에 운영 지갑이 등록된다.  
등록 전까지 레벨업 보상 트랜잭션은 `CREATED` 상태에서 처리되지 않는다.

### 4-2. 등록 확인

```sql
SELECT id, wallet_alias, treasury_address FROM web3_treasury_keys;
-- id=1, wallet_alias='reward-treasury' 가 있어야 함
```

---

## 5. 배포 실패 / 롤백

### 5-1. 즉시 헬스체크 실패 시

EC2에 접속하여 로그를 확인한다.

```bash
docker logs --tail 100 mztk-prod
```

### 5-2. 롤백 (이전 이미지로 복구)

Docker Hub에 `{git-sha}` 태그 이미지가 보존되어 있다. EC2에서 이전 SHA를 지정해 컨테이너를 재기동한다.

```bash
docker stop mztk-prod && docker rm mztk-prod

docker run -d --name mztk-prod --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  # ... (기존 -e 환경변수 동일하게 유지) \
  <DOCKER_HUB_USERNAME>/mztk-be:<이전-git-sha>
```

### 5-3. Flyway 마이그레이션 실패 시

서버가 기동되지 않는다. 원인을 파악한 뒤 아래 중 하나를 선택한다.

- **수정 후 재배포**: 새 마이그레이션 파일로 문제를 해결하고 다시 머지한다.
- **긴급 핫픽스**: `flyway_schema_history`에서 실패한 버전을 `DELETE`하고 서버를 재기동한다 (마이그레이션 파일 수정이 선행되어야 함).

---

## 6. 배포 완료 기준

아래 조건이 모두 만족되어야 배포가 완료된 것으로 본다.

- [ ] `deploy-prod.yml` Actions 초록불
- [ ] `health-check-delayed.yml` Actions 초록불 (배포 후 약 10분 후 확인)
- [ ] 신규 DB인 경우: `web3_treasury_keys` 등록 완료
