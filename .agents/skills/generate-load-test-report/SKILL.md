---
name: generate-load-test-report
description: >
  k6 부하테스트 회차 결과를 Prometheus 시계열 + SVG 차트 + 인사이트 보고서로 정리하는
  MZTK-BE 전용 스킬. k6 터미널 로그와 result JSON 을 입력받아 측정 구간을 확정하고,
  Prometheus 에서 서버 시계열을 추출해 차트를 만들고, RESULT_GUIDE.md 템플릿에 맞춰
  PASS/FAIL 판정과 도출 가능한 모든 인사이트를 담은 결과 문서(RESULT_<유형>.md)를 작성한다.
  사용자가 "부하테스트 결과 정리해줘", "load test 결과 만들어줘", "k6 결과 문서화해줘",
  "breakpoint/load/endurance 결과 보고서 써줘", "성능테스트 결과 차트 만들어줘",
  "이 부하테스트 결과 RESULT 문서로" 등 부하·성능 테스트 *회차 결과* 를 정리·문서화·시각화해
  달라고 하면 반드시 이 스킬을 사용하라. 부하테스트를 *실행* 하거나 *계획* 을 세우는 요청,
  단순 k6 스크립트 작성에는 사용하지 않는다.
---

# 부하테스트 결과 보고서 생성 (MZTK-BE)

k6 부하테스트 한 회차의 원시 결과(터미널 로그 + result JSON)를 받아, Prometheus 서버
시계열을 근거로 한 차트와 인사이트 보고서를 만든다. 목표는 "테스트가 PASS/FAIL 인가,
그리고 데이터가 말해주는 모든 것" 을 한 문서로 남기는 것이다.

## 산출물

회차 산출물은 **테스트 실행 날짜별로** `docs.local/load_test/phaseN/results/{YYYY.MM.DD}/`
아래에 모은다 (`{YYYY.MM.DD}` 예: `2026.05.23`):
- `k6/<유형>_<날짜>.json` — k6 raw result JSON (`run.sh` 가 생성)
- `charts/<유형>_NN_*.svg` — 테스트 성격에 맞춰 고른 시계열 차트
- `reports/RESULT_<유형>.md` — 결과 문서 (`RESULT_BP.md` / `RESULT_LOAD.md` / `RESULT_ENDU.md`)

날짜 디렉토리는 **테스트를 실행한 날**이다 — 한 회차의 k6 JSON·차트·보고서가 같은 폴더에
모여 날짜별로 확인된다. `RESULT_GUIDE.md`·`BP_INSIGHT_GUIDE.md`·`ENDU_INSIGHT_GUIDE.md`
같은 가이드/템플릿은 회차 산출물이 아니므로 `results/` 루트에 그대로 두고 날짜별로 나누지 않는다.

읽기·쓰기·외부연동(Web3/AWS/Gemini) 어떤 부하테스트든 다룬다. 기존 회차 예시:
`RESULT_LOAD.md`, `RESULT_ENDU.md` — **구조·서술 톤을 그대로 따른다.**

## 핵심 원칙

- **server 시계열이 1순위 근거.** k6 client 측정엔 맥북↔EC2 네트워크 RTT(~80–90ms)가 섞인다.
  판정은 Prometheus server 히스토그램으로, k6 JSON 은 교차검증·시나리오별 분해용으로 쓴다.
- **인사이트는 빠짐없이.** 콜드스타트, 측정 갭, 자원 여유/포화, 시드 noise, 유형별 특이 패턴 —
  데이터에서 읽히는 건 전부 "특이사항" 에 적는다. 표만 채우고 끝내지 않는다.
- **읽기·쓰기를 가리지 않는다.** 쓰기·외부연동(Web3 온체인 TX·AWS KMS/S3·Gemini) 테스트는
  외부 호출 지연·실패와 비동기 처리 백로그(요청 성공 ≠ 처리 완료)가 핵심이다. 어떤 자원을
  볼지·어떤 차트를 그릴지는 `references/metrics-and-judgment.md` §5 의 성격별 초점을 따른다.
- **부족하면 묻는다.** 추측으로 빈칸을 메우지 말 것. Step 1·3 의 정보 부족은 사용자에게 요청한다.

## 워크플로

작업 전 `references/metrics-and-judgment.md` 를 읽어 메트릭·설정·판정 기준을 파악한다.

