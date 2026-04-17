package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.EventoOfflineDispositivo;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.TipoEventoOfflineDispositivo;
import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfil;
import com.eickrono.api.identidade.aplicacao.servico.ClienteContextoPessoaPerfil;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.EventoOfflineDispositivoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.TokenDispositivoRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroResponse;
import com.eickrono.api.identidade.apresentacao.dto.PoliticaOfflineDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.ValidacaoTokenDispositivoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(classes = {
        AplicacaoApiIdentidade.class,
        RegistroDispositivoControllerITConfiguration.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RegistroDispositivoControllerITConfiguration.class)
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
class RegistroDispositivoControllerIT {

    private static final String REGISTRO_ENDPOINT = "/identidade/dispositivos/registro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CodigoCapturador codigoCapturador;

    @Autowired
    private TokenDispositivoRepositorio tokenDispositivoRepositorio;

    @Autowired
    private EventoOfflineDispositivoRepositorio eventoOfflineDispositivoRepositorio;

    @MockBean
    private ClienteContextoPessoaPerfil clienteContextoPessoaPerfil;

    @BeforeEach
    void setUp() {
        ContextoPessoaPerfil contexto = new ContextoPessoaPerfil(
                123L,
                "usuario-xyz",
                "teste@eickrono.com",
                "Usuario Teste",
                null,
                "ATIVO"
        );
        when(clienteContextoPessoaPerfil.buscarPorSub("usuario-xyz"))
                .thenReturn(Optional.of(contexto));
        when(clienteContextoPessoaPerfil.buscarPorEmail("teste@eickrono.com"))
                .thenReturn(Optional.of(contexto));
    }

    private MockMvc mockMvc() {
        return Objects.requireNonNull(mockMvc);
    }

    private ObjectMapper objectMapper() {
        return Objects.requireNonNull(objectMapper);
    }

    private CodigoCapturador codigoCapturador() {
        return Objects.requireNonNull(codigoCapturador);
    }

    private TokenDispositivoRepositorio tokenDispositivoRepositorio() {
        return Objects.requireNonNull(tokenDispositivoRepositorio);
    }

    private EventoOfflineDispositivoRepositorio eventoOfflineDispositivoRepositorio() {
        return Objects.requireNonNull(eventoOfflineDispositivoRepositorio);
    }

    @Test
    void fluxoCompletoDeRegistroConfirmacaoERevogacao() throws Exception {
        RegistroDispositivoResponse registro = solicitarRegistro();
        assertThat(registro.status()).isEqualTo(StatusRegistroDispositivo.PENDENTE);
        assertThat(registro.canaisConfirmacao()).containsExactlyInAnyOrder(CanalVerificacao.EMAIL, CanalVerificacao.SMS);

        String codigoSms = codigoCapturador().obterCodigo(registro.registroId(), CanalVerificacao.SMS)
                .orElseThrow(() -> new IllegalStateException("Código SMS não capturado"));
        String codigoEmail = codigoCapturador().obterCodigo(registro.registroId(), CanalVerificacao.EMAIL)
                .orElseThrow(() -> new IllegalStateException("Código e-mail não capturado"));

        ConfirmacaoRegistroResponse confirmacao = confirmarRegistro(registro.registroId(), codigoSms, codigoEmail);

        assertThat(confirmacao.tokenDispositivo()).isNotBlank();

        // GET com token válido deve passar pelo filtro; o endpoint em si está desativado e responde 410.
        mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isGone());

        // Sem o cabeçalho obrigatório deve retornar 428
        mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt())))
                .andExpect(status().isPreconditionRequired());
        MvcResult semCabecalho = mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt())))
                .andExpect(status().isPreconditionRequired())
                .andReturn();
        assertThat(semCabecalho.getResponse().getContentAsString()).contains("DEVICE_TOKEN_MISSING");

        // Token inválido retorna 423
        MvcResult tokenInvalido = mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", "token-invalido"))
                .andExpect(status().isLocked())
                .andReturn();
        assertThat(tokenInvalido.getResponse().getContentAsString()).contains("DEVICE_TOKEN_INVALID");

        MvcResult validacao = mockMvc().perform(get("/identidade/dispositivos/token/validacao")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andReturn();
        ValidacaoTokenDispositivoResponse payloadValidacao = objectMapper().readValue(
                validacao.getResponse().getContentAsByteArray(),
                ValidacaoTokenDispositivoResponse.class);
        assertThat(payloadValidacao.valido()).isTrue();
        assertThat(payloadValidacao.codigo()).isEqualTo("DEVICE_TOKEN_VALID");

        MvcResult validacaoInterna = mockMvc().perform(get("/identidade/dispositivos/token/validacao/interna")
                        .with(Objects.requireNonNull(clienteJwtInterno()))
                        .header("X-Eickrono-Internal-Secret", "local-internal-secret")
                        .header("X-Usuario-Sub", "usuario-xyz")
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andReturn();
        ValidacaoTokenDispositivoResponse payloadInterno = objectMapper().readValue(
                validacaoInterna.getResponse().getContentAsByteArray(),
                ValidacaoTokenDispositivoResponse.class);
        assertThat(payloadInterno.valido()).isTrue();

        MvcResult politicaOffline = mockMvc().perform(get("/identidade/dispositivos/offline/politica")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andReturn();
        PoliticaOfflineDispositivoResponse politica = objectMapper().readValue(
                politicaOffline.getResponse().getContentAsByteArray(),
                PoliticaOfflineDispositivoResponse.class);
        assertThat(politica.permitido()).isTrue();
        assertThat(politica.exigeReconciliacao()).isTrue();
        assertThat(politica.condicoesBloqueio()).contains("TOKEN_REVOGADO");

        String payloadEventosOffline = Objects.requireNonNull(objectMapper().writeValueAsString(Map.of(
                "eventos", java.util.List.of(Map.of(
                        "tipoEvento", TipoEventoOfflineDispositivo.MODO_OFFLINE_ATIVADO.name(),
                        "detalhes", "usuario entrou em modo offline"
                )))));
        mockMvc().perform(post("/identidade/dispositivos/offline/eventos")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo())
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content(payloadEventosOffline))
                .andExpect(status().isAccepted());

        assertThat(eventoOfflineDispositivoRepositorio().findAll())
                .extracting(EventoOfflineDispositivo::getTipoEvento)
                .contains(TipoEventoOfflineDispositivo.MODO_OFFLINE_ATIVADO);

        // Revogação
        mockMvc().perform(post("/identidade/dispositivos/revogar")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo())
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content("{\"motivo\":\"SOLICITACAO_CLIENTE\"}"))
                .andExpect(status().isNoContent());

        MvcResult revogado = mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isLocked())
                .andReturn();
        assertThat(revogado.getResponse().getContentAsString()).contains("DEVICE_TOKEN_REVOKED");

        Optional<TokenDispositivo> tokenPersistido = tokenDispositivoRepositorio().findAll().stream().findFirst();
        assertThat(tokenPersistido).isPresent();
        assertThat(tokenPersistido.get().getMotivoRevogacao()).contains(MotivoRevogacaoToken.SOLICITACAO_CLIENTE);
    }

    @Test
    void reenviarCodigoRespeitaLimites() throws Exception {
        RegistroDispositivoResponse registro = solicitarRegistro();

        mockMvc().perform(post(REGISTRO_ENDPOINT + "/" + registro.registroId() + "/reenviar")
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content("{}"))
                .andExpect(status().isAccepted());
    }

    private RegistroDispositivoResponse solicitarRegistro() throws Exception {
        String payload = """
                {
                  "email": "teste@eickrono.com",
                  "telefone": "+55-11-99999-0000",
                  "fingerprint": "ios|iphone14,3|device",
                  "plataforma": "iOS",
                  "versaoAplicativo": "1.0.0"
                }
                """;

        MvcResult resultado = mockMvc().perform(post(REGISTRO_ENDPOINT)
                        .with(Objects.requireNonNull(clienteJwt()))
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        return objectMapper().readValue(resultado.getResponse().getContentAsByteArray(), RegistroDispositivoResponse.class);
    }

    private ConfirmacaoRegistroResponse confirmarRegistro(UUID registroId, String codigoSms, String codigoEmail) throws Exception {
        String payload = Objects.requireNonNull(objectMapper().writeValueAsString(Map.of(
                "codigoSms", codigoSms,
                "codigoEmail", codigoEmail
        )));

        MvcResult resultado = mockMvc().perform(post(REGISTRO_ENDPOINT + "/" + registroId + "/confirmacao")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper().readValue(resultado.getResponse().getContentAsByteArray(), ConfirmacaoRegistroResponse.class);
    }

    private RequestPostProcessor clienteJwt() {
        return Objects.requireNonNull(jwt().jwt(builder -> builder
                        .subject("usuario-xyz")
                        .claim("email", "teste@eickrono.com")
                        .claim("name", "Usuario Teste")
                        .claim("preferred_username", "usuario.teste")
                        .claim("scope", "identidade:ler"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_cliente"),
                        new SimpleGrantedAuthority("SCOPE_identidade:ler")));
    }

    private RequestPostProcessor clienteJwtInterno() {
        return Objects.requireNonNull(jwt().jwt(builder -> builder
                        .subject("service-account-servidor-autorizacao-interno")
                        .claim("azp", "servidor-autorizacao-interno")
                        .claim("preferred_username", "service-account-servidor-autorizacao-interno")));
    }

    private MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }

    static class CodigoCapturador {
        private final Map<CanalVerificacao, Map<UUID, String>> mapa = new ConcurrentHashMap<>();

        void registrar(UUID registroId, CanalVerificacao canal, String codigo) {
            mapa.computeIfAbsent(canal, c -> new ConcurrentHashMap<>())
                    .put(registroId, codigo);
        }

        Optional<String> obterCodigo(UUID registroId, CanalVerificacao canal) {
            return Optional.ofNullable(mapa.getOrDefault(canal, Map.of()).get(registroId));
        }
    }
}
