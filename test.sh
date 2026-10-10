#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
mkdir -p build/tests
javac -encoding UTF-8 -Xlint:all -cp build/classes -d build/tests tests/*.java
java -cp build/classes:build/tests IndexTest
java -cp build/classes:build/tests IntegrationTest