### Step 1 — 입력 수집 & 누락 정보 확인

다음을 확보한다. 하나라도 없으면 **진행을 멈추고 사용자에게 요청한다:**
- 테스트 유형 (breakpoint / load / endurance) — k6 로그 `TEST_TYPE=` 에서 읽거나 질문
- k6 터미널 로그 — THRESHOLDS / CUSTOM / TOTAL RESULTS 블록 전체
- k6 result JSON 경로 — `run.sh` 산출물은 `phaseN/results/{YYYY.MM.DD}/k6/<유형>_<날짜>.json`
- 출력 phase·날짜 — 산출물을 둘 `phaseN/results/{YYYY.MM.DD}/` 디렉토리. 날짜는 **k6 JSON 이
  들어 있는 `{YYYY.MM.DD}` 폴더**(JSON 의 `k6/` 의 부모)에서 그대로 가져온다. JSON 이 그
  구조 밖(레거시 경로)에 있으면 테스트 실행 날짜로 `results/{YYYY.MM.DD}/` 를 잡고 사용자에게 확인한다

인프라 상수(-Xmx, HikariCP pool max, EC2 인스턴스, 커밋 SHA)는 레포에서 확인한다:
커밋 SHA 는 `git rev-parse --short HEAD`, pool max 는 `application.yml`, -Xmx 는 `Dockerfile`.
**레포에서 확신 있게 못 찾으면 사용자에게 확인**한다 — 보고서 "실행 환경" 에 들어가는 값이다.

### Step 2 — 측정 구간 확정

k6 JSON 은 수백 MB 다. `grep -m1` / `tail -c` 로 양 끝 timestamp 만 본다
(방법은 `references/metrics-and-judgment.md` §2). 시나리오 시작(setup 제외 첫 포인트)을
START(=T0), 마지막 줄을 END 로 잡고, k6 로그의 총 실행시간·stage 합과 교차검증한다.

### Step 3 — Prometheus 가용성 확인

```bash
curl -s http://localhost:9090/-/healthy
```
측정 구간에 스크레이프 데이터가 있는지 `up{job="mztk-be"}` 등으로 확인한다. Prometheus 가
꺼져 있거나 해당 구간 데이터가 보존기간에서 밀려났으면 **server 차트를 만들 수 없다** —
사용자에게 알리고, k6 JSON 만으로 축약 보고서를 쓸지 / 중단할지 결정을 받는다.

### Step 4 — 메트릭 probe

```bash
python3 <skill>/scripts/probe_metrics.py --start <START> --end <END> \
    [--baseline 5,15 --late 52,62]   # --baseline/--late 는 endurance 에서만
```
출력의 min/max 로 차트 축 범위를 정하고, endurance 면 drift 컬럼으로 Baseline/Late
증가율을 판정한다. 5xx·GC·누수 신호, 외부 호출(`http_client_*`)·비동기 큐 적체
(`executor_*`)도 여기서 1차 확인한다 — NO DATA 면 그 부분이 미계측이라는 진단이다.

### Step 5 — 차트 설정 작성 & 생성

`references/chart-palette.json` 에서 이번 테스트에 맞는 차트를 고른다 — universal 4종
(throughput / latency / cpu / heap)은 항상, DB 접근이면 `db_pool`, endurance 면
`leak_endurance`, 쓰기·외부연동이면 `ext_latency` / `ext_errors` / `async_queue` 를
더한다(선택 기준은 `references` §5). 고른 정의를 `chart_config.json` 의 `charts` 배열에
복사하고, probe 결과로 `ymax`/`yticks` 를, 인프라 상수로 `title` 빈칸(SLO·-Xmx·pool max)을
채운다. `clock0`·`stage_vu`·`xticks` 는 k6 stage 와 START 로부터 계산한다 — xticks 는
라벨이 겹치지 않게 4 index 이상 띄운다(스키마 전체는 `references` §4). 그다음:
```bash
python3 <skill>/scripts/make_charts.py chart_config.json
```
차트가 `chart_config.json` 의 `outdir`(= 회차 날짜 폴더 아래 `phaseN/results/{YYYY.MM.DD}/charts/`)에
생성된다. 팔레트에 없는 자원이 병목이면 §3 메트릭으로
차트를 직접 정의해도 된다 — 스키마는 동일하다.

