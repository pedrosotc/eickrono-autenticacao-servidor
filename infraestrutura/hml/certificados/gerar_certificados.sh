#!/usr/bin/env bash
set -euo pipefail

DIAS_VALIDOS=${1:-825}
SENHA_KEYSTORE=${MTLS_KEYSTORE_SENHA:-senhaBackchannelHml}
SENHA_TRUSTSTORE=${MTLS_TRUSTSTORE_SENHA:-senhaBackchannelHml}

DIR_ATUAL=$(cd "$(dirname "$0")" && pwd)
cd "${DIR_ATUAL}"

ARQUIVOS_GERADOS=(
  backchannel-ca.key
  backchannel-ca.crt
  backchannel-ca.srl
  backchannel-truststore.p12
  api-identidade-eickrono.key
  api-identidade-eickrono.csr
  api-identidade-eickrono.crt
  api-identidade-eickrono.p12
  api-thimisu-eickrono.key
  api-thimisu-eickrono.csr
  api-thimisu-eickrono.crt
  api-thimisu-eickrono.p12
  servidor-autorizacao-interno.key
  servidor-autorizacao-interno.csr
  servidor-autorizacao-interno.crt
  servidor-autorizacao-interno.p12
)

rm -f "${ARQUIVOS_GERADOS[@]}"

openssl req -x509 -newkey rsa:4096 \
  -keyout backchannel-ca.key \
  -out backchannel-ca.crt \
  -days "${DIAS_VALIDOS}" \
  -nodes \
  -sha256 \
  -subj "/CN=eickrono-backchannel-hml-ca/O=Eickrono/OU=Homologacao"

gerar_certificado() {
  local nome="$1"
  local common_name="$2"
  local san="$3"
  local eku="$4"
  local arquivo_tmp
  arquivo_tmp=$(mktemp)

  cat > "${arquivo_tmp}" <<EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
CN = ${common_name}
O = Eickrono
OU = Homologacao

[v3_req]
subjectAltName = ${san}
extendedKeyUsage = ${eku}
keyUsage = digitalSignature, keyEncipherment
basicConstraints = CA:FALSE
EOF

  openssl req -new -newkey rsa:4096 \
    -keyout "${nome}.key" \
    -out "${nome}.csr" \
    -nodes \
    -sha256 \
    -config "${arquivo_tmp}"

  openssl x509 -req \
    -in "${nome}.csr" \
    -CA backchannel-ca.crt \
    -CAkey backchannel-ca.key \
    -CAcreateserial \
    -out "${nome}.crt" \
    -days "${DIAS_VALIDOS}" \
    -sha256 \
    -extfile "${arquivo_tmp}" \
    -extensions v3_req

  openssl pkcs12 -export \
    -inkey "${nome}.key" \
    -in "${nome}.crt" \
    -certfile backchannel-ca.crt \
    -out "${nome}.p12" \
    -password pass:"${SENHA_KEYSTORE}"

  rm -f "${arquivo_tmp}"
}

gerar_certificado \
  "api-identidade-eickrono" \
  "api-identidade-eickrono" \
  "DNS:api-identidade-eickrono,DNS:host.docker.internal,DNS:localhost,IP:127.0.0.1" \
  "serverAuth,clientAuth"

gerar_certificado \
  "api-thimisu-eickrono" \
  "api-thimisu-eickrono" \
  "DNS:api-thimisu-eickrono,DNS:host.docker.internal,DNS:localhost,IP:127.0.0.1" \
  "serverAuth,clientAuth"

gerar_certificado \
  "servidor-autorizacao-interno" \
  "servidor-autorizacao-interno" \
  "DNS:servidor-autorizacao,DNS:host.docker.internal,DNS:localhost,IP:127.0.0.1" \
  "clientAuth"

keytool -importcert -noprompt \
  -alias eickrono-backchannel-ca \
  -file backchannel-ca.crt \
  -keystore backchannel-truststore.p12 \
  -storetype PKCS12 \
  -storepass "${SENHA_TRUSTSTORE}"

echo "Certificados e truststore do backchannel gerados em ${DIR_ATUAL}"
