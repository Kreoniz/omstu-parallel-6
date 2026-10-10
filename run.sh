#!/bin/sh
set -eu
cd "$(dirname "$0")"
case "${1:-}" in
    1) main_class=lab1.WordCounter ;;
    2|3|4|5|6) main_class="lab$1.Main" ;;
    *) echo 'Использование: ./run.sh <1..6> [путь] [число потоков для 5/6]' >&2; exit 2 ;;
esac
shift
./build.sh
exec java -cp build/classes "$main_class" "$@"
