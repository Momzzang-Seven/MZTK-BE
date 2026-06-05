# DB Migration Guide

이 디렉터리는 MZTK-BE의 DB schema 변경 이력을 Flyway migration으로 관리하는 공간입니다.

현재 프로젝트는 이미 `V001__initial_schema.sql` 이후의 migration을 사용하고 있습니다. 따라서 이 디렉터리는 비워둘 수 있는 초기 상태가 아니며, integration/prod schema의 기준으로 취급합니다.

---

## 역할

- DB schema 변경 이력을 코드처럼 버전 관리합니다.
- CI, E2E, 운영 환경의 schema drift를 줄입니다.
- JPA `ddl-auto`에만 의존한 암묵적 변경을 막습니다.
- 운영 DB 변경 시 리뷰 가능한 SQL 단위를 남깁니다.

---

## 작성 규칙

- 기존 migration 파일은 수정하지 않습니다.
- 새 schema 변경은 다음 번호의 `Vxxx__description.sql` 파일로 추가합니다.
- 파일명은 변경 의도를 짧고 명확하게 표현합니다.
- 한 migration에는 서로 강하게 연결된 변경만 묶습니다.
- 운영 데이터나 secret 값은 migration에 남기지 않습니다.
- seed/reference data가 필요한 경우에도 실제 secret, private key, 운영 credential은 포함하지 않습니다.

---

## Profile별 기준

- `dev`: 로컬 개발 편의를 위해 Hibernate 설정이 다를 수 있습니다.
- `integration`: Flyway migration과 JPA entity 정합성을 검증합니다.
- `prod`: Flyway migration이 schema 변경의 기준입니다.

Profile별 세부 정책은 `DEV.md`, `src/main/AGENTS.md`, `src/test/AGENTS.md`를 함께 확인합니다. AI Agent가 migration을 수정한다면 상위 `AGENTS.md`도 함께 확인해야 합니다.

---

## PR 전 확인

```bash
bash scripts/ci/check-env-coverage.sh
./gradlew spotlessCheck
./gradlew checkstyleMain
./gradlew test
```

Migration이나 entity를 변경했다면 E2E 또는 migration validation이 필요한지도 함께 확인합니다.
