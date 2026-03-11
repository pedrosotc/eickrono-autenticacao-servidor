package com.eickrono.api.contas.configuracao;

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
import org.springframework.web.client.RestTemplate;

/**
 * Valida o token de dispositivo chamando a API de Identidade.
 */
@Component
public class ValidadorTokenDispositivoHttp implements ValidadorTokenDispositivoRemoto {

    private static final String HEADER_DEVICE_TOKEN = "X-Device-Token";
    private static final String CAMINHO_VALIDACAO = "/identidade/dispositivos/token/validacao";

    private final RestTemplate restTemplate;
    private final IntegracaoIdentidadeProperties properties;

    public ValidadorTokenDispositivoHttp(RestTemplateBuilder restTemplateBuilder,
                                         IntegracaoIdentidadeProperties properties) {
        this.restTemplate = restTemplateBuilder
                .errorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
                    @Override
                    public boolean hasError(@NonNull ClientHttpResponse response) {
                        return false;
                    }
                })
                .build();
        this.properties = properties;
    }

    @Override
    public ResultadoValidacaoTokenDispositivoRemoto validar(String authorizationHeader, String tokenDispositivo) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(authorizationHeader)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        headers.set(HEADER_DEVICE_TOKEN, tokenDispositivo);

        URI endpoint = Objects.requireNonNull(URI.create(properties.getUrlBase() + CAMINHO_VALIDACAO));
        HttpMethod metodo = Objects.requireNonNull(HttpMethod.GET);
        ResponseEntity<ValidacaoTokenDispositivoResponse> response = restTemplate.exchange(
                endpoint,
                metodo,
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
}
