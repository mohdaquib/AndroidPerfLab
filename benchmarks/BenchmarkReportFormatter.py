#!/usr/bin/env python3
"""
Reads every *-benchmarkData.json produced by the Macrobenchmark suite and
writes a single GitHub-flavoured Markdown comment to stdout.

The comment contains:
  • SDK Init before/after table   (AppStartupBenchmark)
  • Scroll rendering before/after (ScrollBenchmark)
  • All startup modes summary     (StartupBenchmark)
  • Collapsible full-results dump

A hidden HTML marker (<!-- benchmark-report -->) at the top lets the
workflow upsert the comment instead of posting a duplicate on every push.

Usage:
    BENCHMARK_STATUS=success python3 benchmarks/BenchmarkReportFormatter.py
"""

import datetime
import glob
import json
import os
import sys

# ── CI gate thresholds — must stay in sync with the Kotlin source files ───────
TTID_GATE_MS      = 800.0   # AppStartupBenchmark.COLD_START_MAX_TTID_MS
FRAME_P99_GATE_MS = 16.0    # ScrollBenchmark.FRAME_P99_MAX_MS


# ── Data loading ──────────────────────────────────────────────────────────────

def load_benchmarks():
    """Return {test_name: {metric_key: {min, median, max}}} from all JSON files."""
    search_paths = [
        "benchmarks/build/outputs/connected_android_test_additional_output"
        "/**/*-benchmarkData.json",
        "**/*-benchmarkData.json",
    ]
    files = []
    for pattern in search_paths:
        files = glob.glob(pattern, recursive=True)
        if files:
            break

    result = {}
    for path in files:
        try:
            with open(path) as fh:
                payload = json.load(fh)
            for bench in payload.get("benchmarks", []):
                name = bench.get("name", "")
                result.setdefault(name, {})
                for m_key, m_vals in bench.get("metrics", {}).items():
                    result[name][m_key] = {
                        "min":    m_vals.get("minimum"),
                        "median": m_vals.get("median"),
                        "max":    m_vals.get("maximum"),
                    }
        except Exception as exc:
            print(f"warning: could not parse {path}: {exc}", file=sys.stderr)

    return result


# ── Formatting helpers ────────────────────────────────────────────────────────

def ms(val):
    return f"{val:.1f} ms" if val is not None else "—"


def pct(before, after):
    """Return '−82%' / '+5%' or '—' when inputs are unavailable."""
    if before is None or after is None or before == 0:
        return "—"
    delta = (after - before) / before * 100
    sign = "−" if delta < 0 else "+"
    return f"{sign}{abs(delta):.0f}%"


def gate(val, threshold):
    """Return ✅ / ❌ or — when no gate is defined for the metric."""
    if val is None or threshold is None:
        return "—"
    return "✅" if val < threshold else f"❌ {val:.0f} > {threshold:.0f} ms"


def get(data, test_name, metric_key, stat="median"):
    return data.get(test_name, {}).get(metric_key, {}).get(stat)


# ── Section renderers ─────────────────────────────────────────────────────────

def section_sdk_init(data):
    """AppStartupBenchmark: sync (baseline) vs async (optimised) cold start."""
    b_ttid = get(data, "startupCold_sdkAsyncInit_baseline",  "timeToInitialDisplayMs")
    o_ttid = get(data, "startupCold_sdkAsyncInit_optimized", "timeToInitialDisplayMs")
    b_ttfd = get(data, "startupCold_sdkAsyncInit_baseline",  "timeToFullDisplayMs")
    o_ttfd = get(data, "startupCold_sdkAsyncInit_optimized", "timeToFullDisplayMs")

    if all(v is None for v in [b_ttid, o_ttid, b_ttfd, o_ttfd]):
        return None

    return "\n".join([
        "### 🚀 SDK Init — Cold Start · 10 iterations",
        "",
        "| Metric | Before — sync, main thread | After — async, `Dispatchers.IO` | Δ | CI Gate |",
        "| :--- | ---: | ---: | ---: | :---: |",
        f"| TTID | {ms(b_ttid)} | {ms(o_ttid)} | {pct(b_ttid, o_ttid)} | {gate(o_ttid, TTID_GATE_MS)} |",
        f"| TTFD | {ms(b_ttfd)} | {ms(o_ttfd)} | {pct(b_ttfd, o_ttfd)} | — |",
        "",
        "_TTID = Time To Initial Display (first frame). "
        "TTFD = Time To Full Display (`reportFullyDrawn()`)._",
    ])


