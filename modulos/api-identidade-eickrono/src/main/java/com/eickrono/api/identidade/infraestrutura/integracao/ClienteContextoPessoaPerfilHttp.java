package com.eickrono.api.identidade.infraestrutura.integracao;

import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfil;
import com.eickrono.api.identidade.aplicacao.servico.ClienteContextoPessoaPerfil;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class ClienteContextoPessoaPerfilHttp implements ClienteContextoPessoaPerfil {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String CAMINHO_CONTEXTO = "/api/interna/identidade/contexto";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final String urlBase;
    private final String segredoInterno;
    private final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak;

    public ClienteContextoPessoaPerfilHttp(final RestTemplateBuilder restTemplateBuilder,
                                           final PerfilDominioBackchannelProperties properties,
                                           final IntegracaoInternaProperties integracaoInternaProperties,
                                           final ConfiguradorRestTemplateBackchannelMtls configuradorRestTemplateBackchannelMtls,
                                           final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak) {
        PerfilDominioBackchannelProperties configuracao = Objects.requireNonNull(properties, "properties é obrigatório");
        this.urlBase = configuracao.getUrlBase();
        this.restTemplate = Objects.requireNonNull(
                        configuradorRestTemplateBackchannelMtls,
                        "configuradorRestTemplateBackchannelMtls é obrigatório")
                .configurar(restTemplateBuilder, this.urlBase, configuracao.getTimeout())
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.segredoInterno = Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties é obrigatório")
                .getSegredo();
        this.clienteTokenBackchannelPerfilKeycloak = Objects.requireNonNull(
                clienteTokenBackchannelPerfilKeycloak,
                "clienteTokenBackchannelPerfilKeycloak é obrigatório");
    }

    @Override
    public Optional<ContextoPessoaPerfil> buscarPorPessoaId(final Long pessoaId) {
        if (pessoaId == null) {
            return Optional.empty();
        }
        return buscar("?pessoaId=" + pessoaId);
    }

    @Override
    public Optional<ContextoPessoaPerfil> buscarPorSub(final String sub) {
        if (sub == null || sub.isBlank()) {
            return Optional.empty();
        }
        return buscar("?sub=" + UriEscapers.escapeQueryParam(sub.trim()));
    }

    @Override
    public Optional<ContextoPessoaPerfil> buscarPorEmail(final String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return buscar("?email=" + UriEscapers.escapeQueryParam(email.trim().toLowerCase(Locale.ROOT)));
    }

    private Optional<ContextoPessoaPerfil> buscar(final String queryString) {
        ResponseEntity<ContextoPessoaPerfil> response = restTemplate.exchange(
                URI.create(urlBase + CAMINHO_CONTEXTO + queryString),
                HttpMethod.GET,
                new HttpEntity<>(cabecalhosBasicos()),
                ContextoPessoaPerfil.class
        );
        if (response.getStatusCode() == NOT_FOUND) {
            return Optional.empty();
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Falha ao resolver contexto de pessoa no serviço de perfil.");
        }
        return Optional.ofNullable(response.getBody());
    }

    private HttpHeaders cabecalhosBasicos() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_SEGREDO_INTERNO, segredoInterno);
        headers.setBearerAuth(clienteTokenBackchannelPerfilKeycloak.obterTokenBearer());
        return headers;
    }

    private static final class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {
        @Override
        public boolean hasError(@NonNull final ClientHttpResponse response) {
            return false;
        }
    }

    private static final class UriEscapers {
        private UriEscapers() {
        }

        private static String escapeQueryParam(final String value) {
            return value.replace(" ", "%20");
        }
    }
}
