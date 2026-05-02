#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/cloudflare/upsert_cloudflare_txt_record.sh"

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

create_fake_curl() {
  local fake_dir fake_curl
  fake_dir="$(mktemp -d)"
  fake_curl="${fake_dir}/curl"
  cat >"$fake_curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

args="$*"
mode="${FAKE_CF_MODE:-create}"
log_file="${FAKE_CF_LOG_FILE:-}"

if [ -n "$log_file" ]; then
  printf '%s\n' "$args" >>"$log_file"
fi

if [[ "$args" == *"zones?name="* ]]; then
  echo '{"result":[{"id":"zone-123"}],"success":true}'
  exit 0
fi

if [[ "$args" == *"dns_records?type=TXT"* ]]; then
  case "$mode" in
    update)
      echo '{"result":[{"id":"record-999"}],"success":true}'
      ;;
    create)
      echo '{"result":[],"success":true}'
      ;;
    nozone)
      echo '{"result":[],"success":true}'
      ;;
  esac
  exit 0
fi

if [[ "$args" == *"dns_records/record-999"* ]]; then
  echo '{"result":{"id":"record-999"},"success":true}'
  exit 0
fi

if [[ "$args" == *"/dns_records" ]]; then
  echo '{"result":{"id":"record-new"},"success":true}'
  exit 0
fi

echo '{"result":[],"success":true}'
EOF
  chmod +x "$fake_curl"
  printf '%s' "$fake_curl"
}

test_missing_required_args() {
  local output status
  set +e
  output="$(bash "$SCRIPT_UNDER_TEST" 2>&1)"
  status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "uso: upsert_cloudflare_txt_record.sh"
}

test_missing_token_env_outside_dry_run() {
  local output status
  set +e
  output="$(env -u CLOUDFLARE_API_TOKEN bash "$SCRIPT_UNDER_TEST" --zone eickrono.com --name _dmarc --content 'v=DMARC1; p=none' 2>&1)"
  status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "variavel de token ausente: CLOUDFLARE_API_TOKEN"
}

test_dry_run_prints_plan() {
  local output
  output="$(bash "$SCRIPT_UNDER_TEST" \
    --zone eickrono.com \
    --name _dmarc \
    --content 'v=DMARC1; p=none; rua=mailto:dmarc@eickrono.com' \
    --dry-run)"

  assert_contains "$output" "ZONE_NAME=eickrono.com"
  assert_contains "$output" "RECORD_NAME=_dmarc"
  assert_contains "$output" "ACTION=lookup-zone"
  assert_contains "$output" "ACTION=upsert-txt-record"
  assert_contains "$output" "CONTENT_PREVIEW=v=DMARC1; p=none; rua=mailto:dmarc@eickrono.com"
}

test_real_mode_creates_record_when_absent() {
  local fake_curl output log_file
  fake_curl="$(create_fake_curl)"
  log_file="$(mktemp)"
  output="$(PATH="$(dirname "$fake_curl"):$PATH" \
    CLOUDFLARE_API_TOKEN=test-token \
    FAKE_CF_MODE=create \
    FAKE_CF_LOG_FILE="$log_file" \
    bash "$SCRIPT_UNDER_TEST" \
      --zone eickrono.com \
      --name _dmarc \
      --content 'v=DMARC1; p=none')"

  assert_contains "$output" "ACTION=create-record"
  assert_contains "$(cat "$log_file")" "api.cloudflare.com/client/v4/zones/zone-123/dns_records"
  rm -f "$log_file"
  rm -rf "$(dirname "$fake_curl")"
}

test_real_mode_updates_record_when_present() {
  local fake_curl output log_file
  fake_curl="$(create_fake_curl)"
  log_file="$(mktemp)"
  output="$(PATH="$(dirname "$fake_curl"):$PATH" \
    CLOUDFLARE_API_TOKEN=test-token \
    FAKE_CF_MODE=update \
    FAKE_CF_LOG_FILE="$log_file" \
    bash "$SCRIPT_UNDER_TEST" \
      --zone eickrono.com \
      --name _dmarc \
      --content 'v=DMARC1; p=quarantine')"

  assert_contains "$output" "ACTION=update-record"
  assert_contains "$(cat "$log_file")" "api.cloudflare.com/client/v4/zones/zone-123/dns_records/record-999"
  rm -f "$log_file"
  rm -rf "$(dirname "$fake_curl")"
}

main() {
  test_missing_required_args
  test_missing_token_env_outside_dry_run
  test_dry_run_prints_plan
  test_real_mode_creates_record_when_absent
  test_real_mode_updates_record_when_present
  echo "ok"
}

main "$@"
