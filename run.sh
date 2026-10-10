#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
exec java -cp build/classes lab2.Main "$@"
