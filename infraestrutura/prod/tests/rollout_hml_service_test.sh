#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/ecs/rollout_hml_service.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "$haystack" != *"$needle"* ]]; then
    fail "nao encontrou trecho esperado: ${needle}"
  fi
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  if [ "$expected" != "$actual" ]; then
    fail "esperado '${expected}', obtido '${actual}'"
  fi
}

test_missing_required_args() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "uso: rollout_hml_service.sh"
}

test_invalid_service() {
  local output
  set +e
  output="$("$SCRIPT_UNDER_TEST" --service invalido --image exemplo:tag 2>&1)"
  local status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "service invalido: invalido"
}

test_dry_run_renders_identidade_image_and_commands() {
  local tmpdir output rendered image
  tmpdir="$(mktemp -d)"
  rendered="${tmpdir}/identidade.rendered.json"

  output="$("$SCRIPT_UNDER_TEST" \
    --service identidade \
    --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:hml-test-001 \
    --render-output "$rendered" \
    --dry-run)"

  image="$(jq -r '.containerDefinitions[0].image' "$rendered")"
  assert_equals "531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:hml-test-001" "$image"
  assert_contains "$output" "SERVICE_KEY=identidade"
  assert_contains "$output" "TASK_FAMILY=identidade-hml"
  assert_contains "$output" "CONTAINER_NAME=identidade"
  assert_contains "$output" "ECS_SERVICE=identidade-hml"
  assert_contains "$output" "RENDERED_IMAGE=531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-identidade-servidor:hml-test-001"
  assert_contains "$output" "aws ecs register-task-definition"
  assert_contains "$output" "aws ecs update-service"
  assert_contains "$output" "aws ecs wait services-stable"

  rm -rf "$tmpdir"
}

test_dry_run_no_wait_skips_wait_command() {
  local output
  output="$("$SCRIPT_UNDER_TEST" \
    --service auth \
    --image 531708494702.dkr.ecr.sa-east-1.amazonaws.com/eickrono-autenticacao-servidor:hml-test-002 \
    --no-wait \
    --dry-run)"

  assert_contains "$output" "WAIT_FOR_STABLE=false"
  if [[ "$output" == *"aws ecs wait services-stable"* ]]; then
    fail "nao deveria conter wait quando --no-wait for informado"
  fi
}

main() {
  test_missing_required_args
  test_invalid_service
  test_dry_run_renders_identidade_image_and_commands
  test_dry_run_no_wait_skips_wait_command
  echo "ok"
}

main "$@"
