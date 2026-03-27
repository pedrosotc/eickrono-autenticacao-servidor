package com.eickrono.api.contas.infraestrutura.configuracao;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Valida o token de dispositivo chamando a API de Identidade.
 */
@Component
public class ValidadorTokenDispositivoHttp implements ValidadorTokenDispositivoRemoto {

    private static final String HEADER_DEVICE_TOKEN = "X-Device-Token";
    private static final String CAMINHO_VALIDACAO = "/identidade/dispositivos/token/validacao";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final String urlBase;

    public ValidadorTokenDispositivoHttp(RestTemplateBuilder restTemplateBuilder,
                                         IntegracaoIdentidadeProperties properties) {
        this.restTemplate = restTemplateBuilder
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.urlBase = Objects.requireNonNull(
                Objects.requireNonNull(properties, "properties e obrigatorio").getUrlBase(),
                "integracao.identidade.url-base e obrigatorio");
    }

    @Override
    @SuppressWarnings("null")
    public ResultadoValidacaoTokenDispositivoRemoto validar(String authorizationHeader, String tokenDispositivo) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(authorizationHeader)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        headers.set(HEADER_DEVICE_TOKEN, tokenDispositivo);

        ResponseEntity<ValidacaoTokenDispositivoResponse> response = restTemplate.exchange(
                URI.create(urlBase + CAMINHO_VALIDACAO),
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                ValidacaoTokenDispositivoResponse.class);

        ValidacaoTokenDispositivoResponse payload = response.getBody();
        if (payload == null) {
            payload = new ValidacaoTokenDispositivoResponse(false,
                    "DEVICE_TOKEN_VALIDATION_UNAVAILABLE",
                    "Falha ao validar token de dispositivo",
                    null);
        }
        return new ResultadoValidacaoTokenDispositivoRemoto(response.getStatusCode().value(), payload);
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull ClientHttpResponse response) {
            return false;
        }
    }
}
