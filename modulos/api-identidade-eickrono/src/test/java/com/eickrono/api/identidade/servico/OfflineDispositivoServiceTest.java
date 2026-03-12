package com.eickrono.api.identidade.servico;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.eickrono.api.identidade.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.EventoOfflineDispositivo;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusDispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TipoEventoOfflineDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.EventoOfflineDispositivoRepositorio;
import com.eickrono.api.identidade.dto.EventoOfflineDispositivoRequest;
import com.eickrono.api.identidade.dto.PoliticaOfflineDispositivoResponse;
import com.eickrono.api.identidade.dto.RegistrarEventosOfflineRequest;

class OfflineDispositivoServiceTest {

    private static final Clock CLOCK_FIXO = Clock.fixed(Instant.parse("2026-03-11T18:00:00Z"), ZoneOffset.UTC);

    private final List<EventoOfflineDispositivo> eventosPersistidos = new ArrayList<>();
    private final List<AuditoriaEventoIdentidade> auditorias = new ArrayList<>();

    private OfflineDispositivoService service;
    private TokenDispositivo tokenValido;
    private DispositivoIdentidade dispositivo;

    private void inicializarServico(boolean dispositivoConfiavel) {
        eventosPersistidos.clear();
        auditorias.clear();

        DispositivoProperties properties = new DispositivoProperties();
        properties.getOffline().setPermitido(true);
        properties.getOffline().setTempoMaximoMinutos(480);
        properties.getOffline().setExigeReconciliacao(true);
        properties.getOffline().setBloquearQuandoTokenRevogado(true);
        properties.getOffline().setBloquearQuandoTokenExpirado(true);
        properties.getOffline().setBloquearQuandoDispositivoSemConfianca(true);

        Pessoa pessoa = new Pessoa(
                "sub-123",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                OffsetDateTime.now(CLOCK_FIXO));
        RegistroDispositivo registro = new RegistroDispositivo(
                UUID.randomUUID(),
                "sub-123",
                "teste@eickrono.com",
                "+5511999990000",
                "ios|iphone14,3|device",
                "IOS",
                "1.0.0",
                "chave-publica",
                StatusRegistroDispositivo.CONFIRMADO,
                OffsetDateTime.now(CLOCK_FIXO).minusHours(1),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(8));
        dispositivo = new DispositivoIdentidade(
                pessoa,
                registro.getFingerprint(),
                registro.getPlataforma(),
                registro.getVersaoAplicativo().orElse(null),
                registro.getChavePublica().orElse(null),
                dispositivoConfiavel ? StatusDispositivoIdentidade.ATIVO : StatusDispositivoIdentidade.REVOGADO,
                OffsetDateTime.now(CLOCK_FIXO).minusHours(1),
                OffsetDateTime.now(CLOCK_FIXO).minusHours(1));
        tokenValido = new TokenDispositivo(
                UUID.randomUUID(),
                registro,
                dispositivo,
                "sub-123",
                registro.getFingerprint(),
                registro.getPlataforma(),
                registro.getVersaoAplicativo().orElse(null),
                "hash-token",
                StatusTokenDispositivo.ATIVO,
                OffsetDateTime.now(CLOCK_FIXO).minusMinutes(5),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(48));

        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepositorio());
        service = new OfflineDispositivoService(
                properties,
                new TokenDispositivoServiceFake(properties, CLOCK_FIXO, tokenValido),
                new DispositivoIdentidadeServiceFake(CLOCK_FIXO, dispositivo),
                eventoOfflineRepositorio(),
                auditoriaService,
                CLOCK_FIXO);
    }

    @Test
    @DisplayName("deve publicar política central offline do backend")
    void devePublicarPoliticaCentralOffline() {
        inicializarServico(true);

        PoliticaOfflineDispositivoResponse politica = service.obterPolitica();

        assertThat(politica.permitido()).isTrue();
        assertThat(politica.tempoMaximoMinutos()).isEqualTo(480);
        assertThat(politica.exigeReconciliacao()).isTrue();
        assertThat(politica.condicoesBloqueio())
                .containsExactlyInAnyOrder("TOKEN_REVOGADO", "TOKEN_EXPIRADO", "DISPOSITIVO_SEM_CONFIANCA");
    }

    @Test
    @DisplayName("deve registrar eventos offline e auditar a reconciliação")
    void deveRegistrarEventosOffline() {
        inicializarServico(true);
        RegistrarEventosOfflineRequest request = new RegistrarEventosOfflineRequest(List.of(
                new EventoOfflineDispositivoRequest(
                        TipoEventoOfflineDispositivo.MODO_OFFLINE_ATIVADO,
                        OffsetDateTime.now(CLOCK_FIXO).minusMinutes(30),
                        "app entrou em modo offline"),
                new EventoOfflineDispositivoRequest(
                        TipoEventoOfflineDispositivo.RECONCILIACAO_REALIZADA,
                        OffsetDateTime.now(CLOCK_FIXO),
                        "sincronização concluída")));

        service.registrarEventosOffline("sub-123", "token-claro", request);

        assertThat(eventosPersistidos).hasSize(2);
        assertThat(eventosPersistidos)
                .extracting(EventoOfflineDispositivo::getTipoEvento)
                .containsExactly(
                        TipoEventoOfflineDispositivo.MODO_OFFLINE_ATIVADO,
                        TipoEventoOfflineDispositivo.RECONCILIACAO_REALIZADA);
        assertThat(auditorias)
                .extracting(AuditoriaEventoIdentidade::getTipoEvento)
                .contains("DISPOSITIVO_EVENTOS_OFFLINE_REGISTRADOS");
    }

    @Test
    @DisplayName("deve bloquear reconciliação quando o dispositivo estiver sem confiança")
    void deveBloquearReconcilicaoQuandoDispositivoSemConfianca() {
        inicializarServico(false);
        RegistrarEventosOfflineRequest request = new RegistrarEventosOfflineRequest(List.of(
                new EventoOfflineDispositivoRequest(
                        TipoEventoOfflineDispositivo.MODO_OFFLINE_ATIVADO,
                        OffsetDateTime.now(CLOCK_FIXO),
                        "app entrou em modo offline")));

        assertThatThrownBy(() -> service.registrarEventosOffline("sub-123", "token-claro", request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.LOCKED);
    }

    private EventoOfflineDispositivoRepositorio eventoOfflineRepositorio() {
        return (EventoOfflineDispositivoRepositorio) Proxy.newProxyInstance(
                EventoOfflineDispositivoRepositorio.class.getClassLoader(),
                new Class<?>[] {EventoOfflineDispositivoRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> salvarEvento((EventoOfflineDispositivo) Objects.requireNonNull(args)[0]);
                    case "findAll" -> List.copyOf(eventosPersistidos);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "EventoOfflineDispositivoRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio() {
        return (AuditoriaEventoIdentidadeRepositorio) Proxy.newProxyInstance(
                AuditoriaEventoIdentidadeRepositorio.class.getClassLoader(),
                new Class<?>[] {AuditoriaEventoIdentidadeRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> salvarAuditoria((AuditoriaEventoIdentidade) Objects.requireNonNull(args)[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "AuditoriaEventoIdentidadeRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private EventoOfflineDispositivo salvarEvento(EventoOfflineDispositivo evento) {
        eventosPersistidos.add(evento);
        return evento;
    }

    private AuditoriaEventoIdentidade salvarAuditoria(AuditoriaEventoIdentidade auditoria) {
        auditorias.add(auditoria);
        return auditoria;
    }

    private static class TokenDispositivoServiceFake extends TokenDispositivoService {

        private final Optional<TokenDispositivo> token;

        TokenDispositivoServiceFake(DispositivoProperties properties, Clock clock, TokenDispositivo token) {
            super(org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.TokenDispositivoRepositorio.class), properties, clock);
            this.token = Optional.ofNullable(token);
        }

        @Override
        public Optional<TokenDispositivo> validarTokenAtivo(String usuarioSub, String tokenClaro) {
            return token.filter(valor -> Objects.equals(valor.getUsuarioSub(), usuarioSub));
        }
    }

    private static class DispositivoIdentidadeServiceFake extends DispositivoIdentidadeService {

        private final DispositivoIdentidade dispositivo;

        DispositivoIdentidadeServiceFake(Clock clock, DispositivoIdentidade dispositivo) {
            super(
                    org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.DispositivoIdentidadeRepositorio.class),
                    org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.PessoaRepositorio.class),
                    clock);
            this.dispositivo = dispositivo;
        }

        @Override
        public DispositivoIdentidade garantirDispositivoParaToken(TokenDispositivo token) {
            return dispositivo;
        }
    }
}
