#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
uso: rollout_hml_service.sh --service <auth|identidade|thimisu-backend> --image <ecr-image:tag> [opcoes]

opcoes:
  --cluster <nome>             cluster ECS (default: eickrono-hml)
  --region <aws-region>        regiao AWS (default: sa-east-1)
  --ecs-service <nome>         nome do service ECS; default = family do task definition
  --render-output <arquivo>    persiste o task definition renderizado no caminho informado
  --no-wait                    nao aguarda service stable
  --dry-run                    nao executa comandos AWS; apenas imprime o plano
  --help                       mostra esta ajuda
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_WRAPPER="${PROD_DIR}/log_comando_runbook.sh"

cluster="eickrono-hml"
region="sa-east-1"
service_key=""
image=""
ecs_service=""
render_output=""
dry_run="false"
wait_for_stable="true"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --service)
      service_key="${2:-}"
      shift 2
      ;;
    --image)
      image="${2:-}"
      shift 2
      ;;
    --cluster)
      cluster="${2:-}"
      shift 2
      ;;
    --region)
      region="${2:-}"
      shift 2
      ;;
    --ecs-service)
      ecs_service="${2:-}"
      shift 2
      ;;
    --render-output)
      render_output="${2:-}"
      shift 2
      ;;
    --no-wait)
      wait_for_stable="false"
      shift
      ;;
    --dry-run)
      dry_run="true"
      shift
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

if [ -z "$service_key" ] || [ -z "$image" ]; then
  usage >&2
  exit 2
fi

case "$service_key" in
  auth)
    template="${SCRIPT_DIR}/auth-task-definition.hml.json"
    ;;
  identidade)
    template="${SCRIPT_DIR}/identidade-task-definition.hml.json"
    ;;
  thimisu-backend)
    template="${SCRIPT_DIR}/thimisu-backend-task-definition.hml.json"
    ;;
  *)
    echo "service invalido: ${service_key}" >&2
    echo "valores aceitos: auth, identidade, thimisu-backend" >&2
    exit 2
    ;;
esac

if [ ! -f "$template" ]; then
  echo "template nao encontrado: ${template}" >&2
  exit 2
fi

family="$(jq -r '.family' "$template")"
container_name="$(jq -r '.containerDefinitions[0].name' "$template")"
ecs_service="${ecs_service:-$family}"

if [ -n "$render_output" ]; then
  rendered_task="$render_output"
  mkdir -p "$(dirname "$rendered_task")"
else
  rendered_task="$(mktemp)"
fi

jq --arg image "$image" --arg container "$container_name" '
  .containerDefinitions |= map(
    if .name == $container then
      .image = $image
    else
      .
    end
  )
' "$template" >"$rendered_task"

cleanup() {
  if [ -z "$render_output" ] && [ -f "$rendered_task" ]; then
    rm -f "$rendered_task"
  fi
}
trap cleanup EXIT

run_cmd() {
  if [ "$dry_run" = "true" ]; then
    printf '[dry-run] '
    printf '%q ' "$@"
    printf '\n'
    return 0
  fi

  if [ -n "${EICKRONO_HML_HISTORICO:-}" ] && [ -f "$LOG_WRAPPER" ]; then
    "$LOG_WRAPPER" "$EICKRONO_HML_HISTORICO" "$@"
    return $?
  fi

  "$@"
}

echo "SERVICE_KEY=${service_key}"
echo "CLUSTER=${cluster}"
echo "REGION=${region}"
echo "TASK_FAMILY=${family}"
echo "CONTAINER_NAME=${container_name}"
echo "ECS_SERVICE=${ecs_service}"
echo "IMAGE=${image}"
echo "TASK_DEFINITION_TEMPLATE=${template}"
echo "TASK_DEFINITION_RENDERED=${rendered_task}"
echo "WAIT_FOR_STABLE=${wait_for_stable}"

if [ "$dry_run" = "true" ]; then
  jq -r '.containerDefinitions[0].image' "$rendered_task" | sed 's/^/RENDERED_IMAGE=/'
  run_cmd aws ecs register-task-definition --region "$region" --cli-input-json "file://${rendered_task}"
  run_cmd aws ecs update-service --region "$region" --cluster "$cluster" --service "$ecs_service" \
    --task-definition "${family}:<revision>" --force-new-deployment
  if [ "$wait_for_stable" = "true" ]; then
    run_cmd aws ecs wait services-stable --region "$region" --cluster "$cluster" --services "$ecs_service"
  fi
  run_cmd aws ecs describe-services --region "$region" --cluster "$cluster" --services "$ecs_service"
  exit 0
fi

task_definition_arn="$(
  run_cmd aws ecs register-task-definition \
    --region "$region" \
    --cli-input-json "file://${rendered_task}" \
    --query 'taskDefinition.taskDefinitionArn' \
    --output text
)"

run_cmd aws ecs update-service \
  --region "$region" \
  --cluster "$cluster" \
  --service "$ecs_service" \
  --task-definition "$task_definition_arn" \
  --force-new-deployment >/dev/null

if [ "$wait_for_stable" = "true" ]; then
  run_cmd aws ecs wait services-stable \
    --region "$region" \
    --cluster "$cluster" \
    --services "$ecs_service"
fi

run_cmd aws ecs describe-services \
  --region "$region" \
  --cluster "$cluster" \
  --services "$ecs_service" \
  --query 'services[0].{Service:serviceName,TaskDefinition:taskDefinition,Running:runningCount,Status:status,RolloutState:deployments[0].rolloutState}' \
  --output table
