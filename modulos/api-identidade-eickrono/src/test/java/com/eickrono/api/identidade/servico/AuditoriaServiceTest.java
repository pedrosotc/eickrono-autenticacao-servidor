package com.eickrono.api.identidade.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio;

    private AuditoriaService auditoriaService;

    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio() {
        return Objects.requireNonNull(auditoriaRepositorio);
    }

    /**
     * Instancia o serviço real com o repositório mockado para capturar exatamente o que é persistido.
     */
    private void inicializarServico() {
        auditoriaService = new AuditoriaService(auditoriaRepositorio());
    }

    private AuditoriaEventoIdentidade auditoriaEvento(InvocationOnMock invocation) {
        return Objects.requireNonNull(invocation.getArgument(0, AuditoriaEventoIdentidade.class));
    }

    private static <T> T anyValue(Class<T> tipo) {
        return any(tipo);
    }

    /**
     * Valida que registrarEvento cria a entidade com tipo, sujeito, detalhes e timestamp preenchidos.
     * Utilizamos ArgumentCaptor para inspecionar o objeto entregue ao repositório.
     */
    @Test
    @DisplayName("deve persistir evento de auditoria com dados completos")
    void devePersistirEvento() {
        inicializarServico();
        when(auditoriaRepositorio().save(Objects.requireNonNull(anyValue(AuditoriaEventoIdentidade.class))))
                .thenAnswer(this::auditoriaEvento);

        auditoriaService.registrarEvento("PERFIL_CONSULTADO", "sub-123", "Consulta de perfil");

        ArgumentCaptor<AuditoriaEventoIdentidade> captor = ArgumentCaptor.forClass(AuditoriaEventoIdentidade.class);
        verify(auditoriaRepositorio()).save(Objects.requireNonNull(captor.capture()));
        AuditoriaEventoIdentidade salvo = Objects.requireNonNull(captor.getValue());
        assertThat(salvo.getTipoEvento()).isEqualTo("PERFIL_CONSULTADO");
        assertThat(salvo.getSujeito()).isEqualTo("sub-123");
        assertThat(salvo.getDetalhes()).isEqualTo("Consulta de perfil");
        assertThat(salvo.getRegistradoEm()).isNotNull();
    }
}
