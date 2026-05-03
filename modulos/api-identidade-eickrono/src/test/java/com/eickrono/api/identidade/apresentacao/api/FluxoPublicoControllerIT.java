package com.eickrono.api.identidade.apresentacao.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.aplicacao.servico.AvaliacaoSegurancaAplicativoService;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaPendenteScheduler;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.ClienteContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.servico.IntegracaoProdutoPendenteScheduler;
import com.eickrono.api.identidade.aplicacao.servico.RecuperacaoSenhaService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoScheduler;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoLoginSilenciosoService;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = AplicacaoApiIdentidade.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = FluxoPublicoControllerIT.LocalDatabaseOidcInitializer.class)
class FluxoPublicoControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CadastroContaInternaServico cadastroContaInternaServico;

    @MockBean
    private AtestacaoAppServico atestacaoAppServico;

    @MockBean
    private AvaliacaoSegurancaAplicativoService avaliacaoSegurancaAplicativoService;

    @MockBean
    private AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico;

    @MockBean
    private ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema;

    @MockBean
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @MockBean
    private RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService;

    @MockBean
    private CadastroContaPendenteScheduler cadastroContaPendenteScheduler;

    @MockBean
    private RegistroDispositivoScheduler registroDispositivoScheduler;

    @MockBean
    private IntegracaoProdutoPendenteScheduler integracaoProdutoPendenteScheduler;

    static final class LocalDatabaseOidcInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final String DEFAULT_DB_HOST = "localhost";
        private static final String DEFAULT_DB_PORT = "5432";
        private static final String DEFAULT_DB_NAME = "eickrono_identidade";
        private static final String DEFAULT_DB_USER = "eickrono";
        private static final String DEFAULT_DB_PASSWORD = "senhaLocalDev";

        @Override
        public void initialize(final ConfigurableApplicationContext context) {
            String issuer = InfraestruturaTesteIdentidade.obterIssuer();
            TestPropertyValues.of(
                    "spring.datasource.url=" + jdbcUrl(),
                    "spring.datasource.username=" + env("EICKRONO_TEST_DB_USER", DEFAULT_DB_USER),
                    "spring.datasource.password=" + env("EICKRONO_TEST_DB_PASSWORD", DEFAULT_DB_PASSWORD),
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.flyway.enabled=false",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
                            + issuer + "/protocol/openid-connect/certs",
                    "fapi.seguranca.audiencia-esperada=api-identidade-eickrono"
            ).applyTo(context.getEnvironment());
            context.addApplicationListener((ApplicationListener<ContextClosedEvent>) event ->
                    InfraestruturaTesteIdentidade.encerrarInfraestrutura());
        }

        private static String jdbcUrl() {
            String explicit = System.getenv("EICKRONO_TEST_JDBC_URL");
            if (explicit != null && !explicit.isBlank()) {
                return explicit.trim();
            }
            return "jdbc:postgresql://"
                    + env("EICKRONO_TEST_DB_HOST", DEFAULT_DB_HOST)
                    + ":"
                    + env("EICKRONO_TEST_DB_PORT", DEFAULT_DB_PORT)
                    + "/"
                    + env("EICKRONO_TEST_DB_NAME", DEFAULT_DB_NAME);
        }

        private static String env(final String name, final String fallback) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim();
        }
    }

    @BeforeEach
    void setUp() {
        when(atestacaoAppServico.validarComprovante(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.ValidacaoAtestacaoAppConcluida(
                        null,
                        com.eickrono.api.identidade.aplicacao.modelo.ValidacaoOficialAtestacaoAppResultado.naoExecutada(
                                "validacao oficial nao executada no teste"
                        ),
                        com.eickrono.api.identidade.aplicacao.modelo.StatusValidacaoAtestacaoApp.VALIDADA_LOCALMENTE
                ));
        org.mockito.Mockito.doAnswer(invocacao -> new com.eickrono.api.identidade.aplicacao.modelo
                        .AvaliacaoSegurancaAplicativoRealizada(false, true, 0, java.util.List.of()))
                .when(avaliacaoSegurancaAplicativoService)
                .avaliar(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()
                );
        when(registroDispositivoLoginSilenciosoService.registrar(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DispositivoSessaoRegistrado(
                        "device-token-teste",
                        OffsetDateTime.parse("2026-03-27T20:00:00Z")
                ));
    }

    @Test
    void deveConsultarDisponibilidadePublicaDoUsuario() throws Exception {
        when(cadastroContaInternaServico.identificadorPublicoSistemaDisponivelPublico("ana.souza")).thenReturn(false);

        mockMvc.perform(get("/api/publica/cadastros/usuarios/disponibilidade")
                        .param("usuario", " Ana.Souza "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("ana.souza"))
                .andExpect(jsonPath("$.disponivel").value(false));

        verify(cadastroContaInternaServico).identificadorPublicoSistemaDisponivelPublico("ana.souza");
    }

    @Test
    void deveConsultarDisponibilidadePublicaDoUsuarioPorAplicacao() throws Exception {
        when(cadastroContaInternaServico.identificadorPublicoSistemaDisponivelPublico(
                "ana.souza",
                "eickrono-thimisu-app"
        ))
                .thenReturn(true);

        mockMvc.perform(get("/api/publica/cadastros/usuarios/disponibilidade")
                        .param("usuario", " Ana.Souza ")
                        .param("aplicacaoId", "eickrono-thimisu-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("ana.souza"))
                .andExpect(jsonPath("$.disponivel").value(true));

        verify(cadastroContaInternaServico)
                .identificadorPublicoSistemaDisponivelPublico("ana.souza", "eickrono-thimisu-app");
    }

    @Test
    void deveCriarCadastroPublicoComAplicacaoIdComoSistemaSolicitante() throws Exception {
        UUID cadastroId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(cadastroContaInternaServico.cadastrarPublico(
                eq(com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro.FISICA),
                eq("Ana Souza"),
                eq("Ana LTDA"),
                eq("ana.souza"),
                eq(com.eickrono.api.identidade.dominio.modelo.SexoPessoaCadastro.FEMININO),
                eq("BR"),
                eq(java.time.LocalDate.parse("1994-08-17")),
                eq("ana@eickrono.com"),
                eq("+5511999999999"),
                eq(com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro.SMS),
                eq("SenhaForte123"),
                eq("eickrono-thimisu-app"),
                any(),
                any()))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado(
                        cadastroId,
                        "sub-ana",
                        "ana@eickrono.com",
                        true
                ));

        mockMvc.perform(post("/api/publica/cadastros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "tipoPessoa": "FISICA",
                                  "nomeCompleto": "Ana Souza",
                                  "nomeFantasia": "Ana LTDA",
                                  "usuario": "ana.souza",
                                  "sexo": "FEMININO",
                                  "paisNascimento": "BR",
                                  "dataNascimento": "1994-08-17",
                                  "emailPrincipal": "ana@eickrono.com",
                                  "telefone": "+5511999999999",
                                  "tipoValidacaoTelefone": "SMS",
                                  "senha": "SenhaForte123",
                                  "plataformaApp": "IOS",
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cadastroId").value(cadastroId.toString()))
                .andExpect(jsonPath("$.status").value("PENDENTE_EMAIL"));

        verify(cadastroContaInternaServico).cadastrarPublico(
                eq(com.eickrono.api.identidade.dominio.modelo.TipoPessoaCadastro.FISICA),
                eq("Ana Souza"),
                eq("Ana LTDA"),
                eq("ana.souza"),
                eq(com.eickrono.api.identidade.dominio.modelo.SexoPessoaCadastro.FEMININO),
                eq("BR"),
                eq(java.time.LocalDate.parse("1994-08-17")),
                eq("ana@eickrono.com"),
                eq("+5511999999999"),
                eq(com.eickrono.api.identidade.dominio.modelo.CanalValidacaoTelefoneCadastro.SMS),
                eq("SenhaForte123"),
                eq("eickrono-thimisu-app"),
                any(),
                any());
    }

    @Test
    void deveCancelarCadastroPendentePublico() throws Exception {
        mockMvc.perform(delete("/api/publica/cadastros/{cadastroId}",
                        "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNoContent());

        verify(cadastroContaInternaServico).cancelarCadastroPendentePublico(
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void deveMapearContaNaoLiberadaQuandoKeycloakRetornaContaDesabilitada() throws Exception {
        UUID cadastroId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(autenticacaoSessaoInternaServico.autenticar("b@b.com", "SenhaForte123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account disabled"));
        when(cadastroContaInternaServico.buscarCadastroPendenteEmailPublico("b@b.com"))
                .thenReturn(Optional.of(cadastroId));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "b@b.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("conta_nao_liberada"))
                .andExpect(jsonPath("$.detalhes.cadastroId").value(cadastroId.toString()));
    }

    @Test
    void deveMapearCredenciaisInvalidasQuandoKeycloakRetornaSenhaInvalida() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("a@a.com", "SenhaErrada123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user credentials"));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "a@a.com",
                                  "senha": "SenhaErrada123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("credenciais_invalidas"));
    }

    @Test
    void deveMapearContaNaoLiberadaQuandoKeycloakRetornaContaNaoConfigurada() throws Exception {
        UUID cadastroId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(autenticacaoSessaoInternaServico.autenticar("b@b.com", "SenhaForte123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not fully set up"));
        when(cadastroContaInternaServico.buscarCadastroPendenteEmailPublico("b@b.com"))
                .thenReturn(Optional.of(cadastroId));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "b@b.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("conta_nao_liberada"))
                .andExpect(jsonPath("$.detalhes.cadastroId").value(cadastroId.toString()));
    }

    @Test
    void deveMapearContaIncompletaQuandoKeycloakRetornaContaNaoConfiguradaSemCadastroPendente() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("c@c.com", "SenhaForte123"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not fully set up"));
        when(cadastroContaInternaServico.buscarCadastroPendenteEmailPublico("c@c.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "c@c.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("conta_incompleta"));
    }

    @Test
    void deveEmitirTokenDispositivoJaNoLoginPublico() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("a@a.com", "SenhaForte123"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token",
                        "refresh-token",
                        3600
                ));
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("a@a.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        10L,
                        "sub-123",
                        "a@a.com",
                        "Ana",
                        "usuario-1",
                        "LIBERADO"
                )));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "a@a.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.tokenDispositivo").value("device-token-teste"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("cenario 15: deve permitir login central quando o contexto do produto estiver indisponivel")
    void devePermitirLoginCentralQuandoContextoDoProdutoEstiverIndisponivel() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("ana@eickrono.com", "SenhaForte123"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token",
                        "refresh-token",
                        3600
                ));
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com"))
                .thenThrow(new IllegalStateException("produto indisponivel"));
        when(cadastroContaInternaServico.buscarContextoCentralPorEmailPublico("ana@eickrono.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        77L,
                        "sub-ana",
                        "ana@eickrono.com",
                        "Ana Souza",
                        null,
                        "PENDENTE_LIBERACAO_PRODUTO"
                )));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "ana@eickrono.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.statusUsuario").value("PENDENTE_LIBERACAO_PRODUTO"))
                .andExpect(jsonPath("$.tokenDispositivo").value("device-token-teste"));

        verify(registroDispositivoLoginSilenciosoService).registrar(
                any(ContextoPessoaPerfilSistema.class),
                any()
        );
    }

    @Test
    @org.junit.jupiter.api.DisplayName("cenario 15: deve permitir login central quando o perfil do produto ainda estiver pendente")
    void devePermitirLoginCentralQuandoPerfilDoProdutoEstiverPendente() throws Exception {
        when(autenticacaoSessaoInternaServico.autenticar("ana@eickrono.com", "SenhaForte123"))
                .thenReturn(new com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada(
                        true,
                        "Bearer",
                        "access-token",
                        "refresh-token",
                        3600
                ));
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com"))
                .thenReturn(Optional.empty());
        when(cadastroContaInternaServico.buscarContextoCentralPorEmailPublico("ana@eickrono.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfilSistema(
                        77L,
                        "sub-ana",
                        "ana@eickrono.com",
                        "Ana Souza",
                        null,
                        "PENDENTE_LIBERACAO_PRODUTO"
                )));

        mockMvc.perform(post("/api/publica/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "login": "ana@eickrono.com",
                                  "senha": "SenhaForte123",
                                  "dispositivo": {
                                    "plataforma": "IOS",
                                    "identificadorInstalacao": "instalacao-teste",
                                    "modelo": "simulador",
                                    "sistemaOperacional": "ios",
                                    "versaoSistema": "18",
                                    "versaoApp": "1.0.0"
                                  },
                                  "atestacao": {
                                    "plataforma": "IOS",
                                    "provedor": "APPLE_APP_ATTEST",
                                    "tipoComprovante": "OBJETO_ASSERCAO",
                                    "identificadorDesafio": "desafio",
                                    "desafioBase64": "ZGVzYWZpbw==",
                                    "conteudoComprovante": "Y29tcHJvdmFudGU=",
                                    "geradoEm": "2026-03-26T20:00:00Z",
                                    "chaveId": "chave"
                                  },
                                  "segurancaAplicativo": {
                                    "plataforma": "IOS",
                                    "provedorAtestacao": "APPLE_APP_ATTEST",
                                    "rootOuJailbreak": false,
                                    "debuggerDetectado": false,
                                    "hookingSuspeito": false,
                                    "tamperSuspeito": false,
                                    "riscoCapturaTela": false,
                                    "assinaturaValida": true,
                                    "identidadeAplicativoValida": true,
                                    "sinaisRisco": [],
                                    "scoreRiscoLocal": 0,
                                    "bundleIdentifier": "com.eickrono.thimisu",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.statusUsuario").value("PENDENTE_LIBERACAO_PRODUTO"))
                .andExpect(jsonPath("$.tokenDispositivo").value("device-token-teste"));

        verify(registroDispositivoLoginSilenciosoService).registrar(
                any(ContextoPessoaPerfilSistema.class),
                any()
        );
    }
}
