# $ black -l 80 command/helper/java.py

import os
import sys
from pathlib import Path


def openjdk() -> str:
    return _java("JAVA_HOME")


def graalvm() -> str:
    return _java("GRAALVM_HOME")


def _java(home: str) -> str:
    path = os.environ.get(home)
    if path is None:
        sys.exit(f"`{home}` is not set")
    return str(Path(path) / "bin" / "java")