def section_scroll(data):
    """ScrollBenchmark: four Compose anti-patterns vs four fixes."""
    BEFORE = "scrollAnimatedList_unoptimized"
    AFTER  = "scrollAnimatedList_optimized"

    rows = [
        ("Frame p50", "frameDurationCpuMs_p50", None),
        ("Frame p90", "frameDurationCpuMs_p90", None),
        ("Frame p95", "frameDurationCpuMs_p95", None),
        ("Frame p99", "frameDurationCpuMs_p99", FRAME_P99_GATE_MS),
    ]

    if all(get(data, BEFORE, k) is None and get(data, AFTER, k) is None
           for _, k, _ in rows):
        return None

    lines = [
        "### 🎞 Compose Scroll Rendering — AnimatedListScreen · 5 iterations",
        "",
        "| Metric | Before — anti-patterns | After — optimized | Δ | CI Gate |",
        "| :--- | ---: | ---: | ---: | :---: |",
    ]
    for label, key, threshold in rows:
        b_val = get(data, BEFORE, key)
        a_val = get(data, AFTER,  key)
        lines.append(
            f"| {label} | {ms(b_val)} | {ms(a_val)} | {pct(b_val, a_val)} | {gate(a_val, threshold)} |"
        )

    lines += [
        "",
        "_Median of per-frame CPU render time. p99 gate = 16 ms (60 fps budget)._",
    ]
    return "\n".join(lines)


def section_startup_modes(data):
    """StartupBenchmark: cold / warm / hot in a single table."""
    modes = [
        ("Cold", "startupCold"),
        ("Warm", "startupWarm"),
        ("Hot",  "startupHot"),
    ]
    if all(get(data, name, "timeToInitialDisplayMs") is None for _, name in modes):
        return None

    lines = [
        "### ⏱ All Startup Modes · 10 iterations",
        "",
        "| Mode | Process | Activity | TTID (median) | TTFD (median) |",
        "| :--- | :--- | :--- | ---: | ---: |",
        f"| Cold | killed | gone | {ms(get(data, 'startupCold', 'timeToInitialDisplayMs'))} | {ms(get(data, 'startupCold', 'timeToFullDisplayMs'))} |",
        f"| Warm | alive | gone | {ms(get(data, 'startupWarm', 'timeToInitialDisplayMs'))} | {ms(get(data, 'startupWarm', 'timeToFullDisplayMs'))} |",
        f"| Hot  | alive | alive | {ms(get(data, 'startupHot',  'timeToInitialDisplayMs'))} | — |",
    ]
    return "\n".join(lines)


def section_full_table(data):
    """Collapsible dump of every metric from every benchmark."""
    rows = []
    for test in sorted(data):
        for metric in sorted(data[test]):
            v = data[test][metric]
            rows.append(
                f"| `{test}` · `{metric}` "
                f"| {ms(v['min'])} | {ms(v['median'])} | {ms(v['max'])} |"
            )

    if not rows:
        return None

    return "\n".join([
        "<details>",
        "<summary>Full results — all benchmarks and metrics</summary>",
        "",
        "| Benchmark · Metric | Min | Median | Max |",
        "| :--- | ---: | ---: | ---: |",
        *rows,
        "",
        "</details>",
    ])


# ── Entry point ───────────────────────────────────────────────────────────────

def main():
    sha       = (os.environ.get("GITHUB_SHA", "") or "")[:7] or "unknown"
    run_id    = os.environ.get("GITHUB_RUN_ID", "")
    repo      = os.environ.get("GITHUB_REPOSITORY", "")
    status    = os.environ.get("BENCHMARK_STATUS", "")   # "success" | "failure" | ""
    timestamp = datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M UTC")

    run_url = (
        f"https://github.com/{repo}/actions/runs/{run_id}"
        if repo and run_id else ""
    )

    status_note = " — ⚠️ run failed, results may be incomplete" if status == "failure" else ""

    lines = [
        "<!-- benchmark-report -->",
        f"## 📊 Benchmark Report — `{sha}` → `main`{status_note}",
        "",
        f"> Android 14 (API 34) · x86\\_64 emulator · `CompilationMode.None()` · {timestamp}",
        "",
    ]

    data = load_benchmarks()

    if not data:
        lines += [
            "> ⚠️ No benchmark JSON files found.",
            "> The emulator run may have failed before any results were written.",
            "",
        ]
        if run_url:
            lines.append(f"[View workflow run ↗]({run_url})")
        print("\n".join(lines))
        return

    sections = [
        section_sdk_init(data),
        section_scroll(data),
        section_startup_modes(data),
    ]

    for section in sections:
        if section:
            lines += ["---", "", section, ""]

    full = section_full_table(data)
    if full:
        lines += ["---", "", full, ""]

    footer = (
        f"> 🤖 [Benchmark Report workflow]({run_url})"
        if run_url else "> 🤖 Benchmark Report"
    )
    lines.append(footer)

    print("\n".join(lines))


if __name__ == "__main__":
    main()
