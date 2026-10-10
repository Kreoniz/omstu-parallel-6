#!/bin/sh
set -eu
cd "$(dirname "$0")"
rm -rf build/classes
mkdir -p build/classes
javac -encoding UTF-8 -Xlint:all -d build/classes src/lab2/Main.java
