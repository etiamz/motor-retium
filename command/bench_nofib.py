#!/usr/bin/python3

# $ black -l 80 command/bench_nofib.py

import json
import os
import platform
import sys
import tempfile
import time
from pathlib import Path

import cpuinfo

from helper.java import graalvm, openjdk
from test_nofib import JAR, NOFIB, Command, run, show_command

type _Seconds = float

type _Timings = dict[str, _Seconds]

type _Benchmarks = dict[str, _Timings]


def main() -> None:
    output = {
        "platform": platform.platform(),
        "cpu": _cpu(),
        "cpus": os.cpu_count(),
        "memory": _memory(),
        "openjdk": _jvm_version(openjdk()),
        "graalvm": _jvm_version(graalvm()),
        "ghc": _ghc_version(),
        "data": _benchmarks(),
    }
    json.dump(output, sys.stdout, indent=4)
    print()


def _cpu() -> str:
    return cpuinfo.get_cpu_info()["brand_raw"]


def _memory() -> str:
    # `sysconf` covers macOS and Linux, but is absent on Windows.
    if not hasattr(os, "sysconf"):
        return "unknown"
    total = os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE")
    return f"{total / 2**30:.1f} GiB"


def _jvm_version(binary: str) -> str:
    return run([binary, "--version"]).splitlines()[2]


def _ghc_version() -> str:
    return run(["ghc", "--version"]).splitlines()[0]


def _benchmarks() -> _Benchmarks:
    return {
        _name(rete): _timings(rete) for rete in sorted(NOFIB.glob("*/*.rete"))
    }


def _timings(rete: Path) -> _Timings:
    haskell = rete.with_suffix(".hs")
    return {
        "motor-retium OpenJDK G1": _motor_openjdk_g1_gc(rete),
        "motor-retium OpenJDK ParallelGC": _motor_openjdk_parallel_gc(rete),
        "motor-retium GraalVM G1": _motor_graalvm_g1_gc(rete),
        "motor-retium GraalVM ParallelGC": _motor_graalvm_parallel_gc(rete),
        "GHC -O0": _ghc_o0(haskell),
        "GHC -O2": _ghc_o2(haskell),
        "GHC bytecode": _ghc_bytecode(haskell),
    }


def _name(benchmark: Path) -> str:
    return str(benchmark.relative_to(NOFIB).with_suffix(""))


def _motor_openjdk_g1_gc(rete: Path) -> _Seconds:
    return _motor(rete, openjdk(), "-XX:+UseG1GC")


def _motor_openjdk_parallel_gc(rete: Path) -> _Seconds:
    return _motor(rete, openjdk(), "-XX:+UseParallelGC")


def _motor_graalvm_g1_gc(rete: Path) -> _Seconds:
    return _motor(rete, graalvm(), "-XX:+UseG1GC")


def _motor_graalvm_parallel_gc(rete: Path) -> _Seconds:
    return _motor(rete, graalvm(), "-XX:+UseParallelGC")


def _motor(rete: Path, jvm: str, gc: str) -> _Seconds:
    source = run(["cpp", "-traditional-cpp", "-P", rete])
    return _measure([jvm, gc, "-jar", JAR], input=source)


def _ghc_o0(haskell: Path) -> _Seconds:
    return _ghc(haskell, "-O0")


def _ghc_o2(haskell: Path) -> _Seconds:
    return _ghc(haskell, "-O2")


def _ghc(haskell: Path, level: str) -> _Seconds:
    with tempfile.TemporaryDirectory() as directory:
        binary = Path(directory) / "benchmark"
        flags = [level, "-outputdir", directory, "-o", binary]
        run(["ghc", *flags, haskell])
        return _measure([binary])


def _ghc_bytecode(haskell: Path) -> _Seconds:
    return _measure(["runghc", "--", haskell])


def _measure(command: Command, **kwargs) -> _Seconds:
    print(f"Measuring `{show_command(command)}`...", file=sys.stderr)
    start = time.perf_counter()
    run(command, **kwargs)
    wall = round(time.perf_counter() - start, 2)  # seconds
    print(f"Wall: {wall:.2f} s", file=sys.stderr)
    return wall


if __name__ == "__main__":
    main()
