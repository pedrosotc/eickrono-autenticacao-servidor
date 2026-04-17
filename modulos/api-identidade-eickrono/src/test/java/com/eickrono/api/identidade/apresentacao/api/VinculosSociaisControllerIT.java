package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.dominio.modelo.FormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.VinculoSocial;
import com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.VinculoSocialRepositorio;
import com.eickrono.api.identidade.support.ClienteAdministracaoCadastroKeycloakStubConfiguration;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AplicacaoApiIdentidade.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ClienteAdministracaoCadastroKeycloakStubConfiguration.class)
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
class VinculosSociaisControllerIT {

    private static final String ENDPOINT = "/identidade/vinculos-sociais";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteAdministracaoCadastroKeycloakStubConfiguration keycloakStub;

    @Autowired
    private VinculoSocialRepositorio vinculoSocialRepositorio;

    @Autowired
    private FormaAcessoRepositorio formaAcessoRepositorio;

    @BeforeEach
    void limparEstado() {
        keycloakStub.limparIdentidadesFederadas();
        vinculoSocialRepositorio.deleteAll();
        formaAcessoRepositorio.deleteAll();
    }

    @Test
    void deveSincronizarListarERemoverVinculosSociais() throws Exception {
        keycloakStub.definirIdentidadesFederadas(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com")));

        mockMvc.perform(post(ENDPOINT + "/google/sincronizacao")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].vinculado").value(true))
                .andExpect(jsonPath("$.provedores[0].identificadorMascarado").value("t***@gmail.com"));

        assertThat(vinculoSocialRepositorio.findAll())
                .extracting(VinculoSocial::getProvedor, VinculoSocial::getIdentificador)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("google", "teste@gmail.com"));
        assertThat(formaAcessoRepositorio.findAll().stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .toList())
                .extracting(FormaAcesso::getProvedor, FormaAcesso::getIdentificador)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("GOOGLE", "google-sub-1"));

        mockMvc.perform(get(ENDPOINT)
                        .with(jwtEscopo("vinculos:ler")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].vinculado").value(true));

        mockMvc.perform(delete(ENDPOINT + "/google")
                        .with(jwtEscopo("vinculos:escrever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedores[0].provedor").value("google"))
                .andExpect(jsonPath("$.provedores[0].vinculado").value(false));

        assertThat(vinculoSocialRepositorio.findAll()).isEmpty();
        assertThat(formaAcessoRepositorio.findAll().stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .toList()).isEmpty();
    }

    @Test
    void deveNegarListagemSemEscopo() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .with(jwtSemEscopo()))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtEscopo(final String escopo) {
        return jwt().jwt(builder -> builder
                        .subject("sub-123")
                        .claim("email", "teste@eickrono.com")
                        .claim("name", "Pessoa Teste"))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + Objects.requireNonNull(escopo)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtSemEscopo() {
        return jwt().jwt(builder -> builder
                .subject("sub-123")
                .claim("email", "teste@eickrono.com")
                .claim("name", "Pessoa Teste"));
    }
}
