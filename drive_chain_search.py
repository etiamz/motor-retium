#!/usr/bin/python3

# $ black -l 80 drive_chain_search.py

import os
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).parent

PANIC_PREFIX = "Panic: User panic: "

JAVA_FLAGS = ["-XX:+UseParallelGC"]


# Prefer GraalVM for speed; if absent, use the default JVM.
def java() -> str:
    home = os.environ.get("GRAALVM_HOME") or os.environ.get("JAVA_HOME")
    if home is None:
        sys.exit("Neither `GRAALVM_HOME` nor `JAVA_HOME` is set")
    return str(Path(home) / "bin" / "java")


def main() -> None:
    target = int(sys.argv[1])
    if target < 1:
        sys.exit(f"Expected a positive target, got {target}")
    level = (target - 1).bit_length()  # the initial level
    while (message := run(target, level)) is None:
        print(f"Level {level} exhausted.", file=sys.stderr)
        level += 1
    render(message)


def run(target: int, level: int) -> str | None:
    program = subprocess.run(
        [
            "cpp",
            "-traditional-cpp",
            "-P",
            f"-DTARGET={target}",
            f"-DLEVEL={level}",
            HERE / "chain-search.rete",
        ],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    engine = subprocess.run(
        [java(), *JAVA_FLAGS, "-jar", HERE / "target" / "motor-retium.jar"],
        input=program,
        capture_output=True,
        text=True,
    )
    if engine.stderr.startswith(PANIC_PREFIX):
        return engine.stderr[len(PANIC_PREFIX) :]
    if engine.stdout.strip() == '"No solution"':
        return None
    sys.exit(
        f"stderr: {engine.stderr!r}\n"
        f"stdout: {engine.stdout!r}",
    )  # fmt: skip


class Found(Exception):
    pass


def render(message: str) -> None:
    line, _, _ = message.partition("\n")
    chain = [int(x) for x in line.strip('"').split()]
    assert chain == sorted(set(chain))
    lines = [str(chain[0])]
    for k in range(1, len(chain)):
        n = chain[k]
        try:
            for a in chain[:k]:
                for b in chain[:k]:
                    if a <= b and a + b == n:
                        raise Found(f"{n} = {a} + {b}")
            sys.exit(f"{n} is not a sum of two earlier elements")
        except Found as found:
            lines.append(str(found))
    print("\n".join(lines))


if __name__ == "__main__":
    main()
