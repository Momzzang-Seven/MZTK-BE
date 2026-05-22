# 부하테스트 결과 기록 가이드 (범용 템플릿)

> 본 문서는 **결과 기록 템플릿과 작성 규칙**만 담는다. 측정 결과를 이 파일에 직접 쓰지 말 것.
> 새 회차 결과는 아래 템플릿을 복사해 테스트 유형별 `RESULT_XX.md` 파일에 작성한다.
> 이 파일은 `generate-load-test-report` 스킬의 **범용 템플릿**이다 — phase 의 results 디렉토리에
> 자체 `RESULT_GUIDE.md`(phase 고유 시나리오 반영)가 있으면 그쪽을 우선한다.
>
> | 테스트 유형 | 결과 파일 |
> |---|---|
> | Breakpoint | `RESULT_BP.md` |
> | Load | `RESULT_LOAD.md` |
> | Endurance | `RESULT_ENDU.md` |
>
> - 같은 유형을 여러 번 측정하면 해당 `RESULT_XX.md` 안에 회차 섹션을 누적한다 (최신 회차가 맨 위).
> - 새 유형을 측정하면 `RESULT_<유형>.md` 를 새로 만들고 아래 템플릿으로 채운다.
> - Prometheus 시계열 차트는 스킬의 `make_charts.py` 로 생성해 결과 파일에 임베드한다.
> - PASS/FAIL 기준은 `LOAD_TEST_PLAN.md §7`. 유형별 판정 가이드(`BP_INSIGHT_GUIDE.md` /
>   `ENDU_INSIGHT_GUIDE.md`)가 phase 에 있으면 그것을 1순위로 적용한다.
> - 읽기 / 쓰기·외부연동(Web3·AWS·Gemini) 회차에 따라 아래 (전용) 표시 항목을 취사 기재한다.

---

## 결과 기록 템플릿

> 새 `RESULT_XX.md` 를 만들 때 아래 코드블록을 복사해 채운다. (전용) 항목은 해당 테스트에만.

```markdown
## [테스트 유형] 결과 — YYYY-MM-DD

**실행 환경**
- BASE_URL        :
- 서버 인스턴스    : EC2 (vCPU __ / JVM -Xmx __ MB)
- DB              : RDS PostgreSQL · HikariCP pool max __
- 외부 연동        : (외부연동 테스트 — Web3 RPC / AWS KMS·S3 / Gemini 중 해당)
- k6 실행 위치    : 로컬 맥북 → 인터넷 → EC2
- 커밋 SHA        :
- 시드 규모       : (시나리오별 시드 — 예: posts / answers / classes / users)
- 토큰 수 · VU    : USER __명 / 본 회차 VU __

**측정 결과**

| 항목                                  | 목표      | 실측값 | 판정 |
|--------------------------------------|----------|------|------|
| p50 응답시간                          | ≤ 200ms  |      |      |
| p95 응답시간                          | ≤ 500ms  |      |      |
| p99 응답시간                          | ≤ 800ms  |      |      |
| server_errors (5xx)                  | < 1%     |      |      |
| business_4xx_rate                    | < 0.5%   |      |      |
| 평균 / 최대 TPS                       | -        |      |      |
| 임계 VU (Breakpoint 전용)             | ≥ 200    |      |      |
| 응답시간 증가율 (Endurance 전용)       | < 20%    |      |      |
| token_reissues (Endurance 전용)       | > 99%    |      |      |
| 외부 호출 p95 / 실패율 (외부연동 전용) | -        |      |      |
| 비동기 처리 완료율 (쓰기 전용)         | -        |      |      |

> SLO 목표값은 테스트 성격에 맞춘다 — 쓰기·외부연동(온체인 TX·KMS·Gemini) 엔드포인트는
> 읽기 대비 p95/p99 목표가 훨씬 느슨하다. breakpoint 는 p99 ≤ 1,000ms 등 유형별 기준을 따른다.

**시나리오별 응답시간** (k6 custom trend)

| 시나리오 | p50 | p95 | p99 | 에러율 |
|---|---|---|---|---|
| (시나리오마다 행 추가 — 예: C-01 게시글 목록 / M-02 클래스 상세 / 쓰기 시나리오) |  |  |  |  |

**자원·외부 관찰 지표** (해당하는 것만 기재)
- JVM Heap used 추이 / GC pause          :
- HikariCP active·pending 최대값          :
- 외부 호출 p95·p99 / 실패율 (외부연동)    :
- 비동기 executor 큐 적체 추이 (쓰기)      :
- 메모리·커넥션·스레드 누수 (Endurance)    :

### 특이사항
-

### 개선 액션
-

### 측정 한계
-

### 첨부
- k6 stdout 로그 : `phaseN/results/<유형>_<날짜>.log`
- k6 JSON 결과   : `phaseN/results/<유형>_<날짜>.json`
- 시계열 차트     : `phaseN/results/charts/<유형>_NN_*.svg`
```
