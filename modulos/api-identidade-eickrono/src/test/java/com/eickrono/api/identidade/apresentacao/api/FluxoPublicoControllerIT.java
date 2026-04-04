package com.eickrono.api.identidade.apresentacao.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.servico.AtestacaoAppServico;
import com.eickrono.api.identidade.aplicacao.servico.AvaliacaoSegurancaAplicativoService;
import com.eickrono.api.identidade.aplicacao.servico.AutenticacaoSessaoInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.aplicacao.servico.ClienteContextoPessoaPerfil;
import com.eickrono.api.identidade.aplicacao.servico.RecuperacaoSenhaService;
import com.eickrono.api.identidade.aplicacao.servico.RegistroDispositivoLoginSilenciosoService;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfil;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = AplicacaoApiIdentidade.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
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
    private ClienteContextoPessoaPerfil clienteContextoPessoaPerfil;

    @MockBean
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @MockBean
    private RegistroDispositivoLoginSilenciosoService registroDispositivoLoginSilenciosoService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.doNothing()
                .when(atestacaoAppServico)
                .validarComprovante(org.mockito.ArgumentMatchers.any());
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
        when(cadastroContaInternaServico.usuarioDisponivelPublico("ana.souza")).thenReturn(false);

        mockMvc.perform(get("/api/publica/cadastros/usuarios/disponibilidade")
                        .param("usuario", " Ana.Souza "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("ana.souza"))
                .andExpect(jsonPath("$.disponivel").value(false));

        verify(cadastroContaInternaServico).usuarioDisponivelPublico("ana.souza");
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
                                  "aplicacaoId": "eickrono-flashcard-app",
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
                                    "provedor": "APP_ATTEST",
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
                                    "bundleIdentifier": "com.eickrono.flashCards",
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
                                  "aplicacaoId": "eickrono-flashcard-app",
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
                                    "provedor": "APP_ATTEST",
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
                                    "bundleIdentifier": "com.eickrono.flashCards",
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
                                  "aplicacaoId": "eickrono-flashcard-app",
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
                                    "provedor": "APP_ATTEST",
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
                                    "bundleIdentifier": "com.eickrono.flashCards",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("conta_nao_liberada"))
                .andExpect(jsonPath("$.detalhes.cadastroId").value(cadastroId.toString()));
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
        when(clienteContextoPessoaPerfil.buscarPorEmail("a@a.com"))
                .thenReturn(Optional.of(new ContextoPessoaPerfil(
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
                                  "aplicacaoId": "eickrono-flashcard-app",
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
                                    "provedor": "APP_ATTEST",
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
                                    "bundleIdentifier": "com.eickrono.flashCards",
                                    "teamIdentifier": "TEAM123"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.tokenDispositivo").value("device-token-teste"));
    }
}
