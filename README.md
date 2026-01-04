# MZTK-BE
Back-End Server for Momzzang Token.

# 빠른 시작 가이드
### 1. JDK 21 설치 (https://www.oracle.com/kr/java/technologies/downloads/)
### 2. Docker Desktop 설치 (https://www.docker.com/products/docker-desktop/)
### 3. `.env` 파일 준비 (https://www.notion.so/BE-env-Value-2d446e15be2e80de9a32f4737157d7a0?source=copy_link)
### 4. 도커 컨테이너 실행 (```docker compose up -d```)
### 5. 서버 실행 (```./gradlew bootRun```)
### 6-1. Swagger UI 접속 (http://localhost:8080/swagger-ui/index.html)
### 6-2. API 문서 확인 (http://localhost:8080/v3/api-docs)
### 6-3. 노션 API 문서 확인 (https://www.notion.so/API-2cd46e15be2e80108267c5a780fe7bc0?source=copy_link)
### 7. 도커 컨이테이너 중지 (```docker compose down```)
### 8. 서버 종료 (```Ctrl + C```)


# 개발자 가이드 (DEV.md)

## 개요

- Spring Boot 3.3.4, Java 21 기반 백엔드입니다.
- PostgreSQL / Redis는 **Docker 기반으로 실행**합니다.
- 환경 변수는 `.env` 파일을 통해 주입됩니다 (`me.paulschwarz:spring-dotenv` 사용).
- 코드 포맷터는 Spotless(Google Java Format), 스타일 검증은 Checkstyle을 사용합니다.
- Git Hooks + GitHub Actions CI 로 코드 품질 및 컨벤션을 자동으로 관리합니다.

---

## 디렉터리 구조(주요)

- `build/` – 빌드 산출물(버전 관리 대상 아님)
- `config/checkstyle/` – Checkstyle 설정(`google_checks.xml`)
- `gradle/` – Gradle Wrapper 설정
- `src/main/java/momzzangseven/mztkbe/bootstrap/MztkBeApplication.java` – 엔트리포인트
- `src/main/resources/application.yml` – dev 기본 설정
- `.env.example` – 환경 변수 템플릿
- `build.gradle`, `settings.gradle`, `gradlew*` – 빌드/실행 스크립트

---

## 로컬 개발 준비

1️⃣ `.env` 준비 (노션 BE .env Value)
```bash
cp .env.example .env
````

2️⃣ 필수 도구

* JDK 21
* Docker Desktop 설치

## DB & Redis (Docker)

DB / Redis 실행

```bash
docker compose up -d
```

중지

```bash
docker compose down
```

DB 설정은 `.env` 로 주입됩니다.

---

## 실행 / 빌드 / 테스트

애플리케이션 실행

```bash
./gradlew bootRun
```

빌드

```bash
./gradlew build
```

테스트

```bash
./gradlew test
```
