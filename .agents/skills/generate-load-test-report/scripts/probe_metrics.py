#!/usr/bin/env python3
"""부하테스트 구간의 Prometheus 메트릭 전수 probe.

차트 축 범위(ymax/yticks) 결정과 Baseline/Late drift 판정에 쓸 숫자를 한 번에 뽑는다.
make_charts.py 로 차트를 그리기 *전* 에 실행해 각 메트릭의 min/max/first/last 를 보고,
--baseline / --late 를 주면 endurance drift(우상향 기울기)까지 계산한다.

읽기·쓰기·외부호출 테스트 메트릭을 모두 질의한다. 계측되지 않은 메트릭은 'NO DATA'
로 나오며 — 그 자체가 정보다(예: 외부 호출이 Micrometer 로 계측 안 됨 → §3 instrumentation 필요).

사용법:
    python3 probe_metrics.py --start <ISO8601> --end <ISO8601> \\
        [--prom http://localhost:9090] [--job mztk-be] [--step 60] \\
        [--baseline 5,15] [--late 52,62]

--baseline / --late 는 포인트 index 범위(= START 부터의 분). endurance 에서만 의미.
GC·executor 계열은 라벨이 여러 개라 sum()/max() 로 집계해야 정확하다 — 이 스크립트가 처리한다.
"""
import argparse
import json
import urllib.parse
import urllib.request

ap = argparse.ArgumentParser()
ap.add_argument("--prom", default="http://localhost:9090")
ap.add_argument("--job", default="mztk-be")
ap.add_argument("--start", required=True)
ap.add_argument("--end", required=True)
ap.add_argument("--step", type=int, default=60)
ap.add_argument("--baseline", help="drift Baseline index range, e.g. 5,15")
ap.add_argument("--late", help="drift Late index range, e.g. 52,62")
A = ap.parse_args()

PROM, STEP = A.prom, A.step
JOB = f'job="{A.job}"'
NOACT = 'uri!~"/actuator.*"'
BASE = tuple(int(x) for x in A.baseline.split(",")) if A.baseline else None
LATE = tuple(int(x) for x in A.late.split(",")) if A.late else None


def qr(expr):
    qs = urllib.parse.urlencode(
        {"query": expr, "start": A.start, "end": A.end, "step": STEP})
    with urllib.request.urlopen(f"{PROM}/api/v1/query_range?{qs}", timeout=30) as r:
        return json.load(r)["data"]["result"]


def series(expr):
    res = qr(expr)
    if not res:
        return []
    out = []
    for _, v in res[0]["values"]:
        try:
            out.append(float(v))
        except ValueError:
            out.append(None)
    return out


def stat(name, expr):
    s = series(expr)
    if not s:
        print(f"  {name:32s} : NO DATA")
        return
    vals = [x for x in s if x is not None]
    if not vals:
        print(f"  {name:32s} : ALL NULL")
        return
    n = len(s)
    line = (f"  {name:32s} : n={n:3d} min={min(vals):10.2f} "
            f"max={max(vals):10.2f} first={s[0]} last={s[-1]}")
    if BASE and LATE:
        b = [s[i] for i in range(BASE[0], BASE[1] + 1) if i < n and s[i] is not None]
        l = [s[i] for i in range(LATE[0], LATE[1] + 1) if i < n and s[i] is not None]
        ba = sum(b) / len(b) if b else None
        la = sum(l) / len(l) if l else None
        drift = (f"{(la/ba-1)*100:+.1f}%" if (ba and la and ba > 0) else "n/a")
        line += (f"  | base={ba and round(ba,2)} late={la and round(la,2)} "
                 f"drift={drift}")
    print(line)


def discover(name, expr, tags):
    """누적 카운터의 라벨 분해 — 어떤 GC/executor/외부클라이언트가 도는지."""
    res = qr(expr)
    if not res:
        print(f"  ({name}: NO DATA)")
        return
    for r in res:
        m = r["metric"]
        v = [float(x) for _, x in r["values"]]
        tagstr = "  ".join(f'{t}={m.get(t,"?")}' for t in tags)
        print(f'  {tagstr:60s} first={v[0]:.0f} last={v[-1]:.0f} '
              f'delta={v[-1]-v[0]:.0f}')


print(f"# probe {A.start} ~ {A.end}  step={STEP}s  job={A.job}")
if BASE and LATE:
    print(f"# drift = Late(idx {LATE[0]}-{LATE[1]}) / Baseline(idx {BASE[0]}-{BASE[1]}) - 1")

print("\n=== TRAFFIC / LATENCY (server) ===")
stat("rps_server", f'sum(rate(http_server_requests_seconds_count{{{JOB},{NOACT}}}[1m]))')
stat("p50_ms", f'histogram_quantile(0.50,sum by(le)(rate(http_server_requests_seconds_bucket{{{JOB},{NOACT}}}[2m])))*1000')
stat("p95_ms", f'histogram_quantile(0.95,sum by(le)(rate(http_server_requests_seconds_bucket{{{JOB},{NOACT}}}[2m])))*1000')
stat("p99_ms", f'histogram_quantile(0.99,sum by(le)(rate(http_server_requests_seconds_bucket{{{JOB},{NOACT}}}[2m])))*1000')
stat("4xx_rate", f'sum(rate(http_server_requests_seconds_count{{{JOB},status=~"4.."}}[1m]))')
stat("5xx_rate", f'sum(rate(http_server_requests_seconds_count{{{JOB},status=~"5.."}}[1m]))')

