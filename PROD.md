# Production 배포 가이드

MZTK-BE 운영 배포의 전체 흐름을 한 장으로 정리한 문서. 상세 로직은 각 파일 주석을 참고.

## 1. 구성 요소 한눈에 보기

### 1.1 env 파일 (repo 루트)
| 파일 | 역할                                                                                                                                   | SSM 경로 |
|---|--------------------------------------------------------------------------------------------------------------------------------------|---|
| `.env.example` | 전체 키의 source of truth. 로컬 개발용 샘플이자 prod 키 동기화의 기준                                                                                    | — |
| `.env.prod.common` | chain/network 무관한 공통 운영값 (DB, JWT, OAuth, AWS, …)                                                                                    | `/mztk/prod/common/*` |
| `.env.prod.web3.<chain>.<network>.template` | web3 전용값. `optimism/base` × `testnet/mainnet` 4종. 추후 네트워크 추가됨에 따라 추가될 수 있음.                                                          | `/mztk/prod/web3/<chain>/<network>/*` |
| `.env.ec2` | 배포 담당자 로컬에만 존재. EC2 SSH + Docker Hub 크레덴셜 (`EC2_HOST`, `EC2_USER`, `EC2_KEY_PATH`, `DOCKER_HUB_USERNAME`, `DOCKER_HUB_ACCESS_TOKEN`) | — |

> 4개의 web3 템플릿은 **키 집합이 항상 동일**해야 한다. `sync-env-keys-multi.sh` 가 이를 강제한다.

### 1.2 SSM Parameter Store 레이아웃 (`ap-northeast-2`)
```
/mztk/prod/
├── active-chain         # "optimism" | "base"   ← 런타임 스위치
├── active-network       # "testnet"  | "mainnet"← 런타임 스위치
├── common/<KEY>
└── web3/<chain>/<network>/<KEY>
```
- 민감 키 (`*SECRET*`, `*KEY*`, `*PASSWORD*`, `DB_URL_PROD`) → `SecureString` + KMS `alias/mztk-ssm-prod`
- 그 외 → `String`
- 모든 파라미터에 `Project=mztk`, `Env=prod` 태그 부여

