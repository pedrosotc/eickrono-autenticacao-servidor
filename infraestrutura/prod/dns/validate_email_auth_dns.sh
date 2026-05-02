#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
uso: validate_email_auth_dns.sh --domain <dominio> [opcoes]

opcoes:
  --selector <nome>            seletor DKIM. Pode ser repetido. Default: sig1
  --report-email <email>       endereco sugerido para relatorios DMARC
  --dig-bin <binario>          binario dig a usar (default: env DIG_BIN ou "dig")
  --help                       mostra esta ajuda

status de saida:
  0  todos os registros obrigatorios encontrados
  1  pelo menos um registro obrigatorio ausente
  2  uso invalido ou dependencia ausente
EOF
}

domain=""
report_email=""
dig_bin="${DIG_BIN:-dig}"
declare -a selectors=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --domain)
      domain="${2:-}"
      shift 2
      ;;
    --selector)
      selectors+=("${2:-}")
      shift 2
      ;;
    --report-email)
      report_email="${2:-}"
      shift 2
      ;;
    --dig-bin)
      dig_bin="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "argumento invalido: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ -z "$domain" ]; then
  usage >&2
  exit 2
fi

if [ "${#selectors[@]}" -eq 0 ]; then
  selectors=(sig1)
fi

if [ -z "$report_email" ]; then
  report_email="dmarc@${domain}"
fi

if ! command -v "$dig_bin" >/dev/null 2>&1; then
  echo "binario dig nao encontrado: ${dig_bin}" >&2
  exit 2
fi

query_record() {
  local type="$1"
  local name="$2"
  "$dig_bin" +short "$type" "$name" 2>/dev/null | sed '/^[[:space:]]*$/d'
}

flatten_record() {
  tr '\n' ' ' | tr -d '"' | sed 's/[[:space:]]\+/ /g; s/^ //; s/ $//'
}

spf_raw="$(query_record TXT "$domain" | flatten_record)"
dmarc_raw="$(query_record TXT "_dmarc.${domain}" | flatten_record)"

spf_present="false"
dmarc_present="false"
if printf '%s\n' "$spf_raw" | grep -qi 'v=spf1'; then
  spf_present="true"
fi
if printf '%s\n' "$dmarc_raw" | grep -qi 'v=dmarc1'; then
  dmarc_present="true"
fi

missing=()
if [ "$spf_present" != "true" ]; then
  missing+=("spf")
fi
if [ "$dmarc_present" != "true" ]; then
  missing+=("dmarc")
fi

echo "DOMAIN=${domain}"
echo "DIG_BIN=${dig_bin}"
echo "SPF_PRESENT=${spf_present}"
echo "SPF_RECORD=${spf_raw:-<ausente>}"
echo "DMARC_PRESENT=${dmarc_present}"
echo "DMARC_RECORD=${dmarc_raw:-<ausente>}"
echo "RECOMMENDED_DMARC=v=DMARC1; p=none; rua=mailto:${report_email}; adkim=r; aspf=r; fo=1"

for selector in "${selectors[@]}"; do
  selector_host="${selector}._domainkey.${domain}"
  dkim_cname="$(query_record CNAME "$selector_host" | flatten_record)"
  dkim_txt="$(query_record TXT "$selector_host" | flatten_record)"
  dkim_present="false"
  dkim_source="<ausente>"
  if [ -n "$dkim_cname" ]; then
    dkim_present="true"
    dkim_source="CNAME:${dkim_cname}"
  elif [ -n "$dkim_txt" ]; then
    dkim_present="true"
    dkim_source="TXT:${dkim_txt}"
  else
    missing+=("dkim:${selector}")
  fi

  echo "DKIM_SELECTOR_${selector}_PRESENT=${dkim_present}"
  echo "DKIM_SELECTOR_${selector}_RECORD=${dkim_source}"
done

if [ "${#missing[@]}" -gt 0 ]; then
  echo "OVERALL_STATUS=fail"
  echo "MISSING_RECORDS=$(IFS=,; echo "${missing[*]}")"
  exit 1
fi

echo "OVERALL_STATUS=ok"
echo "NEXT_STEP=validar_cabecalhos_reais_com_spf_dkim_dmarc_pass"