print("\n=== OUTBOUND CALLS (Web3 RPC / AWS KMS·S3 / Gemini — http_client) ===")
print("# http_client_* 는 WebClient/RestClient/RestTemplate 이 Micrometer 로 계측될 때만 존재.")
print("# NO DATA 면 외부 호출이 미계측 — 쓰기/외부연동 테스트 전 instrumentation 필요(§3).")
stat("http_client_rps", f'sum(rate(http_client_requests_seconds_count{{{JOB}}}[1m]))')
stat("http_client_p95_ms", f'histogram_quantile(0.95,sum by(le)(rate(http_client_requests_seconds_bucket{{{JOB}}}[2m])))*1000')
stat("http_client_p99_ms", f'histogram_quantile(0.99,sum by(le)(rate(http_client_requests_seconds_bucket{{{JOB}}}[2m])))*1000')
stat("http_client_error_rate", f'sum(rate(http_client_requests_seconds_count{{{JOB},outcome=~"CLIENT_ERROR|SERVER_ERROR"}}[1m]))')

print("\n=== ASYNC EXECUTORS (TransactionIssuerWorker / 배치 스케줄러 등) ===")
print("# executor_* 는 ThreadPoolTaskExecutor 가 MeterRegistry 에 등록될 때만 존재.")
stat("executor_active_threads", f'sum(executor_active_threads{{{JOB}}})')
stat("executor_pool_size", f'sum(executor_pool_size_threads{{{JOB}}})')
stat("executor_queued_tasks", f'sum(executor_queued_tasks{{{JOB}}})')
stat("executor_completed_rate", f'sum(rate(executor_completed_tasks_total{{{JOB}}}[1m]))')

print("\n=== CPU ===")
stat("process_cpu_%", f'process_cpu_usage{{{JOB}}}*100')
stat("system_cpu_%", f'system_cpu_usage{{{JOB}}}*100')

print("\n=== HEAP / GC (leak signals) ===")
stat("heap_used_MB", f'sum(jvm_memory_used_bytes{{{JOB},area="heap"}})/1024/1024')
stat("oldgen_used_MB", f'sum(jvm_memory_used_bytes{{{JOB},area="heap",id=~".*Old Gen.*|.*Tenured.*"}})/1024/1024')
stat("gc_live_data_MB", f'jvm_gc_live_data_size_bytes{{{JOB}}}/1024/1024')
stat("gc_count_rate_/s", f'sum(rate(jvm_gc_pause_seconds_count{{{JOB}}}[5m]))')
stat("gc_time_fraction_/s", f'sum(rate(jvm_gc_pause_seconds_sum{{{JOB}}}[5m]))')
stat("gc_pause_max_s", f'max(jvm_gc_pause_seconds_max{{{JOB}}})')
stat("gc_count_cumulative", f'sum(jvm_gc_pause_seconds_count{{{JOB}}})')
stat("gc_time_cumulative_s", f'sum(jvm_gc_pause_seconds_sum{{{JOB}}})')

print("\n=== POOL / THREADS / FD (leak signals) ===")
stat("hikari_active", f'max_over_time(hikaricp_connections_active{{{JOB}}}[1m])')
stat("hikari_pending", f'max_over_time(hikaricp_connections_pending{{{JOB}}}[1m])')
stat("hikari_idle", f'min_over_time(hikaricp_connections_idle{{{JOB}}}[1m])')
stat("hikari_acquire_max_s", f'hikaricp_connections_acquire_seconds_max{{{JOB}}}')
stat("hikari_timeout_total", f'hikaricp_connections_timeout_total{{{JOB}}}')
stat("jvm_threads_live", f'jvm_threads_live_threads{{{JOB}}}')
stat("tomcat_busy", f'tomcat_threads_busy_threads{{{JOB}}}')
stat("tomcat_current", f'tomcat_threads_current_threads{{{JOB}}}')
stat("proc_open_fds", f'process_files_open_files{{{JOB}}}')

print("\n=== 라벨 분해 (cumulative) ===")
print("# GC action 별:")
discover("gc", f'jvm_gc_pause_seconds_count{{{JOB}}}', ["action", "cause"])
print("# 외부 클라이언트별 (있으면):")
discover("http_client", f'http_client_requests_seconds_count{{{JOB}}}',
         ["client_name", "uri", "status"])
print("# executor 별 (있으면):")
discover("executor", f'executor_completed_tasks_total{{{JOB}}}', ["name"])

print("\n# 끝. 차트 축은 위 min/max 로, drift 판정은 base/late 컬럼으로.")
