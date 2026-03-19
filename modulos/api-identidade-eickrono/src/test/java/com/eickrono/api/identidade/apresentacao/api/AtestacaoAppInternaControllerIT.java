package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.dominio.modelo.DesafioAtestacaoApp;
import com.eickrono.api.identidade.dominio.repositorio.DesafioAtestacaoAppRepositorio;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AplicacaoApiIdentidade.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
class AtestacaoAppInternaControllerIT {

    private static final String SEGREDO_INTERNO = "local-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DesafioAtestacaoAppRepositorio desafioRepositorio;

    @AfterEach
    void limparBanco() {
        desafioRepositorio.deleteAll();
    }

    @Test
    void deveGerarDesafioInternoComSegredoValido() throws Exception {
        mockMvc.perform(post("/identidade/atestacoes/interna/desafios")
                        .header("X-Eickrono-Internal-Secret", SEGREDO_INTERNO)
                        .header("X-Eickrono-Client-Ip", "10.10.10.1")
                        .header("X-Eickrono-Client-User-Agent", "JUnit/MockMvc")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "operacao": "LOGIN",
                                  "plataforma": "ANDROID"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operacao").value("LOGIN"))
                .andExpect(jsonPath("$.plataforma").value("ANDROID"))
                .andExpect(jsonPath("$.provedorEsperado").value("GOOGLE_PLAY_INTEGRITY"))
                .andExpect(jsonPath("$.numeroProjetoNuvemAndroid").value("123456789"));

        assertThat(desafioRepositorio.findAll())
                .singleElement()
                .extracting(DesafioAtestacaoApp::getIpSolicitante, DesafioAtestacaoApp::getUserAgentSolicitante)
                .containsExactly("10.10.10.1", "JUnit/MockMvc");
    }

    @Test
    void deveValidarComprovanteInternoEConsumirDesafio() throws Exception {
        JsonNode desafio = criarDesafio("IOS");

        mockMvc.perform(post("/identidade/atestacoes/interna/validacoes")
                        .header("X-Eickrono-Internal-Secret", SEGREDO_INTERNO)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "plataforma": "IOS",
                                  "provedor": "APPLE_APP_ATTEST",
                                  "tipoComprovante": "OBJETO_ATESTACAO",
                                  "identificadorDesafio": "%s",
                                  "desafioBase64": "%s",
                                  "conteudoComprovante": "objeto-atestacao-base64",
                                  "geradoEm": "2026-03-18T21:00:00Z",
                                  "chaveId": "key-id-ios"
                                }
                                """.formatted(
                                desafio.get("identificadorDesafio").asText(),
                                desafio.get("desafioBase64").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusValidacao").value("VALIDADA_LOCALMENTE"))
                .andExpect(jsonPath("$.identificadorDesafio").value(desafio.get("identificadorDesafio").asText()));

        assertThat(desafioRepositorio.findByIdentificadorDesafio(desafio.get("identificadorDesafio").asText()))
                .get()
                .extracting(DesafioAtestacaoApp::getConsumidoEm)
                .isNotNull();
    }

    @Test
    void deveRecusarSegredoInternoInvalido() throws Exception {
        mockMvc.perform(post("/identidade/atestacoes/interna/desafios")
                        .header("X-Eickrono-Internal-Secret", "segredo-invalido")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "operacao": "LOGIN",
                                  "plataforma": "ANDROID"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode criarDesafio(final String plataforma) throws Exception {
        String corpo = mockMvc.perform(post("/identidade/atestacoes/interna/desafios")
                        .header("X-Eickrono-Internal-Secret", SEGREDO_INTERNO)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "operacao": "LOGIN",
                                  "plataforma": "%s"
                                }
                                """.formatted(plataforma)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(Objects.requireNonNull(corpo));
    }
}
