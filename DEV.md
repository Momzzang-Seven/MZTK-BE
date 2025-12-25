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

1️⃣ `.env` 준비
```bash
cp .env.example .env
````

2️⃣ 필수 도구

* JDK 21
* Docker

3️⃣ Spring Profile

```
SPRING_PROFILES_ACTIVE=dev
```

4️⃣ Git Hooks 설치 (최초 1회)

```bash
sh scripts/git-hooks/install.sh
```

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

포맷 적용(선택)

```bash
./gradlew spotlessApply
```

---

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

## 커밋 규칙

일반 커밋 흐름

```bash
git add .
git commit
```

자동 처리됨

* 코드 포맷 검사
* Checkstyle 검사
* 커밋 메시지 규칙 검사 + 자동 보정

자동 생성 형식

```
[MZTK-123] feat: add login api
```

실패 메시지가 나오면 안내에 따라 수정 후 다시 커밋하세요.

---

## PR 규칙

### 🔹 feature → dev

* Base: `dev`
* Source: `feat/...`

자동 처리됨

* PR 제목 자동 생성
* Jira Key 자동 적용
* PR 템플릿 자동 생성

개발자가 할 일
- 1️⃣ PR 생성
- 2️⃣ 자동 작성된 내용 확인 및 보완
- 3️⃣ 리뷰 요청 및 반영
- 4️⃣ CI 통과 확인

---

### 🔹 dev → main

* 릴리즈 PR은 자동으로 생성됩니다.
* 팀 확인 후 머지 진행합니다.

---

## CI (자동 검사)

Push / Pull Request 시 자동 실행됩니다.

* 빌드
* 테스트
* 코드 스타일 검사
* 보안 / 시크릿 스캔

통과하지 못하면 머지 불가합니다.

---

## ✅ 핵심 정리

1. 브랜치는 `feat/MZTK-xxx-...`
2. 그냥 commit — 규칙은 자동 처리
3. PR은 `feat → dev`, 리뷰 후 머지
4. `dev → main` 릴리즈는 자동 생성

---