#!/usr/bin/env bash

set -uo pipefail

if [ "$#" -lt 2 ]; then
  echo "uso: $0 <runbook> <comando> [args...]" >&2
  exit 2
fi

runbook="$1"
shift

if [ ! -f "$runbook" ]; then
  echo "runbook nao encontrado: $runbook" >&2
  exit 2
fi

timestamp="$(date '+%Y-%m-%d %H:%M:%S %Z')"
cwd="$(pwd)"
tmp_output="$(mktemp)"

set +e
"$@" >"$tmp_output" 2>&1
status=$?
set -e

{
  printf '\n#### %s\n' "$timestamp"
  printf -- '- Diretório: `%s`\n' "$cwd"
  printf -- '- Comando: ' 
  printf '`'
  printf '%q ' "$@"
  printf '`\n'
  printf -- '- Exit code: `%s`\n' "$status"
  printf '```text\n'
  cat "$tmp_output"
  printf '```\n'
} >>"$runbook"

cat "$tmp_output"
rm -f "$tmp_output"
exit "$status"
