#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes
find src -name '*.java' -type f | sort > build/sources.txt
javac -encoding UTF-8 -Xlint:all -d build/classes @build/sources.txt
