# 메트릭 카탈로그 · 차트 설정 · 판정 기준

`SKILL.md` 워크플로의 상세 레퍼런스. 단계별로 필요할 때 해당 절만 읽으면 된다.
이 스킬은 **읽기·쓰기·외부연동(Web3/AWS/Gemini) 어떤 부하테스트든** 다루도록 일반화돼 있다.

## 목차
- [1. 입력 — 무엇이 어디서 오나](#1-입력)
- [2. 측정 구간 확정](#2-측정-구간-확정)
- [3. Prometheus 메트릭 카탈로그](#3-prometheus-메트릭-카탈로그)
- [4. 차트 설정 — chart_config.json](#4-차트-설정--chart_configjson)
- [5. 테스트 유형 · 읽기/쓰기 초점 · 판정 기준](#5-테스트-유형--읽기쓰기-초점--판정-기준)
- [6. probe_metrics.py 출력 해석](#6-probe_metricspy-출력-해석)

---

## 1. 입력

보고서 한 회차를 쓰려면 아래가 필요하다. 없으면 사용자에게 요청한다(워크플로 Step 1).

| 입력 | 출처 | 없을 때 |
|---|---|---|
| 테스트 유형 | k6 터미널 로그(`TEST_TYPE=...`) 또는 사용자 | breakpoint / load / endurance 중 무엇인지 질문 |
| 테스트 성격 | k6 시나리오·로그(읽기 위주 / 쓰기·외부연동 포함) | 모호하면 질문 — §5 판독 초점이 갈린다 |
| k6 터미널 로그 | 사용자가 붙여넣기 | 요청 — THRESHOLDS/CUSTOM/TOTAL 블록 전체 |
| k6 result JSON | `docs.local/load_test/phaseN/results/<유형>_<날짜>.json` | 경로 질문 |
| 결과 출력 디렉토리 | 보통 위 JSON 과 같은 `phaseN/results/` | 어느 phase 인지 질문 |
| Prometheus URL | 기본 `http://localhost:9090` | 다르면 질문 |
| 인프라 상수 (-Xmx, HikariCP pool max, EC2 인스턴스, executor 풀 크기) | `Dockerfile`(JVM heap), `application.yml`(`hikari.maximum-pool-size`, executor 설정), 기존 RESULT_*.md | 레포에서 못 찾으면 질문 |
| 커밋 SHA | `git rev-parse --short HEAD` | — |

k6 로그에서 바로 읽히는 것: BASE_URL, VU 수, 시드 규모, THRESHOLD PASS/FAIL, 시나리오별
custom trend, `http_req_*`, `server_errors`, `token_reissues`, `business_4xx_rate`, 총 iteration/req 수.
쓰기 테스트면 여기에 더해 **요청 성공 ≠ 비동기 처리 완료** 임을 유의한다(§5).

## 2. 측정 구간 확정

k6 JSON 은 거대(수백 MB)하므로 `grep -m1` / `tail -c` 로 양 끝만 본다.

```bash
# 시나리오 시작 = setup 이 아닌 첫 포인트의 time (= 측정 T0)
grep -m1 '"group":"","scenario":"default"' <result.json> | head -c 260
# 종료 = 마지막 줄의 time
tail -c 400 <result.json>
```

- **START** = 첫 포인트 timestamp, **END** = 마지막 timestamp. ISO8601(+09:00) 그대로.
- k6 로그의 `running (...)` 총 시간과 stage 합이 맞는지 교차 검증한다.
- stage 프로파일은 해당 phase 의 k6 스크립트(`phaseN.js` 의 `STAGE_PRESETS`)에서 **반드시 직접 확인**한다.
  아래는 phase1 기준 예시일 뿐 — phase 마다 다를 수 있다:

| 유형 | stage (예시, phase1) | 합계 |
|---|---|---|
| breakpoint | 5m@10 · 5m@50 · 5m@100 · 5m@150 · 5m@200 · 5m@300 | 30m |
| load | 2m ramp→100 · 10m@100 · 2m ramp→0 | 14m |
| endurance | 2m ramp→50 · 60m@50 · 2m ramp→0 | 64m |

## 3. Prometheus 메트릭 카탈로그

job 라벨은 배포 `prometheus.yml` 기준 `mztk-be`. actuator 자기호출은 `uri!~"/actuator.*"` 로 제외.
server 히스토그램을 1순위로 본다 — k6 client 측정엔 맥북↔EC2 네트워크 RTT(~80–90ms)가 섞인다.

### 3-A. 서버 · JVM (모든 테스트 공통)

| 메트릭 | 의미 | 쓰임 |
|---|---|---|
| `http_server_requests_seconds_{count,bucket}` | inbound 요청 수 / 지연 히스토그램 | RPS·p50/95/99·5xx·uri별 |
| `process_cpu_usage` / `system_cpu_usage` | JVM / 머신 CPU(0~1) | ×100 해서 % |
| `jvm_memory_used_bytes{area="heap"}` | 힙 사용량 | 톱니파(raw) |
| `jvm_memory_used_bytes{...id=~".*Old Gen.*"}` | old-gen 사용량 | **post-GC floor — 누수 1차 신호** |
| `jvm_gc_live_data_size_bytes` | major GC 직후 live set | 누수 신호(불변=major GC 없음) |
| `jvm_gc_pause_seconds_{count,sum,max}` | GC 횟수/시간/최대 pause | GC 열화. **action 라벨 다수 → `sum()`/`max()` 집계 필수** |
| `jvm_threads_live_threads` | 살아있는 스레드 수 | 스레드 누수 |
| `tomcat_threads_{busy,current,config_max}_threads` | Tomcat 워커 | 워커 포화 |
| `process_files_open_files` | 열린 FD 수 | FD/소켓 누수 |
| `hikaricp_connections_{active,pending,idle}` | DB 커넥션 풀 (읽기·쓰기 공통) | 풀 포화·누수 |
| `hikaricp_connections_{acquire_seconds_max,timeout_total}` | 커넥션 획득 지연/타임아웃 | **쓰기 경합 시 1순위 신호** |

### 3-B. 외부 호출 · 비동기 (쓰기 · 외부연동 테스트)

| 메트릭 | 의미 | 쓰임 |
|---|---|---|
| `http_client_requests_seconds_{count,bucket}` | **outbound** HTTP 호출 수/지연 | 외부 호출 latency·실패율. 라벨 `client_name`/`uri`/`outcome`/`status` |
| `executor_{active_threads,pool_size_threads,queued_tasks}` | `ThreadPoolTaskExecutor` 상태 | 비동기 쓰기 백로그(큐 적체) |
| `executor_completed_tasks_total` | 누적 처리 작업 수 | 비동기 처리율 |

> **계측 전제 — 외부 호출 관측은 공짜가 아니다.** `http_client_requests` 는 호출이
> Micrometer 가 계측하는 클라이언트(`WebClient`/`RestClient`/`RestTemplate`)를 거칠 때만 잡힌다.
> **Web3j 의 자체 HTTP, AWS SDK(KMS·S3), Gemini SDK** 는 그 경로를 안 탈 수 있어 — 그러면
> `http_client_*` 가 비어 있다. 그 경우 호출 지점에 `@Timed`/`Observation` 을 직접 더해야
> 외부 호출이 시계열에 나온다. `executor_*` 도 해당 `ThreadPoolTaskExecutor` 가 `MeterRegistry`
> 에 등록돼야 잡힌다. `probe_metrics.py` 가 이들을 질의하므로, **NO DATA 면 곧 "그 부분은
> 계측이 없다" 는 진단**이다 — 보고서 "측정 한계" 에 적고, 필요하면 개선 액션으로 instrumentation 을 건다.
> (ENDU_INSIGHT_GUIDE 의 JDBC 트레이싱 보강 권고와 같은 맥락.)

## 4. 차트 설정 — chart_config.json

`make_charts.py` 는 JSON 한 개로 구동된다. 차트 목록이 **완전히 config 안에** 있어, 읽기든
쓰기든 외부연동이든 `charts` 배열만 바꾸면 된다.

### 최상위 필드 (모든 차트 공통)

| 필드 | 정하는 법 |
|---|---|
| `prometheus` | Prometheus 호스트. 기본 `http://localhost:9090` |
| `start`/`end` | §2 측정 구간 (ISO8601) |
| `step` | 60(초) 기본 |
| `outdir` | `phaseN/results/charts` 절대경로 |
| `name` | 파일 접두사. 출력은 `<name>_<chart.file>.svg` (보통 테스트 유형) |
| `clock0` | `[START 의 시, 분]`. 예 00:50:32 → `[0,50]` |
| `stage_vu` | k6 stage 를 포인트 index 로. `[start_i, start_vu, end_i, end_vu]` 구간 나열. index = START 부터 분 |
| `xticks` | 눈금 찍을 포인트 index. **서로 4 index 이상 띄운다** — 가까우면 시각+VU 라벨이 겹친다 |
| `charts` | 차트 정의 배열 (아래) |

### 차트 한 개 (`charts` 배열 원소)

| 필드 | 의미 |
|---|---|
| `file` | 파일명 조각. 출력 `<name>_<file>.svg` |
| `title` / `ylabel` | 차트 제목 / y축 라벨 |
| `ymax` / `yticks` | y축 상한 / 눈금 수. probe 의 max 보다 약간 크게, 클리핑 방지 우선 |
| `series` | 선 배열. 각 원소 `{label, color, query}` |
| `series[].query` | PromQL 식. 특수값 `"@vu"` 는 k6 stage 에서 유도한 VU 곡선 |
| `refline` | (선택) `[값, "라벨"]` — 빨간 기준선 (예: pool max) |
| `annotate_max` | (선택) `{"series": N, "label": "... {v} ..."}` — N번 선의 최댓값 점에 주석. `{v}` → 정수값 |

### 표준 차트 팔레트

`references/chart-palette.json` 에 검증된 차트 정의 9종이 있다 — 필요한 것을 골라 `charts`
배열에 복사하고 `ymax`/`yticks`/`title` 의 빈칸을 probe 결과·인프라 상수로 채운다.

- **universal** (모든 테스트): `throughput` · `latency` · `cpu` · `heap`
- **opt-in** (해당 자원을 쓰는 테스트만):
  - `db_pool` — DB 접근 테스트 (읽기·쓰기 공통)
  - `leak_endurance` — endurance 전용 누수 차트
  - `ext_latency` · `ext_errors` — 외부 호출(Web3/AWS/Gemini) 테스트
  - `async_queue` — 비동기 쓰기 경로(TransactionIssuerWorker·배치 스케줄러) 테스트

읽기 위주 회차는 throughput/latency/cpu/db_pool/heap(+endurance면 leak) — phase1 의 5(+1)종.
쓰기·외부연동 회차는 여기에 `ext_latency`/`ext_errors`/`async_queue` 를 더한다. 팔레트에
없는 자원이 병목이면 §3 카탈로그의 메트릭으로 차트를 새로 정의해도 된다(스키마는 동일).

생성 후 **반드시 육안 검증**: `qlmanage -t -s 880 -o <tmp> <svg>` → PNG → Read.
선이 차트 위로 잘리면 `ymax` 를 키워 재생성한다.

## 5. 테스트 유형 · 읽기/쓰기 초점 · 판정 기준

**두 축이 직교한다.** "유형"(부하 거는 방식)과 "성격"(읽기냐 쓰기·외부연동이냐).

### 유형 — 부하 프로파일 (읽는 축)

| 유형 | 독립변수 | 읽는 것 | 판정 기준 출처 |
|---|---|---|---|
| breakpoint | VU(증가) | 응답시간·에러가 꺾이는 임계 VU(*무릎*) | `phaseN/results/BP_INSIGHT_GUIDE.md` (있으면) |
| load | 없음(고정) | 운영 목표 부하에서 SLO 충족 여부 | `LOAD_TEST_PLAN.md §7` |
| endurance | 경과시간(부하 고정) | 시간축 drift — 누수·열화 | `phaseN/results/ENDU_INSIGHT_GUIDE.md` (있으면) |

endurance 핵심: Baseline 윈도우(T+5\~15m) 대비 Late 윈도우(T+52\~62m) 기울기. server p95
`Late ÷ Baseline − 1 < 20%` 면 PASS. raw heap 톱니파는 정상 — **floor** 만 본다.
probe 의 `--baseline`/`--late` 로 drift 를 뽑는다. **해당 phase 의 판정 가이드가 있으면 그것을
1순위로** 따르고, 없으면 이 표 + `LOAD_TEST_PLAN.md` 로 판정한다.

### 성격 — 읽기 vs 쓰기·외부연동 (인사이트 초점)

**읽기 위주** (phase1: 게시글·클래스 조회 등)
- 병목 후보: DB 커넥션 풀, 힙/GC, Tomcat 워커. 차트는 universal + `db_pool`(+leak).
- 외부 호출 없음 → `http_client_*`·`executor_*` 는 NO DATA 가 정상.

**쓰기·외부연동** (phase2: Web3 온체인 TX, AWS KMS 서명·S3 업로드, Gemini AI 검증)
- **외부 호출 지연이 응답시간을 지배**한다 — RPC/KMS/S3/Gemini 는 수백 ms\~수 초. read SLO(p95<500ms)를
  그대로 쓰지 말 것: 쓰기/외부 엔드포인트는 SLO 가 훨씬 느슨하다. latency 차트 `ymax`·`lat_slo` 를 그에 맞춘다.
- **요청 성공 ≠ 작업 완료.** level-up→토큰 전송 같은 흐름은 이벤트로 비동기 워커(TransactionIssuerWorker,
  execution 배치 스케줄러)에 넘어간다. k6 가 보는 건 *요청 수락* 까지다. 실제 온체인 완료는
  `executor_queued_tasks`(백로그가 우상향하면 처리율<유입율) 와 TX 상태(CONFIRMED 비율)로 본다 —
  보고서에 "동기 응답" 과 "비동기 처리" 를 나눠 서술한다.
- **외부 실패/재시도**: `http_client` outcome=ERROR, server 5xx, KMS throttle·RPC timeout·Gemini
  rate limit. `ext_errors` 차트로 가시화하고, business_4xx(시드 noise)와는 구분한다.
- **쓰기 경합**: INSERT/UPDATE 는 lock·write 경합을 만든다. Prometheus 단독 신호는
  `hikari_acquire_seconds_max`·`active` 상승. 어느 쿼리·락인지는 Zipkin/RDS 영역(측정 한계로 기록).
- 차트는 universal + `db_pool` + `ext_latency`/`ext_errors`/`async_queue`(+endurance면 leak).

공통: k6 client vs server 측정 갭(네트워크 RTT)을 명시하고, `business_4xx` 는 시드 staleness
일 수 있으니 server 5xx·외부 호출 실패와 구분한다.

## 6. probe_metrics.py 출력 해석

```bash
python3 scripts/probe_metrics.py --start <ISO> --end <ISO> [--baseline 5,15 --late 52,62]
```

- 각 줄 `min/max` → 차트 `ymax` 산정. `max` 보다 약간 크게, 눈금이 깔끔하게.
- `first` 가 큰데 이후 급락 → 콜드스타트. latency 차트 `annotate_max` 라벨 근거.
- `--baseline`/`--late` 를 주면 `drift` 컬럼(endurance 전용). server p95 drift 가 20% 룰.
- `5xx_rate` `NO DATA` → 5xx 0건(정상). 값이 잡히면 FAIL 신호.
- `OUTBOUND CALLS` 가 전부 `NO DATA` → 외부 호출 미계측(§3). 외부연동 테스트면 측정 한계로 기록.
- `executor_queued_tasks` 가 우상향 → 비동기 백로그 누적. 평탄해야 정상.
- `gc_live_data_MB` 가 `first==last` 로 불변 → 측정 동안 major GC 없음 = old-gen 압박 없음(누수 무).
- `라벨 분해` → GC action별 횟수, 외부 호출 client_name별 분포, executor별 처리량을 확인.
- `hikari_pending`/`hikari_timeout_total` 이 0 이 아니면 풀 고갈.
