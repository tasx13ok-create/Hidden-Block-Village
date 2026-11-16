#!/usr/bin/env python3
"""Build Dungeon Guard with Java 25 for Gradle and Java 8 for game bytecode.

Example:
  python tools/build.py --java25 /path/to/jdk25 --java8 /path/to/jdk8

The Gradle wrapper downloads the checksum-pinned Gradle distribution. No game
or Forge dependency is packaged into the resulting addon.
"""
import argparse
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java25', type=Path, required=True)
    parser.add_argument('--java8', type=Path, required=True)
    parser.add_argument('--native-tests', action='store_true')
    parser.add_argument('--actual-mod-tests', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    for home in (args.java25, args.java8):
        javac = home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
        if not javac.is_file():
            parser.error('A complete JDK is required: ' + str(home))
    env = os.environ.copy()
    env['JAVA_HOME'] = str(args.java25.resolve())
    command = [str(root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')),
               '-Porg.gradle.java.installations.paths=' + str(args.java8.resolve()),
               'check', 'reobfJar', 'sourcesJar']
    if args.native_tests:
        command.append('reobfNativeTestJar')
    if args.actual_mod_tests:
        command.append('reobfActualModTestJar')
    return subprocess.call(command, cwd=root, env=env)


if __name__ == '__main__':
    raise SystemExit(main())
