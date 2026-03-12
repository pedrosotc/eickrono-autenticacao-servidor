package com.eickrono.api.contas;

import static org.mockito.Mockito.when;

import com.eickrono.api.contas.support.InfraestruturaTesteContas;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = AplicacaoApiContas.class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = InfraestruturaTesteContas.Initializer.class)
class AplicacaoApiContasTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void deveCarregarContexto() {
        when(jwtDecoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "none").claim("sub", "test").build());
    }
}
