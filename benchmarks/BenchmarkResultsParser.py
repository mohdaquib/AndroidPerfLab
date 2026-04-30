#!/usr/bin/env python3
import json
import glob
import os

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

    print("| Metric | Min | Median | Max |")
    print("| :--- | :---: | :---: | :---: |")

    # Track metrics to avoid duplicates if multiple files are found
    seen_results = set()

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

                    # Some metrics might be in nested objects depending on version
                    # but usually minimum/median/maximum are at the top level of the metric object

                    display_name = f"{benchmark_name}_{metric_name}"
                    result_row = (display_name, m_min, m_median, m_max)

                    if result_row not in seen_results:
                        print(f"| {display_name} | {format_value(m_min)} | {format_value(m_median)} | {format_value(m_max)} |")
                        seen_results.add(result_row)
        except Exception as e:
            # Print error to stderr so it doesn't mess up the markdown table on stdout
            import sys
            print(f"Error parsing {file_path}: {e}", file=sys.stderr)

if __name__ == "__main__":
    main()
