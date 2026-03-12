package com.eickrono.servidor.autorizacao.infraestrutura.dispositivo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * Valida o device token consultando a API de identidade.
 */
final class ValidadorRefreshDispositivoHttp implements ValidadorRefreshDispositivo {

    private static final String CAMINHO_VALIDACAO_INTERNA = "/identidade/dispositivos/token/validacao/interna";

    private final ConfiguracaoValidacaoRefreshDispositivo configuracao;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    ValidadorRefreshDispositivoHttp(ConfiguracaoValidacaoRefreshDispositivo configuracao) {
        this(configuracao, HttpClient.newBuilder()
                .connectTimeout(configuracao.timeout())
                .build(), new ObjectMapper());
    }

    ValidadorRefreshDispositivoHttp(ConfiguracaoValidacaoRefreshDispositivo configuracao,
                                    HttpClient httpClient,
                                    ObjectMapper objectMapper) {
        this.configuracao = configuracao;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResultadoValidacaoRefreshDispositivo validar(String usuarioSub, String deviceToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(configuracao.urlBaseIdentidade() + CAMINHO_VALIDACAO_INTERNA))
                    .timeout(configuracao.timeout())
                    .header("X-Eickrono-Internal-Secret", configuracao.segredoInterno())
                    .header("X-Device-Token", deviceToken)
                    .header("X-Usuario-Sub", usuarioSub == null ? "" : usuarioSub)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Falha ao validar device token no refresh. status=" + response.statusCode());
            }
            PayloadValidacao payload = Objects.requireNonNull(
                    objectMapper.readValue(response.body(), PayloadValidacao.class));
            return new ResultadoValidacaoRefreshDispositivo(
                    payload.valido(),
                    payload.codigo(),
                    payload.mensagem());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Validacao de device token interrompida", e);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao consultar a API de identidade", e);
        }
    }

    record PayloadValidacao(boolean valido, String codigo, String mensagem) {
    }
}
