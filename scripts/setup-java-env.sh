#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
jdk_source="${1:-${JAVA_HOME:-}}"

if [[ -z "$jdk_source" || ! -x "$jdk_source/bin/javac" ]]; then
    echo 'Uso: ./scripts/setup-java-env.sh /ruta/a/un/jdk-25' >&2
    exit 1
fi
jdk_source="$(cd -- "$jdk_source" && pwd -P)"
case "$("$jdk_source/bin/javac" -version 2>&1)" in
    'javac 25'|'javac 25.'*) ;;
    *) echo 'Se requiere un JDK 25 completo.' >&2; exit 1 ;;
esac

if [[ -e "$project_dir/.java-env/jdk" ]]; then
    echo 'Ya existe .java-env/jdk; no se reemplazó. Usá ./mvnw-local -version.' >&2
    exit 1
fi

mkdir -p "$project_dir/.java-env/maven" "$project_dir/.java-env/repository"
cp -a -- "$jdk_source" "$project_dir/.java-env/jdk"
echo 'Entorno preparado. Ejecutá ./mvnw-local -version o ./mvnw-local verify.'
