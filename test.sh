#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
test_dir=$(mktemp -d build/lab2-test.XXXXXX)
trap 'rm -rf "$test_dir"' EXIT HUP INT TERM

cat > "$test_dir/expected.txt" <<'EOF'
Attachment ->
Base -> Attachment, Document
Document -> FeaturedDocument
FeaturedDocument ->
Identifiable -> Document
Readable -> Writable
Writable -> Document
EOF
java -cp build/classes lab2.Main > "$test_dir/actual.txt"
diff -u "$test_dir/expected.txt" "$test_dir/actual.txt"
./run.sh 2 examples/project > "$test_dir/actual.txt"
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
java -cp build/classes lab2.Main "$test_dir/project" > "$test_dir/actual.txt"
diff -u "$test_dir/expected.txt" "$test_dir/actual.txt"

java -cp build/classes lab2.Main "$test_dir/empty" > "$test_dir/actual.txt"
test ! -s "$test_dir/actual.txt"

if java -cp build/classes lab2.Main "$test_dir/missing" > "$test_dir/actual.txt" 2> "$test_dir/error.txt"; then
    echo 'Отсутствующая папка должна завершать программу с ошибкой' >&2
    exit 1
fi
test ! -s "$test_dir/actual.txt"
test -s "$test_dir/error.txt"

if java -cp build/classes lab2.Main examples/project extra > "$test_dir/actual.txt" 2> "$test_dir/error.txt"; then
    echo 'Лишние аргументы должны завершать программу с ошибкой' >&2
    exit 1
fi
test ! -s "$test_dir/actual.txt"
test -s "$test_dir/error.txt"
echo 'Проверки лабораторной 2 пройдены'
