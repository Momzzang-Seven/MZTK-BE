# <img src="./assets/readme/momzzang-logo.svg" width="46" align="center" alt="몸짱토큰 로고" /> 몸짱토큰 Backend

운동 인증, XP/레벨, 커뮤니티, 마켓플레이스, Web3 토큰 보상을 연결하는 Spring Boot 기반 API 서버입니다.

<p align="left">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-success?style=flat-square" alt="Spring Boot 3.4" />
  <img src="https://img.shields.io/badge/PostgreSQL%20%7C%20PostGIS-17-blue?style=flat-square" alt="PostgreSQL PostGIS" />
  <img src="https://img.shields.io/badge/Web3j-5.0-purple?style=flat-square" alt="Web3j" />
  <img src="https://img.shields.io/badge/AWS-EC2%20%7C%20RDS%20%7C%20S3%20%7C%20KMS-yellow?style=flat-square" alt="AWS" />
</p>

<p align="left">
  <a href="#overview">Overview</a>
  ·
  <a href="#architecture">Architecture</a>
  ·
  <a href="#service-domains">Service Domains</a>
  ·
  <a href="#web3-flow">Web3 Flow</a>
  ·
  <a href="#quality--operations">Quality</a>
  ·
  <a href="#local-development">Development</a>
  ·
  <a href="#backend-team">Team</a>
</p>

## Overview

**몸짱토큰**은 사용자의 운동 인증을 XP와 레벨로 전환하고, 레벨업 보상을 ERC-20 토큰으로 지급하는 Web3 피트니스 플랫폼입니다.

MZTK-BE는 앱에서 발생하는 운동 인증, 커뮤니티 활동, 클래스 예약, 보상 요청을 서버의 도메인 상태로 확정하는 백엔드 API 서버입니다. 이미지 저장, AI 검증, DB 트랜잭션, Web3 트랜잭션처럼 서로 다른 작업을 하나의 서비스 흐름 안에서 안전하게 연결합니다.

이 프로젝트에서 중요한 것은 단순한 CRUD API 구현보다 상태 전이의 일관성과 외부 시스템 실패에 대한 복구 가능성입니다. 운동 인증은 XP 증가와 레벨업으로 이어지고, 레벨업은 보상 지급 intent와 on-chain transaction으로 이어집니다. 백엔드는 이 과정에서 중복 실행, 누락, 지연된 transaction을 줄이고, 실패 상황을 추적·복구할 수 있도록 설계되어 있습니다.

이를 위해 MZTK-BE는 다음 책임을 담당합니다.

- **운동 인증 흐름 처리**: 사진, 위치, 운동 기록을 검증하고 XP와 레벨 상태에 반영합니다.
- **커뮤니티와 마켓플레이스 운영**: 게시글, 답변 채택, 클래스 예약, 정산 상태의 생성과 변경 흐름을 처리합니다.
- **Web3 실행 조율**: 사용자 서명 검증, sponsor transaction, escrow 정산, receipt polling을 수행합니다.
- **외부 시스템 동기화**: DB 커밋 이후 S3, Lambda, KMS, Web3 RPC 작업을 안전하게 이어갑니다.
- **운영 복구 지원**: 실패하거나 지연된 transaction과 외부 작업을 추적하고, 관리자가 재처리할 수 있는 흐름을 제공합니다.

## Architecture

<p align="center">
  <img src="./assets/readme/system-architecture.svg" width="820" alt="MZTK-BE system architecture" />
</p>

MZTK-BE는 Java 21과 Spring Boot 3.4 기반으로 동작합니다. HTTP, JPA, AWS SDK, Web3j 같은 기술 요소는 adapter 계층으로 분리하고, 핵심 비즈니스 규칙은 Hexagonal Architecture의 application/domain 계층에 둡니다.

- **Runtime**: Java 21, Spring Boot 3.4, Gradle
- **API/Security**: Spring MVC, Validation, Spring Security, JWT, OAuth2
- **Persistence**: PostgreSQL, PostGIS, JPA, QueryDSL, Flyway
- **External Adapter**: AWS S3/KMS/Secrets, Lambda, Web3j, Gemini API
- **Observability**: Actuator, Prometheus, Zipkin, Grafana/CloudWatch

세부 아키텍처 규칙은 [docs.shared/ARCHITECTURE.md](./docs.shared/ARCHITECTURE.md)를 기준으로 합니다.

## Service Domains

- **Account/User**: OAuth2 로그인, JWT, refresh token, 사용자 프로필, 권한
- **Verification/Level**: 운동 사진·위치·기록 인증, XP ledger, level-up, reward intent
- **Community**: 자유게시판, Q&A, 답변 채택, 댓글, 태그, 커서 페이지네이션
- **Marketplace**: 트레이너 클래스, 예약 승인·거절·취소, 정산 상태
- **Web3/Admin**: 지갑, 서명, 전송, escrow, treasury, transaction recovery, 운영자 조치

## Web3 Flow

