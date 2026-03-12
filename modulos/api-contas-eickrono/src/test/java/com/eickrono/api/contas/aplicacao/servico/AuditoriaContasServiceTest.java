package com.eickrono.api.contas.aplicacao.servico;

import java.lang.reflect.Proxy;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eickrono.api.contas.dominio.modelo.AuditoriaAcessoContas;
import com.eickrono.api.contas.dominio.modelo.AuditoriaEventoContas;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaAcessoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaEventoContasRepositorio;

@ExtendWith(MockitoExtension.class)
class AuditoriaContasServiceTest {

    private AuditoriaContasService auditoriaContasService;
    private AuditoriaEventoContas ultimoEvento;
    private AuditoriaAcessoContas ultimoAcesso;

    private AuditoriaEventoContasRepositorio eventoRepositorio() {
        return (AuditoriaEventoContasRepositorio) Proxy.newProxyInstance(
                AuditoriaEventoContasRepositorio.class.getClassLoader(),
                new Class<?>[] {AuditoriaEventoContasRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        ultimoEvento = (AuditoriaEventoContas) Objects.requireNonNull(args)[0];
                        yield ultimoEvento;
                    }
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

    /** Instancia o serviço com repositórios mockados para inspecionar dados persistidos. */
    private void inicializarServico() {
        auditoriaContasService = new AuditoriaContasService(eventoRepositorio(), acessoRepositorio());
    }

    /** Capturamos o objeto persistido garantindo tipo, sujeito e detalhes. */
    @Test
    @DisplayName("deve criar auditoria de evento com todos os campos")
    void deveSalvarEvento() {
        inicializarServico();

        auditoriaContasService.registrarEvento("PIX_ENVIADO", "sub-123", "Transferência PIX");

        AuditoriaEventoContas evento = Objects.requireNonNull(ultimoEvento);
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

        auditoriaContasService.registrarAcesso("sub-123", "/contas", "Listagem");

        AuditoriaAcessoContas acesso = Objects.requireNonNull(ultimoAcesso);
        assertThat(acesso.getSujeito()).isEqualTo("sub-123");
        assertThat(acesso.getEndpoint()).isEqualTo("/contas");
        assertThat(acesso.getDetalhes()).isEqualTo("Listagem");
        assertThat(acesso.getRegistradoEm()).isNotNull();
    }
}