> **⚠️ `charts/` 정리 시 절대 와일드카드로 전부 지우지 말 것.** 한 날짜 폴더의 `charts/`
> 는 같은 날짜의 다른 회차 유형(BP/Load/Endurance/Spike) 산출물을 함께 보관한다.
> 재생성 전에 정리한다면 **본 회차 prefix 로 좁힌다** — 예: `rm -f charts/load_*.svg`,
> `rm -f charts/breakpoint_*.svg`. `rm *.svg` / `xargs rm -f` 는 다른 회차의 차트까지
> 날리고, 그 회차의 RESULT 문서 임베드 링크가 깨진다. 실제로 한 번 발생한 사고다.
> 더 안전한 방법은 정리하지 않고 그냥 덮어쓰는 것이다(`make_charts.py` 는 동일 파일명을
> 덮어쓴다). 만에 하나 지운다면 **본인이 만든 파일만** 지운다.

### Step 6 — 차트 육안 검증

각 SVG 를 PNG 로 변환해 실제로 본다:
```bash
qlmanage -t -s 880 -o /tmp/ltqc <svg파일들>
```
변환된 PNG 를 Read 로 확인 — 선이 차트 위로 잘리면(클리핑) `chart_config.json` 의 `ymax` 를
키워 Step 5 부터 재생성한다. 검증 없이 보고서에 임베드하지 않는다.

### Step 7 — 보고서 작성

`results/` 루트에 phase 자체 `RESULT_GUIDE.md` 가 있으면 그 템플릿을, 없으면 번들된
`assets/RESULT_GUIDE.md` 를 기준으로 그 회차 날짜 폴더 `results/{YYYY.MM.DD}/reports/` 아래에
`RESULT_<유형>.md` 를 쓴다(같은 날짜에 같은 유형을 재측정하면 그 폴더의 기존 파일 맨 위에
회차 섹션 추가; 다른 날짜면 새 날짜 폴더에 새 파일). 판정 기준은 **해당 phase 의 판정 가이드가 있으면 1순위로**
적용한다 — breakpoint → `BP_INSIGHT_GUIDE.md`, endurance → `ENDU_INSIGHT_GUIDE.md`,
load → `LOAD_TEST_PLAN.md §7`. 가이드가 없으면 `references` §5 + `LOAD_TEST_PLAN.md` 로 판정한다.
생성한 차트를 전부 markdown `![](../charts/...)` 으로 임베드하고 각 차트마다 1\~2문장 판독을 단다.
PASS/FAIL 을 명확히 쓰고, 특이사항·개선 액션·측정 한계를 빠짐없이 도출한다 — 쓰기·외부연동
회차는 외부 호출 지연/실패와 비동기 백로그(요청 성공 ≠ 처리 완료)를 반드시 다룬다(`references` §5).
새 결과 파일이면 첫머리에 헤더 + 안내 인트로를 기존 `RESULT_LOAD.md` 처럼 붙인다.

### Step 8 — 참조 정합성 정리

새 `RESULT_<유형>.md` 를 만들었으면 `results/RESULT_GUIDE.md` 의 유형↔파일 매핑 표가 날짜
폴더 구조(`{YYYY.MM.DD}/reports/RESULT_<유형>.md`)를 반영하는지, 관련 INSIGHT_GUIDE 의 결과
파일 참조가 stale 하지 않은지 확인해 갱신한다. stale 참조가 남지 않게 한다.

## 번들 리소스

- `scripts/make_charts.py` — config-JSON 구동 SVG 차트 생성기 (차트 목록 완전 일반화, 외부 의존성 없음)
- `scripts/probe_metrics.py` — Prometheus 전수 probe (서버·외부호출·비동기, 축 범위 + drift)
- `references/chart-palette.json` — 표준 차트 9종 팔레트 (universal + DB · 누수 · 외부호출 · 비동기)
- `references/metrics-and-judgment.md` — 메트릭 카탈로그 · 차트 스키마 · 유형별·성격별 판정 기준
- `assets/RESULT_GUIDE.md` — 결과 문서 템플릿 (일반화 버전)

> `assets/RESULT_GUIDE.md` 는 범용 템플릿이다. phase 의 results 디렉토리에 자체 `RESULT_GUIDE.md`
> 가 있으면 그것(phase 고유 시나리오 반영)을 우선한다.
