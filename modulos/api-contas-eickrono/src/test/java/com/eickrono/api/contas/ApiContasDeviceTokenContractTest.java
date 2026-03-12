package com.eickrono.api.contas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.contas.support.InfraestruturaTesteContas;
import com.eickrono.api.contas.configuracao.ResultadoValidacaoTokenDispositivoRemoto;
import com.eickrono.api.contas.configuracao.ValidacaoTokenDispositivoResponse;
import com.eickrono.api.contas.configuracao.ValidadorTokenDispositivoRemoto;
import com.eickrono.api.contas.dto.ContaResumoDto;
import com.eickrono.api.contas.dto.TransacaoDto;
import com.eickrono.api.contas.servico.ContaService;
import com.eickrono.api.contas.servico.TransacaoService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(classes = AplicacaoApiContas.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = InfraestruturaTesteContas.Initializer.class)
class ApiContasDeviceTokenContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValidadorTokenDispositivoRemoto validadorTokenDispositivoRemoto;

    @MockBean
    private ContaService contaService;

    @MockBean
    private TransacaoService transacaoService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc() {
        return Objects.requireNonNull(mockMvc);
    }

    private RequestPostProcessor clienteJwt(String tokenValue, String scope) {
        return Objects.requireNonNull(jwt().jwt(builder -> builder.subject("usuario-123").tokenValue(tokenValue))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_cliente"),
                        new SimpleGrantedAuthority(scope)));
    }

    private JwtDecoder jwtDecoder() {
        return Objects.requireNonNull(jwtDecoder);
    }

    private Jwt jwtDecodificado(String tokenValue, String scope) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .claim("sub", "usuario-123")
                .claim("preferred_username", "usuario.123")
                .claim("scope", scope.replace("SCOPE_", ""))
                .claim("realm_access", Map.of("roles", List.of("cliente")))
                .build();
    }

    @Test
    void deveRetornar428QuandoContasNaoRecebeDeviceToken() throws Exception {
        when(jwtDecoder().decode("token-sem-device"))
                .thenReturn(jwtDecodificado("token-sem-device", "SCOPE_contas:ler"));
        MvcResult resultado = mockMvc().perform(get("/contas")
                        .with(Objects.requireNonNull(clienteJwt("token-sem-device", "SCOPE_contas:ler"))))
                .andExpect(status().isPreconditionRequired())
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).contains("DEVICE_TOKEN_MISSING");
    }

    @Test
    void deveRetornar423QuandoContasRecebeDeviceTokenInvalido() throws Exception {
        when(jwtDecoder().decode("token-teste"))
                .thenReturn(jwtDecodificado("token-teste", "SCOPE_contas:ler"));
        when(validadorTokenDispositivoRemoto.validar(eq("Bearer token-teste"), eq("token-invalido")))
                .thenReturn(new ResultadoValidacaoTokenDispositivoRemoto(
                        423,
                        new ValidacaoTokenDispositivoResponse(false,
                                "DEVICE_TOKEN_INVALID",
                                "Token de dispositivo invalido",
                                null)));

        MvcResult resultado = mockMvc().perform(get("/contas")
                        .with(Objects.requireNonNull(clienteJwt("token-teste", "SCOPE_contas:ler")))
                        .header("Authorization", "Bearer token-teste")
                        .header("X-Device-Token", "token-invalido"))
                .andExpect(status().isLocked())
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).contains("DEVICE_TOKEN_INVALID");
    }

    @Test
    void devePermitirContasQuandoDeviceTokenForValido() throws Exception {
        when(jwtDecoder().decode("token-teste"))
                .thenReturn(jwtDecodificado("token-teste", "SCOPE_contas:ler"));
        when(validadorTokenDispositivoRemoto.validar(eq("Bearer token-teste"), eq("token-valido")))
                .thenReturn(new ResultadoValidacaoTokenDispositivoRemoto(
                        200,
                        new ValidacaoTokenDispositivoResponse(true,
                                "DEVICE_TOKEN_VALID",
                                "Token de dispositivo valido",
                                OffsetDateTime.parse("2026-03-11T12:00:00Z"))));
        when(contaService.listarPorCliente("usuario-123"))
                .thenReturn(List.of(new ContaResumoDto(1L, "0001", BigDecimal.TEN, OffsetDateTime.parse("2026-03-11T10:00:00Z"))));

        mockMvc().perform(get("/contas")
                        .with(Objects.requireNonNull(clienteJwt("token-teste", "SCOPE_contas:ler")))
                        .header("Authorization", "Bearer token-teste")
                        .header("X-Device-Token", "token-valido"))
                .andExpect(status().isOk());
    }

    @Test
    void deveExigirDeviceTokenTambemEmTransacoes() throws Exception {
        when(jwtDecoder().decode("token-teste"))
                .thenReturn(jwtDecodificado("token-teste", "SCOPE_transacoes:ler"));
        when(validadorTokenDispositivoRemoto.validar(eq("Bearer token-teste"), eq("token-valido")))
                .thenReturn(new ResultadoValidacaoTokenDispositivoRemoto(
                        200,
                        new ValidacaoTokenDispositivoResponse(true,
                                "DEVICE_TOKEN_VALID",
                                "Token de dispositivo valido",
                                OffsetDateTime.parse("2026-03-11T12:00:00Z"))));
        when(transacaoService.listarPorConta(1L, "usuario-123"))
                .thenReturn(List.of(new TransacaoDto(1L, "DEBITO", BigDecimal.ONE, OffsetDateTime.parse("2026-03-11T10:00:00Z"), "teste")));

        mockMvc().perform(get("/transacoes")
                        .with(Objects.requireNonNull(clienteJwt("token-teste", "SCOPE_transacoes:ler")))
                        .queryParam("contaId", "1")
                        .header("Authorization", "Bearer token-teste")
                        .header("X-Device-Token", "token-valido"))
                .andExpect(status().isOk());
    }
}
