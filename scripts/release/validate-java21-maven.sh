#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

command -v java >/dev/null 2>&1 || { echo 'ERROR: Java is required.' >&2; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo 'ERROR: Maven is required.' >&2; exit 1; }

JAVA_MAJOR=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
[[ "$JAVA_MAJOR" == "21" ]] || { echo "ERROR: Java 21 required; found $JAVA_MAJOR." >&2; exit 1; }

mapfile -t POMS < <(find services -mindepth 2 -maxdepth 2 -name pom.xml -print | sort)
((${#POMS[@]} > 0)) || { echo 'ERROR: no service pom.xml files found.' >&2; exit 1; }

failures=0
for pom in "${POMS[@]}"; do
  module=$(dirname "$pom")
  echo "========== $module =========="
  if ! grep -Eq '<java.version>[[:space:]]*21[[:space:]]*</java.version>|<maven.compiler.release>[[:space:]]*21[[:space:]]*</maven.compiler.release>|<release>[[:space:]]*21[[:space:]]*</release>' "$pom"; then
    echo "ERROR: $pom does not explicitly target Java 21." >&2
    failures=$((failures+1))
    continue
  fi
  if ! mvn -B -ntp -f "$pom" -DskipTests=false verify; then
    echo "ERROR: Maven verify failed for $module." >&2
    failures=$((failures+1))
  fi
done

(( failures == 0 )) || { echo "FAILED: $failures Java/Maven module(s) failed." >&2; exit 1; }
echo "SUCCESS: ${#POMS[@]} Java services target Java 21 and passed Maven verify."
