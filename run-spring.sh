#!/bin/sh
set -eu
cd "$(dirname "$0")"
spring_dir=build/spring-framework
if [ ! -d "$spring_dir" ]; then
    git clone --depth 1 --branch v7.0.9 https://github.com/spring-projects/spring-framework.git "$spring_dir"
fi
exec ./run.sh "$spring_dir"
