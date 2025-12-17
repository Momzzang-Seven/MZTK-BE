# 개발자 가이드 (DEV.md)

## 개요

- Spring Boot 3.3.4, Java 21 기반 백엔드입니다.
- PostgreSQL, Redis를 사용하며 `.env`로 환경 변수를 주입합니다 (`me.paulschwarz:spring-dotenv`).
- 코드 포매터는 Spotless(Google Java Format), 스타일 검증은 Checkstyle을 사용합니다.

## 디렉터리 구조(주요)

- `build/` – 빌드 산출물(버전 관리 대상 아님).
- `config/checkstyle/` – 체크스타일 설정(`google_checks.xml`).
- `gradle/` – Gradle Wrapper 설정.
- `src/main/java/momzzangseven/mztkbe/bootstrap/MztkBeApplication.java` – 애플리케이션 엔트리포인트.
- `src/main/resources/application.yml` – dev 프로파일 기본 설정(JPA `ddl-auto=update`, SQL 로그 노출 등).
- `src/main/resources/db/migration/` – Flyway 마이그레이션 스크립트 위치(현재 가이드만 포함).
- `.env.example` – 필수 환경 변수 템플릿.
- `build.gradle`, `settings.gradle`, `gradlew*` – 빌드/실행 스크립트.

## 로컬 개발 준비

- 필수: JDK 21, 로컬 PostgreSQL/Redis (또는 접근 가능한 인스턴스).
- `.env.example`를 복사해 `.env` 생성 후 값 채우기:
  ```
  cp .env.example .env
  ```
- `SPRING_PROFILES_ACTIVE=dev`로 dev 설정을 활성화합니다.

## 실행/빌드/테스트

- 애플리케이션 실행: `./gradlew bootRun`
- 빌드: `./gradlew build`
- 단위/통합 테스트: `./gradlew test`
- 코드 스타일 검사: `./gradlew check` (Spotless 포맷 검사 포함)
- 자동 포맷 적용: `./gradlew spotlessApply`

## Push 전 필수 체크 (중요)

- 모든 커밋은 아래 순서를 지킨 뒤 push 하는 것을 원칙으로 합니다.

- 코드 포맷 적용:
  `./gradlew spotlessApply`


- 코드 스타일 및 테스트 검사:
  `./gradlew check`


- check가 통과되지 않으면
  → 경고/에러를 수정한 후 다시 실행

- 모든 검사가 통과되면 그 상태 그대로 commit & push

⚠️ CI에서 동일한 검사를 수행할 예정,
로컬에서 check를 통과하지 못한 Commit은 Merge되지 않습니다.

## DB 마이그레이션 전략

- 경로: `src/main/resources/db/migration/`
- 초기 개발 단계는 JPA `ddl-auto=update`로 스키마를 빠르게 돌려보고, 첫 배포부터는 Flyway 스크립트를 작성합니다.
- 이름 규칙 예시: `V1__init.sql`, `V2__add_user_table.sql` (버전 오름차순, 더블 언더스코어로 구분).
- 마이그레이션을 추가하면 `./gradlew flywayMigrate`(플러그인 추가 시) 또는 애플리케이션 기동 시 자동 적용되도록 구성합니다.

## 코드/패키지 가이드

- 루트 패키지: `momzzangseven.mztkbe`.
- 새로운 모듈은 `src/main/java` 하위에 기능 단위 패키지로 나누고, `bootstrap` 패키지는 부트스트랩/설정 클래스만 둡니다.
- `application.yml`에 운영/스테이징 등 프로파일을 분리할 계획이라면 `spring.config.activate.on-profile` 블록을 복수 프로파일에 맞춰 추가합니다.
