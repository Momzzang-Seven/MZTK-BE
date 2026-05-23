#!/usr/bin/env python3
"""부하테스트 결과 -> SVG 차트 생성 (config-JSON 구동 · 차트 목록 완전 일반화).

config 의 "charts" 배열을 순서대로 그린다. 각 series 는 PromQL 식이거나
"@vu"(k6 stage 에서 유도한 VU 곡선)다. 읽기·쓰기·외부호출(Web3/AWS/Gemini) 어떤
테스트든 charts 배열만 바꾸면 된다 — 스크립트는 테스트 종류를 모른다.

표준 차트 정의는 references/chart-palette.json 에서 골라 charts 에 넣는다.
config / chart 필드 정의는 references/metrics-and-judgment.md §4 참조.

사용법:
    python3 make_charts.py <chart_config.json>
"""
import json
import math
import os
import sys
import urllib.parse
import urllib.request

if len(sys.argv) != 2:
    sys.exit("usage: python3 make_charts.py <chart_config.json>")

with open(sys.argv[1], encoding="utf-8") as f:
    CFG = json.load(f)

PROM = CFG.get("prometheus", "http://localhost:9090")
START = CFG["start"]
END = CFG["end"]
STEP = CFG.get("step", 60)
OUTDIR = CFG["outdir"]
NAME = CFG["name"]  # 파일 접두사 — <name>_<chart.file>.svg


def query_range(expr):
    qs = urllib.parse.urlencode(
        {"query": expr, "start": START, "end": END, "step": STEP})
    with urllib.request.urlopen(f"{PROM}/api/v1/query_range?{qs}", timeout=30) as r:
        data = json.load(r)
    res = data["data"]["result"]
    if not res:
        return []
    out = []
    for _, val in res[0]["values"]:
        try:
            v = float(val)
            if math.isnan(v) or math.isinf(v):
                v = None
        except ValueError:
            v = None
        out.append(v)
    return out


def vu_at(i):
    """stage_vu : [[start_i, start_vu, end_i, end_vu], ...] — 선형 보간으로 VU 추정."""
    for a, va, b, vb in CFG["stage_vu"]:
        if a <= i <= b:
            return va + (vb - va) * (i - a) / (b - a)
    return CFG["stage_vu"][-1][3]


