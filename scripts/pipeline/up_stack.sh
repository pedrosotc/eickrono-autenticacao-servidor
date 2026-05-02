#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Uso: $0 <dev|hml>" >&2
  exit 1
fi

AMBIENTE="$1"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_DIR="${ROOT_DIR}/infraestrutura/${AMBIENTE}"

if [[ ! -d "${COMPOSE_DIR}" ]]; then
  echo "Ambiente invalido: ${AMBIENTE}" >&2
  exit 1
fi

"${ROOT_DIR}/scripts/pipeline/package_servicos.sh"
"${ROOT_DIR}/scripts/pipeline/compose_config.sh" "${AMBIENTE}"

echo "==> Subindo stack ${AMBIENTE}"
(
  cd "${COMPOSE_DIR}"
  docker compose up -d --build servidor-autorizacao api-identidade-eickrono api-contas-eickrono
)

echo "Stack ${AMBIENTE} iniciada."
