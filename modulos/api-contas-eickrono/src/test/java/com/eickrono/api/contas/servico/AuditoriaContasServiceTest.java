package com.eickrono.api.contas.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.contas.dominio.modelo.AuditoriaAcessoContas;
import com.eickrono.api.contas.dominio.modelo.AuditoriaEventoContas;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaAcessoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaEventoContasRepositorio;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaContasServiceTest {

    @Mock
    private AuditoriaEventoContasRepositorio eventoRepositorio;
    @Mock
    private AuditoriaAcessoContasRepositorio acessoRepositorio;

    private AuditoriaContasService auditoriaContasService;

    private AuditoriaEventoContasRepositorio eventoRepositorio() {
        return Objects.requireNonNull(eventoRepositorio);
    }

    private AuditoriaAcessoContasRepositorio acessoRepositorio() {
        return Objects.requireNonNull(acessoRepositorio);
    }

    /** Instancia o serviço com repositórios mockados para inspecionar dados persistidos. */
    private void inicializarServico() {
        auditoriaContasService = new AuditoriaContasService(eventoRepositorio(), acessoRepositorio());
    }

    private AuditoriaEventoContas eventoSalvo(InvocationOnMock invocation) {
        return Objects.requireNonNull(invocation.getArgument(0, AuditoriaEventoContas.class));
    }

    private AuditoriaAcessoContas acessoSalvo(InvocationOnMock invocation) {
        return Objects.requireNonNull(invocation.getArgument(0, AuditoriaAcessoContas.class));
    }

    private static <T> T anyValue(Class<T> tipo) {
        return any(tipo);
    }

    /** Capturamos o objeto persistido garantindo tipo, sujeito e detalhes. */
    @Test
    @DisplayName("deve criar auditoria de evento com todos os campos")
    void deveSalvarEvento() {
        inicializarServico();
        when(eventoRepositorio().save(Objects.requireNonNull(anyValue(AuditoriaEventoContas.class))))
                .thenAnswer(this::eventoSalvo);

        auditoriaContasService.registrarEvento("PIX_ENVIADO", "sub-123", "Transferência PIX");

        ArgumentCaptor<AuditoriaEventoContas> captor = ArgumentCaptor.forClass(AuditoriaEventoContas.class);
        verify(eventoRepositorio()).save(Objects.requireNonNull(captor.capture()));
        AuditoriaEventoContas evento = Objects.requireNonNull(captor.getValue());
        assertThat(evento.getTipoEvento()).isEqualTo("PIX_ENVIADO");
        assertThat(evento.getSujeito()).isEqualTo("sub-123");
        assertThat(evento.getDetalhes()).isEqualTo("Transferência PIX");
        assertThat(evento.getRegistradoEm()).isNotNull();
    }

    /** Garante que o endpoint e o sujeito sejam registrados com timestamp atual. */
    @Test
    @DisplayName("deve criar auditoria de acesso")
    void deveSalvarAcesso() {
        inicializarServico();
        when(acessoRepositorio().save(Objects.requireNonNull(anyValue(AuditoriaAcessoContas.class))))
                .thenAnswer(this::acessoSalvo);

        auditoriaContasService.registrarAcesso("sub-123", "/contas", "Listagem");

        ArgumentCaptor<AuditoriaAcessoContas> captor = ArgumentCaptor.forClass(AuditoriaAcessoContas.class);
        verify(acessoRepositorio()).save(Objects.requireNonNull(captor.capture()));
        AuditoriaAcessoContas acesso = Objects.requireNonNull(captor.getValue());
        assertThat(acesso.getSujeito()).isEqualTo("sub-123");
        assertThat(acesso.getEndpoint()).isEqualTo("/contas");
        assertThat(acesso.getDetalhes()).isEqualTo("Listagem");
        assertThat(acesso.getRegistradoEm()).isNotNull();
    }
}
