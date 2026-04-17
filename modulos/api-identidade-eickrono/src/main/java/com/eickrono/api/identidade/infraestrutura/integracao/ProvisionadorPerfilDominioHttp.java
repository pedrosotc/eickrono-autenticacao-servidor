package com.eickrono.api.identidade.infraestrutura.integracao;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;

import com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilRealizado;
import com.eickrono.api.identidade.aplicacao.servico.ProvisionadorPerfilDominioServico;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.infraestrutura.configuracao.ConfiguradorRestTemplateBackchannelMtls;
import com.eickrono.api.identidade.infraestrutura.configuracao.IntegracaoInternaProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.PerfilDominioBackchannelProperties;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProvisionadorPerfilDominioHttp implements ProvisionadorPerfilDominioServico {

    private static final String HEADER_SEGREDO_INTERNO = "X-Eickrono-Internal-Secret";
    private static final String CAMINHO_PROVISIONAMENTO = "/api/interna/identidade/provisionamentos";
    private static final String CAMINHO_DISPONIBILIDADE_USUARIO = "/api/interna/identidade/usuarios/disponibilidade";
    private static final DefaultResponseErrorHandler NO_OP_ERROR_HANDLER = new NoOpResponseErrorHandler();

    private final RestTemplate restTemplate;
    private final String urlBase;
    private final String segredoInterno;
    private final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak;

    public ProvisionadorPerfilDominioHttp(final RestTemplateBuilder restTemplateBuilder,
                                          final PerfilDominioBackchannelProperties properties,
                                          final IntegracaoInternaProperties integracaoInternaProperties,
                                          final ConfiguradorRestTemplateBackchannelMtls configuradorRestTemplateBackchannelMtls,
                                          final ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak) {
        PerfilDominioBackchannelProperties configuracao = Objects.requireNonNull(properties, "properties e obrigatorio");
        this.urlBase = Objects.requireNonNull(configuracao.getUrlBase(), "integracao.perfil.url-base e obrigatorio");
        this.restTemplate = Objects.requireNonNull(
                        configuradorRestTemplateBackchannelMtls,
                        "configuradorRestTemplateBackchannelMtls e obrigatorio")
                .configurar(restTemplateBuilder, this.urlBase, configuracao.getTimeout())
                .errorHandler(NO_OP_ERROR_HANDLER)
                .build();
        this.segredoInterno = Objects.requireNonNull(integracaoInternaProperties, "integracaoInternaProperties e obrigatorio")
                .getSegredo();
        this.clienteTokenBackchannelPerfilKeycloak = Objects.requireNonNull(
                clienteTokenBackchannelPerfilKeycloak,
                "clienteTokenBackchannelPerfilKeycloak e obrigatorio");
    }

    @Override
    public boolean usuarioDisponivel(final String usuario) {
        String usuarioNormalizado = Objects.requireNonNull(usuario, "usuario e obrigatorio")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (usuarioNormalizado.isBlank()) {
            return false;
        }
        ResponseEntity<DisponibilidadeUsuarioInternaResponse> response = restTemplate.exchange(
                URI.create(urlBase + CAMINHO_DISPONIBILIDADE_USUARIO
                        + "?usuario=" + UriEscapers.escapeQueryParam(usuarioNormalizado)),
                HttpMethod.GET,
                new HttpEntity<>(cabecalhosBasicos()),
                DisponibilidadeUsuarioInternaResponse.class
        );
        DisponibilidadeUsuarioInternaResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Nao foi possivel validar a disponibilidade do usuario no dominio do thimisu."
            );
        }
        return body.disponivel();
    }

    @Override
    public ProvisionamentoPerfilRealizado provisionarCadastroConfirmado(final CadastroConta cadastroConta) {
        Objects.requireNonNull(cadastroConta, "cadastroConta e obrigatorio");
        ResponseEntity<ProvisionamentoCadastroInternoResponse> response = restTemplate.exchange(
                URI.create(urlBase + CAMINHO_PROVISIONAMENTO),
                HttpMethod.POST,
                new HttpEntity<>(new ProvisionamentoCadastroInternoRequest(
                        cadastroConta.getCadastroId().toString(),
                        cadastroConta.getSubjectRemoto(),
                        cadastroConta.getTipoPessoa().name(),
                        cadastroConta.getNomeCompleto(),
                        cadastroConta.getNomeFantasia(),
                        cadastroConta.getUsuario(),
                        cadastroConta.getSexo() == null ? null : cadastroConta.getSexo().name(),
                        cadastroConta.getPaisNascimento(),
                        cadastroConta.getDataNascimento(),
                        cadastroConta.getEmailPrincipal(),
                        cadastroConta.getTelefonePrincipal(),
                        cadastroConta.getCanalValidacaoTelefone() == null
                                ? null
                                : cadastroConta.getCanalValidacaoTelefone().name()
                ), cabecalhosBasicos()),
                ProvisionamentoCadastroInternoResponse.class
        );
        if (response.getStatusCode().value() == CONFLICT.value()) {
            throw new ResponseStatusException(CONFLICT, "O perfil local do thimisu entrou em conflito durante o provisionamento.");
        }
        ProvisionamentoCadastroInternoResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Nao foi possivel provisionar o perfil no dominio do thimisu."
            );
        }
        return new ProvisionamentoPerfilRealizado(
                body.pessoaId(),
                body.usuarioId(),
                body.statusUsuario()
        );
    }

    private HttpHeaders cabecalhosBasicos() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_SEGREDO_INTERNO, segredoInterno);
        headers.setBearerAuth(clienteTokenBackchannelPerfilKeycloak.obterTokenBearer());
        return headers;
    }

    private record ProvisionamentoCadastroInternoRequest(
            String cadastroId,
            String subjectRemoto,
            String tipoPessoa,
            String nomeCompleto,
            String nomeFantasia,
            String usuario,
            String sexo,
            String paisNascimento,
            java.time.LocalDate dataNascimento,
            String emailPrincipal,
            String telefonePrincipal,
            String canalValidacaoTelefone
    ) {
    }

    private record ProvisionamentoCadastroInternoResponse(
            Long pessoaId,
            String usuarioId,
            String statusUsuario
    ) {
    }

    private record DisponibilidadeUsuarioInternaResponse(
            String usuario,
            boolean disponivel
    ) {
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
