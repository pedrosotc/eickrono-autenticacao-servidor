#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_UNDER_TEST="${ROOT_DIR}/dns/validate_email_auth_dns.sh"

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

create_fake_dig() {
  local fake_dig
  fake_dig="$(mktemp)"
  cat >"$fake_dig" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

scenario="${FAKE_DNS_SCENARIO:-ok}"
if [ "$#" -lt 3 ]; then
  exit 0
fi

record_type="$2"
record_name="$3"

case "${scenario}|${record_type}|${record_name}" in
  ok\|TXT\|eickrono.com)
    echo "\"v=spf1 include:icloud.com ~all\""
    ;;
  ok\|TXT\|_dmarc.eickrono.com)
    echo "\"v=DMARC1; p=none; rua=mailto:dmarc@eickrono.com\""
    ;;
  ok\|CNAME\|sig1._domainkey.eickrono.com)
    echo "sig1.dkim.mail.example.net."
    ;;
  ok\|CNAME\|sig2._domainkey.eickrono.com)
    echo "sig2.dkim.mail.example.net."
    ;;
  missing-dmarc\|TXT\|eickrono.com)
    echo "\"v=spf1 include:icloud.com ~all\""
    ;;
  missing-dmarc\|CNAME\|sig1._domainkey.eickrono.com)
    echo "sig1.dkim.mail.example.net."
    ;;
  missing-dmarc\|CNAME\|sig2._domainkey.eickrono.com)
    echo "sig2.dkim.mail.example.net."
    ;;
  txt-dkim\|TXT\|eickrono.com)
    echo "\"v=spf1 include:icloud.com ~all\""
    ;;
  txt-dkim\|TXT\|_dmarc.eickrono.com)
    echo "\"v=DMARC1; p=none; rua=mailto:dmarc@eickrono.com\""
    ;;
  txt-dkim\|TXT\|sig1._domainkey.eickrono.com)
    echo "\"v=DKIM1; k=rsa; p=abc123\""
    ;;
  txt-dkim\|TXT\|sig2._domainkey.eickrono.com)
    echo "\"v=DKIM1; k=rsa; p=def456\""
    ;;
esac
EOF
  chmod +x "$fake_dig"
  printf '%s' "$fake_dig"
}

test_missing_required_args() {
  local output status
  set +e
  output="$(bash "$SCRIPT_UNDER_TEST" 2>&1)"
  status=$?
  set -e
  assert_equals "2" "$status"
  assert_contains "$output" "uso: validate_email_auth_dns.sh"
}

test_happy_path_with_spf_dmarc_and_dkim_cname() {
  local fake_dig output
  fake_dig="$(create_fake_dig)"
  output="$(FAKE_DNS_SCENARIO=ok bash "$SCRIPT_UNDER_TEST" --domain eickrono.com --dig-bin "$fake_dig" --selector sig1 --selector sig2)"
  rm -f "$fake_dig"

  assert_contains "$output" "SPF_PRESENT=true"
  assert_contains "$output" "DMARC_PRESENT=true"
  assert_contains "$output" "DKIM_SELECTOR_sig1_PRESENT=true"
  assert_contains "$output" "DKIM_SELECTOR_sig1_RECORD=CNAME:sig1.dkim.mail.example.net."
  assert_contains "$output" "DKIM_SELECTOR_sig2_PRESENT=true"
  assert_contains "$output" "OVERALL_STATUS=ok"
}

test_missing_dmarc_fails_with_exit_code_1() {
  local fake_dig output status
  fake_dig="$(create_fake_dig)"
  set +e
  output="$(FAKE_DNS_SCENARIO=missing-dmarc bash "$SCRIPT_UNDER_TEST" --domain eickrono.com --dig-bin "$fake_dig" 2>&1)"
  status=$?
  set -e
  rm -f "$fake_dig"

  assert_equals "1" "$status"
  assert_contains "$output" "SPF_PRESENT=true"
  assert_contains "$output" "DMARC_PRESENT=false"
  assert_contains "$output" "MISSING_RECORDS=dmarc"
  assert_contains "$output" "OVERALL_STATUS=fail"
}

test_txt_dkim_is_accepted_when_cname_is_absent() {
  local fake_dig output
  fake_dig="$(create_fake_dig)"
  output="$(FAKE_DNS_SCENARIO=txt-dkim bash "$SCRIPT_UNDER_TEST" --domain eickrono.com --dig-bin "$fake_dig" --selector sig1 --selector sig2)"
  rm -f "$fake_dig"

  assert_contains "$output" "DKIM_SELECTOR_sig1_PRESENT=true"
  assert_contains "$output" "DKIM_SELECTOR_sig1_RECORD=TXT:v=DKIM1; k=rsa; p=abc123"
  assert_contains "$output" "DKIM_SELECTOR_sig2_PRESENT=true"
  assert_contains "$output" "OVERALL_STATUS=ok"
}

main() {
  test_missing_required_args
  test_happy_path_with_spf_dmarc_and_dkim_cname
  test_missing_dmarc_fails_with_exit_code_1
  test_txt_dkim_is_accepted_when_cname_is_absent
  echo "ok"
}

main "$@"
