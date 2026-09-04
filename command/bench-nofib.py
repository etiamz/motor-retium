#!/usr/bin/python3

# $ black -l 80 command/bench-nofib.py

import json
import os
import platform
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import cpuinfo

type Seconds = float

type Timings = dict[str, Seconds]

type Benchmarks = dict[str, Timings]

type Command = list[str | Path]

ROOT = Path(__file__).parent.parent

NOFIB = ROOT / "nofib"

JAR = ROOT / "target" / "motor-retium.jar"


def openjdk() -> str:
    return java("JAVA_HOME")


def graalvm() -> str:
    return java("GRAALVM_HOME")


def java(home: str) -> str:
    path = os.environ.get(home)
    if path is None:
        sys.exit(f"`{home}` is not set")
    return str(Path(path) / "bin" / "java")


def main() -> None:
    json.dump(
        {
            "platform": platform.platform(),
            "cpu": cpu(),
            "cpus": os.cpu_count(),
            "memory": memory(),
            "openjdk": jvm_version(openjdk()),
            "graalvm": jvm_version(graalvm()),
            "ghc": ghc_version(),
            "data": benchmarks(),
        },
        sys.stdout,
        indent=4,
    )
    print()


def cpu() -> str:
    return cpuinfo.get_cpu_info()["brand_raw"]


def memory() -> str:
    # `sysconf` covers macOS and Linux, but is absent on Windows.
    if not hasattr(os, "sysconf"):
        return "unknown"
    total = os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE")
    return f"{total / 2**30:.1f} GiB"


def jvm_version(binary: str) -> str:
    return run([binary, "--version"]).splitlines()[2]


def ghc_version() -> str:
    return run(["ghc", "--version"]).splitlines()[0]


def benchmarks() -> Benchmarks:
    return {
        name(rete): timings(rete) for rete in sorted(NOFIB.glob("*/*.rete"))
    }


def timings(rete: Path) -> Timings:
    haskell = rete.with_suffix(".hs")
    return {
        "motor-retium OpenJDK G1": motor_openjdk_g1_gc(rete),
        "motor-retium OpenJDK ParallelGC": motor_openjdk_parallel_gc(rete),
        "motor-retium GraalVM G1": motor_graalvm_g1_gc(rete),
        "motor-retium GraalVM ParallelGC": motor_graalvm_parallel_gc(rete),
        "GHC -O0": ghc_o0(haskell),
        "GHC -O2": ghc_o2(haskell),
        "GHC bytecode": ghc_bytecode(haskell),
    }


def name(benchmark: Path) -> str:
    return str(benchmark.relative_to(NOFIB).with_suffix(""))


def motor_openjdk_g1_gc(rete: Path) -> Seconds:
    return motor(rete, openjdk(), "-XX:+UseG1GC")


def motor_openjdk_parallel_gc(rete: Path) -> Seconds:
    return motor(rete, openjdk(), "-XX:+UseParallelGC")


def motor_graalvm_g1_gc(rete: Path) -> Seconds:
    return motor(rete, graalvm(), "-XX:+UseG1GC")


def motor_graalvm_parallel_gc(rete: Path) -> Seconds:
    return motor(rete, graalvm(), "-XX:+UseParallelGC")


def motor(rete: Path, jvm: str, gc: str) -> Seconds:
    source = run(["cpp", "-traditional-cpp", "-P", rete])
    return measure([jvm, gc, "-jar", JAR], input=source)


def ghc_o0(haskell: Path) -> Seconds:
    return ghc(haskell, "-O0")


def ghc_o2(haskell: Path) -> Seconds:
    return ghc(haskell, "-O2")


def ghc(haskell: Path, level: str) -> Seconds:
    with tempfile.TemporaryDirectory() as directory:
        binary = Path(directory) / "benchmark"
        flags = [level, "-outputdir", directory, "-o", binary]
        run(["ghc", *flags, haskell])
        return measure([binary])


def ghc_bytecode(haskell: Path) -> Seconds:
    return measure(["runghc", "--", haskell])


def measure(command: Command, **kwargs) -> Seconds:
    print(f"Measuring `{show(command)}`...", file=sys.stderr)
    start = time.perf_counter()
    run(command, **kwargs)
    wall = round(time.perf_counter() - start, 2)  # seconds
    print(f"Wall: {wall:.2f} s", file=sys.stderr)
    return wall


def run(command: Command, **kwargs) -> str:
    process = subprocess.run(command, capture_output=True, text=True, **kwargs)
    if process.returncode != 0:
        sys.exit(
            f"`{show(command)}` exited with {process.returncode}: {process.stderr!r}"
        )
    return process.stdout


def show(command: Command) -> str:
    return " ".join(map(str, command))


if __name__ == "__main__":
    main()