def fmt_num(v):
    if abs(v - round(v)) < 1e-9:
        return str(int(round(v)))
    return f"{v:.1f}"


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def line_chart(filename, title, ylabel, series, npoints,
               ymax, yticks=5, refline=None, annotations=None):
    """series: [{label,color,data:[v|None]}]  refline: (value,label)
       annotations: [(index, value, text)]"""
    W, H = 880, 380
    ml, mr, mt, mb = 66, 48, 60, 66
    pw, ph = W - ml - mr, H - mt - mb
    font = "-apple-system,'Segoe UI',Roboto,'Helvetica Neue',sans-serif"

    def px(i):
        return ml + pw * (i / (npoints - 1) if npoints > 1 else 0)

    def py(v):
        return mt + ph * (1 - v / ymax)

    s = []
    s.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" '
             f'height="{H}" viewBox="0 0 {W} {H}" font-family="{font}">')
    s.append(f'<rect width="{W}" height="{H}" fill="#ffffff"/>')
    s.append(f'<text x="{ml}" y="28" font-size="16" font-weight="700" '
             f'fill="#16181d">{esc(title)}</text>')

    # y gridlines + labels
    for k in range(yticks + 1):
        gv = ymax * k / yticks
        gy = py(gv)
        s.append(f'<line x1="{ml}" y1="{gy:.1f}" x2="{ml+pw}" y2="{gy:.1f}" '
                 f'stroke="#ebedf0" stroke-width="1"/>')
        s.append(f'<text x="{ml-9}" y="{gy+4:.1f}" font-size="11" '
                 f'text-anchor="end" fill="#8b9099">{fmt_num(gv)}</text>')
    cy = mt + ph / 2
    s.append(f'<text x="17" y="{cy:.0f}" font-size="11" fill="#8b9099" '
             f'text-anchor="middle" transform="rotate(-90 17 {cy:.0f})">'
             f'{esc(ylabel)}</text>')

    # x ticks at stage boundaries / time markers
    xticks = [t for t in CFG["xticks"] if t < npoints]
    bh, bm = CFG["clock0"]
    for ti, i in enumerate(xticks):
        xx = px(i)
        s.append(f'<line x1="{xx:.1f}" y1="{mt}" x2="{xx:.1f}" '
                 f'y2="{mt+ph}" stroke="#dfe2e6" stroke-dasharray="3 3"/>')
        hh, mm = divmod(bm + i, 60)
        clock = f"{bh+hh}:{mm:02d}"
        anchor = ("start" if ti == 0
                  else "end" if ti == len(xticks) - 1 else "middle")
        for j, txt in enumerate((clock, f"{int(round(vu_at(i)))} VU")):
            s.append(f'<text x="{xx:.1f}" y="{mt+ph+18+j*13}" font-size="10" '
                     f'text-anchor="{anchor}" '
                     f'fill="{"#666b73" if j==0 else "#a0a4ab"}">{txt}</text>')

    # axes
    s.append(f'<line x1="{ml}" y1="{mt+ph}" x2="{ml+pw}" y2="{mt+ph}" '
             f'stroke="#c4c8ce" stroke-width="1.5"/>')
    s.append(f'<line x1="{ml}" y1="{mt}" x2="{ml}" y2="{mt+ph}" '
             f'stroke="#c4c8ce" stroke-width="1.5"/>')

    if refline:
        rv, rlabel = refline
        ry = py(rv)
        s.append(f'<line x1="{ml}" y1="{ry:.1f}" x2="{ml+pw}" y2="{ry:.1f}" '
                 f'stroke="#d64545" stroke-width="1.2" stroke-dasharray="6 3"/>')
        s.append(f'<text x="{ml+pw-4}" y="{ry-6:.1f}" font-size="10" '
                 f'text-anchor="end" fill="#d64545">{esc(rlabel)}</text>')

    # series
    for sr in series:
        seg, segs = [], []
        for i, v in enumerate(sr["data"]):
            if v is None:
                if len(seg) > 1:
                    segs.append(seg)
                seg = []
            else:
                seg.append((px(i), py(v)))
        if len(seg) > 1:
            segs.append(seg)
        for pts in segs:
            d = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
            s.append(f'<polyline points="{d}" fill="none" '
                     f'stroke="{sr["color"]}" stroke-width="2.2" '
                     f'stroke-linejoin="round"/>')
        for i, v in enumerate(sr["data"]):
            if v is not None:
                s.append(f'<circle cx="{px(i):.1f}" cy="{py(v):.1f}" r="2.4" '
                         f'fill="{sr["color"]}"/>')

    if annotations:
        for idx, val, txt in annotations:
            ax, ay = px(idx), py(val)
            s.append(f'<circle cx="{ax:.1f}" cy="{ay:.1f}" r="4.5" '
                     f'fill="none" stroke="#d64545" stroke-width="1.6"/>')
            s.append(f'<text x="{ax+8:.1f}" y="{ay+3:.1f}" font-size="10.5" '
                     f'fill="#d64545" font-weight="600">{esc(txt)}</text>')

    # legend
    lx = ml
    for sr in series:
        s.append(f'<line x1="{lx}" y1="44" x2="{lx+22}" y2="44" '
                 f'stroke="{sr["color"]}" stroke-width="2.6"/>')
        s.append(f'<circle cx="{lx+11}" cy="44" r="2.6" fill="{sr["color"]}"/>')
        s.append(f'<text x="{lx+28}" y="48" font-size="11.5" fill="#4a4f57">'
                 f'{esc(sr["label"])}</text>')
        lx += 34 + len(sr["label"]) * 7.2

    s.append('</svg>')
    os.makedirs(OUTDIR, exist_ok=True)
    path = os.path.join(OUTDIR, filename)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(s))
    print(f"  wrote {path}")


def resolve_series(chart):
    """chart['series'] 의 query 를 실제 데이터로. '@vu' 는 유도 VU 곡선."""
    rows, n = [], 0
    for sr in chart["series"]:
        data = None if sr["query"] == "@vu" else query_range(sr["query"])
        if data is not None:
            n = max(n, len(data))
        rows.append((sr, data))
    out = []
    for sr, data in rows:
        if data is None:
            data = [vu_at(i) for i in range(n)]
        out.append({"label": sr["label"], "color": sr["color"], "data": data})
    return out, n


def main():
    charts = CFG["charts"]
    print(f"querying Prometheus {START} ~ {END}  ({len(charts)} charts) ...")
    for ch in charts:
        series, n = resolve_series(ch)
        if n == 0:
            print(f"  SKIP {ch['file']}: Prometheus 가 데이터를 반환하지 않음 "
                  f"(쿼리/구간/instrumentation 확인)")
            continue
        ann = None
        am = ch.get("annotate_max")
        if am:
            s = series[am["series"]]["data"]
            idx = max(range(len(s)), key=lambda i: (s[i] or 0))
            if s[idx] is not None:
                ann = [(idx, s[idx],
                        am["label"].replace("{v}", str(int(s[idx]))))]
        refline = tuple(ch["refline"]) if ch.get("refline") else None
        line_chart(f"{NAME}_{ch['file']}.svg", ch["title"], ch["ylabel"],
                   series, n, ymax=ch["ymax"], yticks=ch.get("yticks", 5),
                   refline=refline, annotations=ann)
    print("done.")


if __name__ == "__main__":
    main()
