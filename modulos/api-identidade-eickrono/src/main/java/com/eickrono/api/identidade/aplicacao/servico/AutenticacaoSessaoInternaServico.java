package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.infraestrutura.configuracao.SessaoInternaKeycloakProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AutenticacaoSessaoInternaServico {

    private static final String GRANT_TYPE_PASSWORD = "password";
    private static final String CAMINHO_TOKEN = "/realms/%s/protocol/openid-connect/token";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SessaoInternaKeycloakProperties properties;

    public AutenticacaoSessaoInternaServico(final RestTemplateBuilder restTemplateBuilder,
                                            final ObjectMapper objectMapper,
                                            final SessaoInternaKeycloakProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório");
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(this.properties.getTimeout())
                .setReadTimeout(this.properties.getTimeout())
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper é obrigatório");
    }

    public SessaoInternaAutenticada autenticar(final String login, final String senha) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE_PASSWORD);
        form.add("client_id", properties.getClientId());
        if (StringUtils.hasText(properties.getClientSecret())) {
            form.add("client_secret", properties.getClientSecret());
        }
        form.add("username", Objects.requireNonNull(login, "login é obrigatório"));
        form.add("password", Objects.requireNonNull(senha, "senha é obrigatória"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(montarUrlToken()),
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            return lerSessaoAutenticada(response.getBody());
        }

        throw traduzirErro(response);
    }

    private String montarUrlToken() {
        return properties.getUrlBase() + CAMINHO_TOKEN.formatted(properties.getRealm());
    }

    private SessaoInternaAutenticada lerSessaoAutenticada(final String body) {
        try {
            JsonNode payload = objectMapper.readTree(Objects.requireNonNullElse(body, "{}"));
            return new SessaoInternaAutenticada(
                    true,
                    payload.path("token_type").asText("Bearer"),
                    payload.path("access_token").asText(null),
                    payload.path("refresh_token").asText(null),
                    payload.path("expires_in").asLong(0L)
            );
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Falha ao interpretar a resposta do servidor de autorizacao.",
                    ex
            );
        }
    }

    private ResponseStatusException traduzirErro(final ResponseEntity<String> response) {
        try {
            JsonNode payload = objectMapper.readTree(Objects.requireNonNullElse(response.getBody(), "{}"));
            String error = payload.path("error").asText("");
            String description = payload.path("error_description").asText("");
            if ("invalid_grant".equals(error) || response.getStatusCode().value() == 401) {
                return new ResponseStatusException(
                        UNAUTHORIZED,
                        description.isBlank() ? "Credenciais invalidas." : description
                );
            }
        } catch (JsonProcessingException ignored) {
            // cai no erro generico abaixo
        }
        return new ResponseStatusException(
                BAD_GATEWAY,
                "Nao foi possivel autenticar a sessao no servidor de autorizacao."
        );
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }
}
