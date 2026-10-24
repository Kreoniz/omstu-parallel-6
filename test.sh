#!/bin/sh
set -eu
cd "$(dirname "$0")"
lab=${1:-2}
case "$lab" in
    2) classes=build/classes ;;
    3|4|5|6) classes=build/lab$lab ;;
    *) echo 'Номер лабораторной должен быть от 2 до 6' >&2; exit 1 ;;
esac
test_dir=$(mktemp -d "build/lab$lab-test.XXXXXX")
trap 'rm -rf "$test_dir"' EXIT HUP INT TERM

run_lab() {
    timeout 60s java -cp "$classes" "lab$lab.Main" "$@"
}

if [ "$lab" = 3 ]; then
    cat > "$test_dir/expected.txt" <<'EOF'
FILE Base.java
Base ->
FILE Document.java
Attachment ->
Base -> Attachment, Document
Document ->
Identifiable -> Document
Writable -> Document
FILE FeaturedDocument.java
Document -> FeaturedDocument
FeaturedDocument ->
FILE Identifiable.java
Identifiable ->
FILE Readable.java
Readable ->
FILE Writable.java
Readable -> Writable
Writable ->
EOF
else
    cat > "$test_dir/expected.txt" <<'EOF'
Attachment ->
Base -> Attachment, Document
Document -> FeaturedDocument
FeaturedDocument ->
Identifiable -> Document
Readable -> Writable
Writable -> Document
EOF
fi
run_lab > "$test_dir/actual.txt"
diff -u "$test_dir/expected.txt" "$test_dir/actual.txt"
mise run "lab$lab" examples/project > "$test_dir/actual.txt"
diff -u "$test_dir/expected.txt" "$test_dir/actual.txt"
javac -encoding UTF-8 -d "$test_dir/classes" examples/project/*.java

mkdir -p "$test_dir/project/nested" "$test_dir/empty"
cat > "$test_dir/project/nested/Sample.java" <<'EOF'
package sample;
interface Readable {}
interface Writable {}
interface Both extends Readable, Writable {}
class Parent<T> {}
class Child extends sample.Parent<String> implements Both {
    String text = "class Fake extends Wrong {}";
    static class Nested extends Parent<Integer> {}
}
EOF
cat > "$test_dir/expected.txt" <<'EOF'
Both -> Child
Child ->
Nested ->
Parent -> Child, Nested
Readable -> Both
Writable -> Both
EOF
if [ "$lab" = 3 ]; then
    printf '%s\n' 'FILE nested/Sample.java' > "$test_dir/with-header.txt"
    cat "$test_dir/expected.txt" >> "$test_dir/with-header.txt"
    mv "$test_dir/with-header.txt" "$test_dir/expected.txt"
fi
run_lab "$test_dir/project" > "$test_dir/actual.txt"
diff -u "$test_dir/expected.txt" "$test_dir/actual.txt"

run_lab "$test_dir/empty" > "$test_dir/actual.txt"
test ! -s "$test_dir/actual.txt"

if run_lab "$test_dir/missing" > "$test_dir/actual.txt" 2> "$test_dir/error.txt"; then
    echo 'Отсутствующая папка должна завершать программу с ошибкой' >&2
    exit 1
fi
test ! -s "$test_dir/actual.txt"
test -s "$test_dir/error.txt"

if run_lab examples/project extra > "$test_dir/actual.txt" 2> "$test_dir/error.txt"; then
    echo 'Лишние аргументы должны завершать программу с ошибкой' >&2
    exit 1
fi
test ! -s "$test_dir/actual.txt"
test -s "$test_dir/error.txt"

if [ "$lab" != 2 ]; then
    mkdir -p "$test_dir/invalid" "$test_dir/many files"
    printf '%s\n' 'class Broken extends {' > "$test_dir/invalid/Broken.java"
    printf '%s\n' 'class Valid {}' > "$test_dir/invalid/Valid.java"
    if run_lab "$test_dir/invalid" > "$test_dir/actual.txt" 2> "$test_dir/error.txt"; then
        echo 'Ошибка синтаксиса должна завершать программу с ошибкой' >&2
        exit 1
    fi
    test ! -s "$test_dir/actual.txt"
    test -s "$test_dir/error.txt"

    : > "$test_dir/expected.txt"
    number=1
    while [ "$number" -le 80 ]; do
        name=$(printf 'Child%03d' "$number")
        printf 'class %s extends Parent implements Marker {}\n' "$name" > "$test_dir/many files/$name.java"
        if [ "$lab" = 3 ]; then
            printf 'FILE %s.java\n%s ->\nMarker -> %s\nParent -> %s\n' "$name" "$name" "$name" "$name" >> "$test_dir/expected.txt"
        else
            printf '%s ->\n' "$name" >> "$test_dir/expected.txt"
        fi
        number=$((number + 1))
    done
    if [ "$lab" != 3 ]; then
        for parent in Marker Parent; do
            printf '%s -> ' "$parent" >> "$test_dir/expected.txt"
            number=1
            while [ "$number" -le 80 ]; do
                if [ "$number" -gt 1 ]; then printf ', ' >> "$test_dir/expected.txt"; fi
                printf 'Child%03d' "$number" >> "$test_dir/expected.txt"
                number=$((number + 1))
            done
            printf '\n' >> "$test_dir/expected.txt"
        done
    fi
    run_lab "$test_dir/many files" > "$test_dir/actual.txt"
    diff -u "$test_dir/expected.txt" "$test_dir/actual.txt"
    if [ "$lab" = 5 ] || [ "$lab" = 6 ]; then
        for workers in 1 3; do
            run_lab "$test_dir/many files" "$workers" > "$test_dir/actual.txt"
            diff -u "$test_dir/expected.txt" "$test_dir/actual.txt"
        done
        for workers in 0 -1 invalid; do
            if run_lab examples/project "$workers" > "$test_dir/actual.txt" 2> "$test_dir/error.txt"; then
                echo 'Неверное число потоков должно завершать программу с ошибкой' >&2
                exit 1
            fi
            test ! -s "$test_dir/actual.txt"
            test -s "$test_dir/error.txt"
        done
        if run_lab examples/project 2 extra > "$test_dir/actual.txt" 2> "$test_dir/error.txt"; then
            echo 'Лишние аргументы должны завершать программу с ошибкой' >&2
            exit 1
        fi
        test ! -s "$test_dir/actual.txt"
        test -s "$test_dir/error.txt"
    fi
fi
echo "Проверки лабораторной $lab пройдены"