Web3 기능은 사용자 서명, 서버 sponsor, smart contract 실행, DB 상태 변경이 함께 맞물려 동작합니다. 백엔드는 on-chain 실행을 즉시 완료된 작업으로 간주하지 않고, 실행 의도와 결과를 서버 상태로 기록해 중복 실행과 누락을 방지합니다.

```text
Client
  -> Challenge Request
  -> EIP-712 Signature
  -> Execution Intent
  -> EIP-7702 Sponsored Transaction
  -> Receipt Polling
  -> Domain Settlement / Recovery
```

- **Signature**: EIP-712 typed data를 사용해 사용자가 서명한 요청의 의미를 검증합니다.
- **Sponsor Transaction**: EIP-7702 기반 실행으로 사용자의 가스비 부담을 줄입니다.
- **Execution Intent**: 실행할 on-chain 작업을 DB에 먼저 기록해 중복 실행을 방지합니다.
- **Receipt Polling**: transaction receipt를 조회하며 pending, confirmed, failed 상태를 추적합니다.
- **Recovery**: timeout, failed, stuck transaction을 scheduler와 admin endpoint를 통해 재처리합니다.

DB 트랜잭션과 외부 side effect의 기준은 [docs.shared/EXTERNAL_SYSTEM_SYNC.md](./docs.shared/EXTERNAL_SYSTEM_SYNC.md)를 따릅니다.

## Quality & Operations

<p align="center">
  <img src="./assets/readme/ci-cd-pipeline.svg" width="760" alt="MZTK-BE CI/CD pipeline" />
</p>

<p align="center">
  <img src="./assets/readme/test-layer.svg" width="760" alt="MZTK-BE 4-layered test structure" />
</p>

- **CI**: Spotless, Checkstyle, unit/integration test, env coverage, Gitleaks, Semgrep를 검증합니다.
- **Test Layer**: Unit/Integration, Java E2E, Playwright API/E2E 테스트를 계층별로 분리합니다.
- **Migration**: Flyway migration과 entity drift를 검증합니다.
- **Deployment**: GitHub Actions, Docker Hub, EC2 기반 배포 흐름을 사용합니다.
- **Performance Summary**: 100 VU 기준 p95 189 ms, Load/Endurance 테스트 5xx 0%, breakpoint 약 300 VU를 확인했습니다.

상세 부하 테스트 로그와 회차별 분석은 README에 공개하지 않고 내부 문서 기준으로 관리합니다.

## Local Development

실제 환경 변수 값은 팀 내부 공유 기준을 따르며, README나 PR 본문에 남기지 않습니다.

```bash
./install-git-hooks.sh
docker compose up -d
./gradlew bootRun
```

| Purpose | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Health Check | `http://localhost:8080/actuator/health` |
| Prometheus Metrics | `http://localhost:8080/actuator/prometheus` |

PR 전 최소 확인 항목은 다음과 같습니다.

```bash
bash scripts/ci/check-env-coverage.sh
./gradlew spotlessCheck
./gradlew checkstyleMain
./gradlew test
```

E2E 검증이 필요한 변경은 live PostgreSQL 환경에서 별도로 실행합니다.

```bash
./gradlew e2eTest
```

## Documentation

| 문서 | 참고 시점 |
|---|---|
| [DEV.md](./DEV.md) | 로컬 개발 흐름, 검증 기준, 문서 참조 순서 |
| [ONBOARDING.md](./ONBOARDING.md) | 새 팀원 초기 세팅 |
| [PROD.md](./PROD.md) | 운영 배포와 운영 환경 변경 |
| [docs.shared/ARCHITECTURE.md](./docs.shared/ARCHITECTURE.md) | 레이어, 패키지, 모듈 의존성 판단 |
| [docs.shared/EXTERNAL_SYSTEM_SYNC.md](./docs.shared/EXTERNAL_SYSTEM_SYNC.md) | DB 트랜잭션과 외부 시스템 동기화 판단 |
| [AGENTS.md](./AGENTS.md) | AI Agent 작업 규칙 |

## Backend Team

주요 기여 영역은 모듈 구조와 커밋 이력을 기준으로 요약했습니다.

<table>
  <tr height="155px">
    <td align="center" width="190px">
      <a href="https://github.com/raewoo0908"><img height="104px" width="104px" src="https://avatars.githubusercontent.com/raewoo0908" alt="raewoo0908" /></a>
      <br />
      <a href="https://github.com/raewoo0908"><strong>강래우</strong></a>
      <br />
      Account · Web3 · Image/Location
    </td>
    <td align="center" width="190px">
      <a href="https://github.com/Nutriatree"><img height="104px" width="104px" src="https://avatars.githubusercontent.com/Nutriatree" alt="Nutriatree" /></a>
      <br />
      <a href="https://github.com/Nutriatree"><strong>박지우</strong></a>
      <br />
      Web3 · Marketplace · Level/Reward
    </td>
    <td align="center" width="190px">
      <a href="https://github.com/wdong218"><img height="104px" width="104px" src="https://avatars.githubusercontent.com/wdong218" alt="wdong218" /></a>
      <br />
      <a href="https://github.com/wdong218"><strong>우동현</strong></a>
      <br />
      Community · Admin · User Flow
    </td>
  </tr>
</table>