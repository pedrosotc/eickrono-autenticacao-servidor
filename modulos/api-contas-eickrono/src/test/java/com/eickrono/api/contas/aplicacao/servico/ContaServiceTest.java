package com.eickrono.api.contas.aplicacao.servico;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eickrono.api.contas.dominio.modelo.AuditoriaAcessoContas;
import com.eickrono.api.contas.dominio.modelo.Conta;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaAcessoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaEventoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.ContaRepositorio;
import com.eickrono.api.contas.apresentacao.dto.ContaResumoDto;

@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    @Mock
    private ContaRepositorio contaRepositorio;
    private AuditoriaContasService auditoriaContasService;
    private ContaService contaService;
    private AuditoriaAcessoContas ultimoAcesso;

    private ContaRepositorio contaRepositorio() {
        return Objects.requireNonNull(contaRepositorio);
    }

    private AuditoriaEventoContasRepositorio eventoRepositorio() {
        return (AuditoriaEventoContasRepositorio) Proxy.newProxyInstance(
                AuditoriaEventoContasRepositorio.class.getClassLoader(),
                new Class<?>[] {AuditoriaEventoContasRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> Objects.requireNonNull(args)[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "AuditoriaEventoContasRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AuditoriaAcessoContasRepositorio acessoRepositorio() {
        return (AuditoriaAcessoContasRepositorio) Proxy.newProxyInstance(
                AuditoriaAcessoContasRepositorio.class.getClassLoader(),
                new Class<?>[] {AuditoriaAcessoContasRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        ultimoAcesso = (AuditoriaAcessoContas) Objects.requireNonNull(args)[0];
                        yield ultimoAcesso;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "AuditoriaAcessoContasRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Prepara o serviço real com auditoria controlada via mocks. */
    private void inicializarServico() {
        auditoriaContasService = new AuditoriaContasService(eventoRepositorio(), acessoRepositorio());
        contaService = new ContaService(contaRepositorio(), auditoriaContasService);
    }

    /** Esperamos o mapeamento correto dos campos da entidade para o DTO retornado. */
    @Test
    @DisplayName("deve mapear entidades para DTOs quando cliente possuir contas")
    void deveMapearEntidadesParaDtos() throws Exception {
        inicializarServico();
        Conta conta = novaConta("123", "cliente-1", BigDecimal.TEN);
        definirId(conta, 10L);
        when(contaRepositorio().findByClienteId("cliente-1")).thenReturn(List.of(conta));

        List<ContaResumoDto> resultado = contaService.listarPorCliente("cliente-1");

        assertThat(resultado).hasSize(1);
        ContaResumoDto dto = resultado.getFirst();
        assertThat(dto.id()).isEqualTo(10L);
        assertThat(dto.numero()).isEqualTo("123");
        assertThat(dto.saldo()).isEqualTo(BigDecimal.TEN);
    }

    /** Quando o cliente é dono da conta, auditoria deve registrar o acesso. */
    @Test
    @DisplayName("deve retornar conta e registrar auditoria quando cliente for titular")
    void deveRetornarContaQuandoClienteForProprietario() throws Exception {
        inicializarServico();
        Conta conta = novaConta("123", "cliente-1", BigDecimal.ONE);
        definirId(conta, 7L);
        when(contaRepositorio().findById(7L)).thenReturn(Optional.of(conta));

        Optional<ContaResumoDto> resultado = contaService.buscarPorId(7L, "cliente-1");

        assertThat(resultado).isPresent();
        ContaResumoDto dto = resultado.orElseThrow();
        assertThat(dto.id()).isEqualTo(7L);

        assertThat(Objects.requireNonNull(ultimoAcesso).getEndpoint()).isEqualTo("/contas/7");
    }

    /** Caso a conta não pertença ao cliente informado, nenhum registro deve ser retornado nem auditado. */
    @Test
    @DisplayName("deve retornar vazio quando conta não pertencer ao cliente")
    void deveRetornarVazioQuandoNaoPertencerAoCliente() throws Exception {
        inicializarServico();
        Conta conta = novaConta("123", "cliente-2", BigDecimal.ONE);
        definirId(conta, 7L);
        when(contaRepositorio().findById(7L)).thenReturn(Optional.of(conta));

        Optional<ContaResumoDto> resultado = contaService.buscarPorId(7L, "cliente-1");

        assertThat(resultado).isEmpty();
        verifyNoInteractions(acessoRepositorio());
    }

    private Conta novaConta(String numero, String clienteId, BigDecimal saldo) {
        OffsetDateTime agora = OffsetDateTime.parse("2024-05-10T09:00:00Z");
        return new Conta(numero, clienteId, saldo, agora.minusDays(10), agora);
    }

    private void definirId(Conta conta, Long id) throws Exception {
        Field field = Conta.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(conta, id);
    }
}
