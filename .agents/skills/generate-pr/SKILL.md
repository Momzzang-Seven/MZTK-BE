---
name: generate-pr
description: >
  PR description을 자동으로 생성한다. 현재 브랜치의 git diff와 commit log를 분석하고,
  docs/design/ 및 docs/test/ 폴더에서 해당 티켓의 설계/테스트 문서를 참조하여
  .github/pull_request_template.md 양식에 맞는 PR 본문을 작성한다.
  사용자가 "PR 만들어줘", "PR description 작성", "PR 써줘", "generate PR", "PR 본문",
  "PR 내용" 등을 말하거나 PR을 열기 직전인 상황에서 반드시 이 스킬을 사용하라.
---

# generate-pr 스킬

현재 브랜치의 변경 내용을 분석하여 `.github/pull_request_template.md` 양식의
PR description을 완성한다.

## 목적

팀원이 PR을 열었을 때 **무엇을 왜 바꿨는지** 5분 안에 파악할 수 있도록 한다.
한 문장은 반드시 50자 이내로 작성한다.

---

## 실행 순서

### 1. 브랜치·티켓 정보 수집

```bash
git branch --show-current          # 브랜치명 전체
git log origin/develop..HEAD --oneline   # 이번 브랜치의 커밋 목록
```

브랜치명에서 Jira 티켓 번호를 추출한다.
예) `refactor/MOM-351-employ-execute-intent-using-kms` → `MOM-351`

### 2. 변경 코드 파악

```bash
git diff origin/develop...HEAD --stat      # 파일 목록 + 변경량
git diff origin/develop...HEAD --name-only # 파일 경로만
```

변경된 파일을 Java 패키지 단위로 그룹화한다.
(`src/main/java/.../modules/web3/eip7702/...` → `web3/eip7702`)

### 3. 설계 문서 탐색

`docs/design/` 하위에서 티켓 번호와 일치하는 디렉터리 또는 파일을 찾는다.

```bash
find docs/design -iname "*MOM-351*" -o -iname "*mom-351*" 2>/dev/null
# 상위 티켓(예: MOM-340)까지 확인할 필요가 있을 경우 함께 검색
```

찾은 파일을 읽어 핵심 설계 의도(AS-IS → TO-BE, 핵심 변경 표면)만 파악한다.
없으면 생략한다.

### 4. 테스트 문서 탐색

`docs/test/` 하위에서 티켓 번호와 일치하는 디렉터리 또는 파일을 찾는다.

```bash
find docs/test -iname "*MOM-351*" -o -iname "*mom-351*" 2>/dev/null
```

찾은 파일을 읽어 주요 테스트 케이스 목록을 파악한다.
없으면 생략한다.

### 5. PR description 작성

아래 템플릿을 **그대로** 사용하고 각 섹션을 채운다.

```markdown
<!-- AUTO-GENERATED-PR-BODY -->

## 관련 이슈

- [MOM-XXX]

## 작업 내용
- (핵심 변경 1)
- (핵심 변경 2)
- ...

## 변경 범위
- (패키지/모듈 1)
- (패키지/모듈 2)
- ...

## 테스트

- [x] 테스트 코드 작성 및 통과
- [x] 로컬 스모크 테스트 완료
```

---

## 섹션별 작성 기준

### 관련 이슈

브랜치명에서 추출한 Jira 티켓 번호를 `[MOM-XXX]` 형태로 기입한다.
여러 티켓을 커버하면 모두 나열한다.

### 작업 내용

- 설계 문서의 **핵심 AS-IS → TO-BE** 전환을 개괄식으로 요약한다.
- 새로 추가된 클래스·포트·어댑터 중 **핵심**만 언급한다.
  상세 목록은 변경 범위 섹션에서 패키지로 표현하면 충분하다.
- 구조적 변화가 있을 때는 도표(Mermaid 또는 Markdown table)를 활용한다.
- 한 항목은 반드시 50자 이내.

### 변경 범위

- `git diff --name-only` 결과를 Java 패키지 기준으로 묶는다.
  예) `web3/eip7702/domain`, `web3/execution/application`, `web3/shared/util`
- 테스트 코드 패키지도 별도 항목으로 표기한다.
- 한 항목은 반드시 50자 이내.

### 테스트

- 템플릿의 체크박스는 기본적으로 **모두 체크**(`[x]`)한다.
  실제로 해당하지 않는 항목은 `[ ]`로 남긴다.
- 테스트 문서가 있으면 **주요 테스트 케이스**를 개괄식으로 소개한다.
  단위 테스트, 통합 테스트, E2E 테스트를 구분한다.
  필요하면 도표를 활용한다.
- 각 항목은 반드시 50자 이내.

---

## 품질 기준

1. **50자 규칙** — 모든 문장·항목이 50자를 넘으면 안 된다. 넘으면 분할한다.
2. **개괄식** — 각 항목은 완전한 문장이 아닌 명사구·동사구로 시작한다.
3. **핵심만** — 코드 레벨 세부사항(메서드 시그니처 등)은 생략한다.
4. **도표 활용** — 비교(AS-IS/TO-BE), 테스트 케이스 목록 등 구조가 있는 정보는 표로 정리한다.
5. **언어** — 한국어로 작성한다.

---

## 출력 형식

PR description을 마크다운 형식으로 /Users/raewookang/Captone/MZTK-BE/docs/pr-description 하위에 브랜치 이름으로 .md file을 생성한다. 
