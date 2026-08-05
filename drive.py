#!/usr/bin/python3

# $ black -l 80 drive.py

import math
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).parent

PANIC_PREFIX = "Panic: User panic: "


def main() -> None:
    target = int(sys.argv[1])
    level = math.ceil(math.log2(target))
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
            f"-Dtarget={target}u64",
            f"-Dlevel={level}u64",
            HERE / "search.rete",
        ],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    engine = subprocess.run(
        ["java", "-jar", HERE / "target" / "motor-retium.jar"],
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
