#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ "${1:-}" = "2" ]; then
    shift
    if [ "$#" -eq 0 ]; then
        exec ./run-spring.sh
    fi
fi
./build.sh
exec java -cp build/classes lab2.Main "$@"
