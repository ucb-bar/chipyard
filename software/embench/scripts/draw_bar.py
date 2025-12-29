#!/usr/bin/env python3
"""
Generate a stacked bar chart summarising control-flow instruction counts per benchmark.
"""

import re
from pathlib import Path

import matplotlib.pyplot as plt


LINE_RE = re.compile(r"^(.*?):\s+(\d+)\s*$")


def parse_counts(path: Path) -> dict[str, int]:
    data: dict[str, int] = {}
    for raw_line in path.read_text().splitlines():
        match = LINE_RE.match(raw_line)
        if not match:
            continue
        key = match.group(1).strip().lower()
        value = int(match.group(2))
        data[key] = value
    required_keys = {"total control flow change counts", "branch counts", "ij counts", "uj counts"}
    missing = required_keys - data.keys()
    if missing:
        raise ValueError(f"{path.name} is missing keys: {', '.join(sorted(missing))}")
    return data


def main() -> None:
    script_dir = Path(__file__).resolve().parent
    results_dir = script_dir.parent / "results"
    result_files = sorted(results_dir.glob("*.txt"))
    if not result_files:
        raise SystemExit(f"No .txt result files found in {results_dir}")

    benchmarks: list[str] = []
    branch_pct: list[float] = []
    ij_pct: list[float] = []
    uj_pct: list[float] = []

    for result_file in result_files:
        counts = parse_counts(result_file)
        branch = counts["branch counts"]
        ij = counts["ij counts"]
        uj = counts["uj counts"]
        total = branch + ij + uj
        if total == 0:
            raise ValueError(f"{result_file.name} reports zero aggregated control-flow counts")
        benchmarks.append(result_file.stem)
        branch_pct.append((branch / total) * 100.0)
        ij_pct.append((ij / total) * 100.0)
        uj_pct.append((uj / total) * 100.0)

    indices = range(len(benchmarks))
    branch_array = branch_pct
    ij_array = ij_pct
    uj_array = uj_pct

    plt.rc('font', size = 14)
    fig, ax = plt.subplots(figsize=(max(6, len(benchmarks) * 0.5), 6))

    ax.bar(indices, branch_array, label="Branch")
    ax.bar(indices, ij_array, bottom=branch_array, label="Inferable Jumps")
    cumulative = [b + i for b, i in zip(branch_array, ij_array)]
    ax.bar(indices, uj_array, bottom=cumulative, label="Uninferable Jumps")

    ax.set_xticks(list(indices))
    ax.set_xticklabels(benchmarks, rotation=45, ha="right")
    ax.set_ylabel("Percentage of control-flow instructions")
    ax.set_title("Control-flow instruction breakdown per benchmark")
    ax.set_ylim(0, 100)
    ax.legend()
    ax.margins(x=0.01)
    fig.tight_layout()

    output_path = results_dir / "control_flow_counts.png"
    fig.savefig(output_path, dpi=300)
    plt.close(fig)
    print(f"Saved stacked bar chart to {output_path}")


if __name__ == "__main__":
    main()