### 1.3 스크립트 (`scripts/ci/`)
| 스크립트 | 역할 | 옵션                                                                                                                                                                                                                                                                                                                      |
|---|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sync-env-keys-multi.sh` | `.env.example` 에 새로 생긴 키를 `common` 또는 4× `web3` 템플릿에 빈 값으로 append. | `--check`: append 없이 드리프트만 검출 (있으면 `exit 1` — CI/프리커밋용). <br>`--dry-run`: 분류 결과만 출력, 파일 미변경. <br>`-h`, `--help`: 사용법 출력. <br>옵션 없이 호출하면 apply(append) 모드.                                                                                                                                                               |
| `ssm-upsert.sh <env-file>` | 파일명으로부터 SSM 접두어를 자동 추론해 KEY=VALUE 업로드. empty value `""` 은 skip + 경고. | 플래그 없음. 인자: `<env-file>` 단일 필수. <br>환경변수: `AWS_REGION` (기본 `ap-northeast-2`).                                                                                                                                                                                                                                           |
| `refresh-env.sh` | **EC2 위에서 실행**. SSM의 `active-chain × active-network` 를 읽어 `~/apps/.env` 재생성. `WEB3_CHAIN_ID` 가 예상값과 다르면 배포 중단. | 플래그/인자 없음. <br>환경변수: `AWS_REGION` (기본 `ap-northeast-2`).                                                                                                                                                                                                                                                                |
| `refresh-and-restart.sh` | **로컬에서 실행하는 수동 배포 엔트리**. SSM 업로드 → active 스위치 갱신 → `refresh-env.sh` 를 EC2 로 scp → 원격에서 env 재생성 + 컨테이너 재기동 + 헬스체크. | 플래그/인자 없음. <br>환경변수: <br> - `CHAIN` (기본 `optimism`, 허용 `optimism`\|`base`), <br> - `NETWORK` (기본 `testnet`, 허용 `testnet`\|`mainnet`), <br> - `AWS_REGION` (기본 `ap-northeast-2`). <br>필수 파일: repo 루트 <br> - `.env.ec2` (`EC2_HOST`, `EC2_USER`, `EC2_KEY_PATH`, `DOCKER_HUB_USERNAME`, `DOCKER_HUB_ACCESS_TOKEN` 정보 소유). |

### 1.4 GitHub Actions (`.github/workflows/deploy-prod.yml`)
`main` 브랜치 push (또는 `workflow_dispatch`) 시 자동 배포.

## 2. 코드 변경 되었을 때 자동 배포 흐름 (main merge)

1. **Trigger**: `main` 에 push (`src/**`, `build.gradle`, `Dockerfile`, workflow 파일 변경 시)
2. **Test**: GitHub Actions 안에 `postgis/postgis:17-3.4` 서비스 컨테이너(`mztk_ci`)를 띄우고 `./gradlew test` 실행. `MigrationValidationTest` 가 Flyway + Hibernate validate 로 migration/entity 드리프트 차단.
3. **Build**: `./gradlew clean bootJar` → `docker/build-push-action` 으로 `linux/amd64` 이미지 빌드, Docker Hub 에 `mztk-be:latest` 와 `mztk-be:<sha>` 태그로 push.
4. **Deploy (EC2 SSH)**: `appleboy/ssh-action` 으로 EC2 접속 후 `~/apps` 에서 다음 수행:
   1. `./refresh-env.sh` — SSM → `~/apps/.env` 재생성 (chainId 가드 포함)
   2. Docker Hub 로그인 → `docker pull …/mztk-be:latest`
   3. 기존 `mztk-prod` 컨테이너 stop/rm → `--env-file ~/apps/.env`, `SPRING_PROFILES_ACTIVE=prod`, `-p 8080:8080` 로 재실행
   4. `docker image prune -f`
   5. 20초 대기 후 `/actuator/health` 헬스체크 (5초 간격 × 12회). 실패 시 최근 50줄 로그 덤프 + exit 1.

> workflow 는 `~/apps/refresh-env.sh` 가 **이미 EC2 에 존재한다고 가정**한다. 스크립트 자체가 바뀔 때는 `refresh-and-restart.sh` (혹은 수동 scp) 로 한 번 올려줘야 한다.

## 3. 환경변수만 변경되었을 때 배포 흐름 (코드 변경 없음)

DB 크레덴셜, JWT, 컨트랙트 주소 등 값만 바뀌는 경우 — workflow 를 돌리지 않고 로컬에서 수동으로 처리한다.

```bash
# 0. (필요시) .env.example 에 키를 먼저 추가한 뒤 템플릿에 빈 슬롯 동기화
./scripts/ci/sync-env-keys-multi.sh            # append
./scripts/ci/sync-env-keys-multi.sh --check    # CI/프리커밋에서 드리프트 확인

# 1. .env.prod.common / .env.prod.web3.<chain>.<network>.template 값 채움

# 2. 로컬에서 수동 배포 (기본: optimism × testnet)
./scripts/ci/refresh-and-restart.sh

# 다른 chain network으로 전환해야 하는 경우
CHAIN=optimism NETWORK=mainnet ./scripts/ci/refresh-and-restart.sh
CHAIN=base     NETWORK=testnet ./scripts/ci/refresh-and-restart.sh
```

`refresh-and-restart.sh` 4단계:
1. **환경변수 업데이트**: `ssm-upsert.sh` 로 `common` + 선택된 `web3/<chain>/<network>` 환경변수 SSM에 업로드
2. **활성 chain network 변경**: `/mztk/prod/active-chain`, `/mztk/prod/active-network` 갱신
3. **EC2 .env 최신화**: `refresh-env.sh` 를 EC2 `~/apps/` 로 scp (+ chmod)
4. **SSH로 컨테이너 reload, run**: `.env` 재생성 → docker pull → stop/rm → run → 헬스체크

## 4. Chain × Network 스위칭 방식

- 애플리케이션 이미지는 chain/network 를 **하드코딩하지 않는다**. 동일한 `mztk-be:latest` 가 `.env` 의 `ACTIVE_CHAIN`, `ACTIVE_NETWORK`, `WEB3_CHAIN_ID` 로 동작.
- Chain network 전환은 `CHAIN` / `NETWORK` 환경변수만 바꿔 `refresh-and-restart.sh` 를 재실행하면 된다.
- 단, 해당 `CHAIN` / `NETWORK`에 해당하는 .env 파일이 존재해야 한다. 
```bash
CHAIN=<new chain> NETWORK=<new network> refresh-and-restart.sh
```

## 5. 수동 배포 시 사전 조건 체크리스트

- [ ] AWS CLI 로그인 (배포자 로컬, `ap-northeast-2` 접근 + SSM/KMS 권한)
- [ ] KMS 키 `alias/mztk-ssm-prod` 존재
- [ ] `.env.ec2` 에 EC2 + Docker Hub 크레덴셜 채움
- [ ] GitHub Secrets: `DOCKER_HUB_USERNAME`, `DOCKER_HUB_ACCESS_TOKEN`, `EC2_PUBLIC_IP`, `EC2_USERNAME`, `EC2_KEY`
- [ ] EC2 `~/apps/` 에 `refresh-env.sh` 존재 (최초 1회 or 스크립트 갱신 시 재업로드)
- [ ] `.env.example` ↔ prod 템플릿 드리프트 없음 (`sync-env-keys-multi.sh --check`)

## 6. 장애 시 확인 순서

1. **헬스체크 실패 로그**: workflow 또는 `refresh-and-restart.sh` 말미에 `docker logs --tail 50 mztk-prod` 자동 출력.
2. **env 불일치 의심**: EC2 에서 `grep -E '^(ACTIVE_CHAIN|ACTIVE_NETWORK|WEB3_CHAIN_ID)=' ~/apps/.env`.
3. **SSM 값 재확인**: `aws ssm get-parameters-by-path --path /mztk/prod/ --recursive --with-decryption`.
4. **롤백**: Docker Hub 의 이전 `mztk-be:<sha>` 태그로 EC2 에서 수동 `docker run` (workflow 는 `latest` 만 갱신하므로 이전 sha 이미지가 여전히 pull 가능).
