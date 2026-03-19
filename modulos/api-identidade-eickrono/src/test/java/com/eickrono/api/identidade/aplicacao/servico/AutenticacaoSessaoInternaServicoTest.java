package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.infraestrutura.configuracao.SessaoInternaKeycloakProperties;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

class AutenticacaoSessaoInternaServicoTest {

    private static final String URL_TOKEN = "http://localhost:8080/realms/desenvolvimento/protocol/openid-connect/token";

    private AutenticacaoSessaoInternaServico servico;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        SessaoInternaKeycloakProperties properties = new SessaoInternaKeycloakProperties();
        properties.setUrlBase("http://localhost:8080");
        properties.setRealm("desenvolvimento");
        properties.setClientId("app-flutter-local");
        properties.setClientSecret("");

        servico = new AutenticacaoSessaoInternaServico(
                new RestTemplateBuilder(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                properties
        );
        RestTemplate restTemplate = Objects.requireNonNull(
                (RestTemplate) ReflectionTestUtils.getField(servico, "restTemplate"));
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("deve autenticar sessao interna no token endpoint do Keycloak")
    void deveAutenticarSessaoInterna() {
        mockServer.expect(requestTo(URL_TOKEN))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=password")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=app-flutter-local")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("username=ana%40eickrono.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password=SenhaForte123")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token-123",
                          "refresh_token": "refresh-token-456",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        SessaoInternaAutenticada sessao = servico.autenticar("ana@eickrono.com", "SenhaForte123");

        assertThat(sessao.autenticado()).isTrue();
        assertThat(sessao.tipoToken()).isEqualTo("Bearer");
        assertThat(sessao.accessToken()).isEqualTo("access-token-123");
        assertThat(sessao.refreshToken()).isEqualTo("refresh-token-456");
        assertThat(sessao.expiresIn()).isEqualTo(3600L);
        mockServer.verify();
    }

    @Test
    @DisplayName("deve mapear invalid_grant para credenciais invalidas")
    void deveMapearCredenciaisInvalidas() {
        mockServer.expect(requestTo(URL_TOKEN))
                .andExpect(method(POST))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": "invalid_grant",
                                  "error_description": "Invalid user credentials"
                                }
                                """));

        assertThatThrownBy(() -> servico.autenticar("ana@eickrono.com", "senha-errada"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException response = (ResponseStatusException) ex;
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
        mockServer.verify();
    }
}
