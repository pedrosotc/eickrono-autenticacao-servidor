package com.eickrono.servidor.autorizacao.dispositivo;

import java.time.Duration;

/**
 * Configuracao de integracao com a API de identidade para validar device token no refresh.
 */
public record ConfiguracaoValidacaoRefreshDispositivo(
        String urlBaseIdentidade,
        String segredoInterno,
        Duration timeout) {

    static ConfiguracaoValidacaoRefreshDispositivo fromEnvironment() {
        String urlBase = lerAmbiente("EICKRONO_IDENTIDADE_API_BASE_URL", "http://api-identidade-eickrono:8081");
        String segredo = lerAmbiente("EICKRONO_INTERNAL_SECRET", "local-internal-secret");
        String timeoutMs = lerAmbiente("EICKRONO_IDENTIDADE_TIMEOUT_MS", "3000");
        return new ConfiguracaoValidacaoRefreshDispositivo(
                urlBase,
                segredo,
                Duration.ofMillis(Long.parseLong(timeoutMs)));
    }

    private static String lerAmbiente(String chave, String padrao) {
        String valor = System.getenv(chave);
        if (valor == null || valor.isBlank()) {
            valor = System.getProperty(chave, padrao);
        }
        return valor;
    }
}
