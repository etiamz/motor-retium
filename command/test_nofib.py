#!/usr/bin/env python3

# $ black -l 80 command/test_nofib.py

import subprocess
import sys
import tempfile
from pathlib import Path

from helper.java import openjdk

# The name of a macro of a `.rete`, e.g. `N`.
type _MacroName = str

# The value of a macro, e.g. `150`.
type _Value = str

# The `cpp` input to a `.rete` program.
type _Input = dict[_MacroName, _Value]

# The system command to execute with `run`.
type Command = list[str | Path]

_ROOT = Path(__file__).parent.parent

NOFIB = _ROOT / "nofib"

JAR = _ROOT / "target" / "motor-retium.jar"

_ELLIPSIS = 240  # characters

_INPUTS: dict[str, list[_Input]] = {
    "imaginary/peano-exponentiation": [{}, {"N": "1"}, {"N": "2"}, {"N": "5"}],
    "imaginary/primes": [{}, {"N": "3"}, {"N": "10"}, {"N": "100"}],
    "imaginary/queens": [{}, {"N": "1"}, {"N": "3"}, {"N": "5"}],
    "imaginary/takeuchi": [
        {},
        {"X": "5", "Y": "3", "Z": "1"},
        {"X": "12", "Y": "8", "Z": "4"},
        {"X": "24", "Y": "12", "Z": "6"},
    ],
    "parallel/coins": [{}, {"N": "0"}, {"N": "37"}, {"N": "250"}],
    "parallel/fibonacci": [{}, {"N": "0"}, {"N": "1"}, {"N": "15"}],
    "parallel/matrix-multiplication": [{}, {"N": "1"}, {"N": "7"}, {"N": "30"}],
    "spectral/gcd": [{}, {"D": "0"}, {"D": "3"}, {"D": "25"}],
    "spectral/lcss": [
        {},
        {"A": "1", "B": "2", "C": "10", "D": "5", "E": "6", "F": "15"},
        {"A": "2", "B": "4", "C": "60", "D": "1", "E": "2", "F": "30"},
        {"A": "1", "B": "2", "C": "100", "D": "50", "E": "51", "F": "150"},
    ],
    "spectral/life": [{}, {"N": "3"}, {"N": "10"}, {"N": "15"}],
}


def main() -> None:
    nfailures = 0
    for test, inputs in _INPUTS.items():
        rete = (NOFIB / test).with_suffix(".rete")
        haskell = (NOFIB / test).with_suffix(".hs")
        for input in inputs:
            print(f"Testing `{test}` on `{input}`...", file=sys.stderr)
            expected = _run_ghc(haskell, input)
            got = _run_motor(rete, input)
            if got != expected:
                print(
                    f"Expected {_abbreviate(expected)}, got {_abbreviate(got)}.",
                    file=sys.stderr,
                )
                nfailures += 1
            else:
                print("Good.", file=sys.stderr)
    if nfailures > 0:
        sys.exit(f"{nfailures} test(s) failed")
    print("All tests passed.", file=sys.stderr)


def _run_motor(rete: Path, input: _Input) -> str:
    defs = [f"-D{name}={value}" for name, value in input.items()]
    source = run(["cpp", "-traditional-cpp", "-P", *defs, rete])
    return run([openjdk(), "-jar", JAR], input=source)


def _run_ghc(haskell: Path, input: _Input) -> str:
    with tempfile.TemporaryDirectory() as directory:
        binary = Path(directory) / "test"
        flags = ["-O2", "-outputdir", directory, "-o", binary]
        # Compile to native; bytecode interpretation is too slow.
        run(["ghc", *flags, haskell])
        return run([binary, *input.values()])


def _abbreviate(output: str) -> str:
    if len(output) <= _ELLIPSIS:
        return repr(output)
    truncated = output[:_ELLIPSIS]
    nremaining = len(output) - _ELLIPSIS
    return f"{truncated!r} (and {nremaining} more characters)"


def run(command: Command, **kwargs) -> str:
    process = subprocess.run(command, capture_output=True, text=True, **kwargs)
    if process.returncode != 0:
        sys.exit(
            f"`{show_command(command)}` exited with {process.returncode}: {process.stderr!r}"
        )
    return process.stdout


def show_command(command: Command) -> str:
    return " ".join(map(str, command))


if __name__ == "__main__":
    main()
