#!/usr/bin/env python3
import json
import glob
import sys

COLD_START_THRESHOLD_MS = 800
FRAME_P99_THRESHOLD_MS  = 16.0

def format_value(val):
    if val is None:
        return "-"
    if isinstance(val, (int, float)):
        return f"{val:.2f}"
    return str(val)

def main():
    # Search for benchmark data files in the standard output directory
    search_path = 'benchmarks/build/outputs/connected_android_test_additional_output/**/*-benchmarkData.json'
    files = glob.glob(search_path, recursive=True)

    if not files:
        # Fallback to search from current directory
        files = glob.glob('**/*-benchmarkData.json', recursive=True)

    if not files:
        print("No benchmark results found.")
        return

    print("| Metric | Min | Median | Max | Status |")
    print("| :--- | :---: | :---: | :---: | :---: |")

    seen_results = set()
    threshold_exceeded = False

    for file_path in files:
        try:
            with open(file_path, 'r') as f:
                data = json.load(f)

            if 'benchmarks' not in data:
                continue

            for benchmark in data['benchmarks']:
                benchmark_name = benchmark.get('name', 'Unknown')
                metrics = benchmark.get('metrics', {})

                for metric_name, values in metrics.items():
                    m_min = values.get('minimum')
                    m_median = values.get('median')
                    m_max = values.get('maximum')

                    # Cold-start TTID threshold: applies to any cold benchmark row.
                    is_cold_ttid = (
                        "cold" in benchmark_name.lower() and
                        metric_name == "timeToInitialDisplayMs"
                    )

                    # p99 frame-time threshold: applies only to the optimized scroll test
                    # so that the intentionally-slow unoptimized test does not fail CI.
                    is_p99_frame_optimized = (
                        "optimized" in benchmark_name.lower() and
                        "p99" in metric_name.lower() and
                        "frameduration" in metric_name.lower().replace(".", "").replace("_", "")
                    )

                    if is_cold_ttid and m_median is not None:
                        if m_median >= COLD_START_THRESHOLD_MS:
                            status = f"FAIL (>{COLD_START_THRESHOLD_MS}ms)"
                            threshold_exceeded = True
                        else:
                            status = "PASS"
                    elif is_p99_frame_optimized and m_median is not None:
                        if m_median >= FRAME_P99_THRESHOLD_MS:
                            status = f"FAIL (>{FRAME_P99_THRESHOLD_MS}ms)"
                            threshold_exceeded = True
                        else:
                            status = "PASS"
                    else:
                        status = "-"

                    display_name = f"{benchmark_name}_{metric_name}"
                    result_row = (display_name, m_min, m_median, m_max)

                    if result_row not in seen_results:
                        print(
                            f"| {display_name} | {format_value(m_min)} | "
                            f"{format_value(m_median)} | {format_value(m_max)} | {status} |"
                        )
                        seen_results.add(result_row)
        except Exception as e:
            print(f"Error parsing {file_path}: {e}", file=sys.stderr)

    if threshold_exceeded:
        print(
            "\n> One or more thresholds exceeded "
            f"(cold-start TTID > {COLD_START_THRESHOLD_MS}ms or "
            f"optimized p99 frame time > {FRAME_P99_THRESHOLD_MS}ms). "
            "See FAIL rows above.",
            file=sys.stderr,
        )
        sys.exit(1)

if __name__ == "__main__":
    main()
