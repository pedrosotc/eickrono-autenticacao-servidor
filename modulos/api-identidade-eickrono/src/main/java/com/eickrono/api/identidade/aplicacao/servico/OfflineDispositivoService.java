package com.eickrono.api.identidade.aplicacao.servico;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.EventoOfflineDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.EventoOfflineDispositivoRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.EventoOfflineDispositivoRequest;
import com.eickrono.api.identidade.apresentacao.dto.PoliticaOfflineDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RegistrarEventosOfflineRequest;

import jakarta.transaction.Transactional;

/**
 * Publica a politica offline do backend e registra a reconciliacao de eventos offline.
 */
@Service
public class OfflineDispositivoService {

    private final DispositivoProperties dispositivoProperties;
    private final TokenDispositivoService tokenDispositivoService;
    private final DispositivoIdentidadeService dispositivoIdentidadeService;
    private final EventoOfflineDispositivoRepositorio eventoOfflineRepositorio;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public OfflineDispositivoService(DispositivoProperties dispositivoProperties,
                                     TokenDispositivoService tokenDispositivoService,
                                     DispositivoIdentidadeService dispositivoIdentidadeService,
                                     EventoOfflineDispositivoRepositorio eventoOfflineRepositorio,
                                     AuditoriaService auditoriaService,
                                     Clock clock) {
        this.dispositivoProperties = dispositivoProperties;
        this.tokenDispositivoService = tokenDispositivoService;
        this.dispositivoIdentidadeService = dispositivoIdentidadeService;
        this.eventoOfflineRepositorio = eventoOfflineRepositorio;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    public PoliticaOfflineDispositivoResponse obterPolitica() {
        DispositivoProperties.Offline offline = dispositivoProperties.getOffline();
        List<String> condicoes = new ArrayList<>();
        if (offline.isBloquearQuandoTokenRevogado()) {
            condicoes.add("TOKEN_REVOGADO");
        }
        if (offline.isBloquearQuandoTokenExpirado()) {
            condicoes.add("TOKEN_EXPIRADO");
        }
        if (offline.isBloquearQuandoDispositivoSemConfianca()) {
            condicoes.add("DISPOSITIVO_SEM_CONFIANCA");
        }
        return new PoliticaOfflineDispositivoResponse(
                offline.isPermitido(),
                offline.getTempoMaximoMinutos(),
                offline.isExigeReconciliacao(),
                List.copyOf(condicoes));
    }

    @Transactional
    public void registrarEventosOffline(String usuarioSub,
                                        String tokenDispositivo,
                                        RegistrarEventosOfflineRequest request) {
        Objects.requireNonNull(usuarioSub, "usuarioSub é obrigatório");
        Objects.requireNonNull(tokenDispositivo, "tokenDispositivo é obrigatório");

        List<EventoOfflineDispositivoRequest> eventos = Objects.requireNonNull(request, "request é obrigatório").eventos();
        if (eventos == null || eventos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum evento offline informado");
        }

        TokenDispositivo token = tokenDispositivoService.validarTokenAtivo(usuarioSub, tokenDispositivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.LOCKED,
                        "Token de dispositivo inválido para reconciliação offline"));
        DispositivoIdentidade dispositivo = dispositivoIdentidadeService.garantirDispositivoParaToken(token);

        if (!dispositivo.estaConfiavel() && dispositivoProperties.getOffline().isBloquearQuandoDispositivoSemConfianca()) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Dispositivo sem confiança para reconciliação offline");
        }

        OffsetDateTime registradoEm = OffsetDateTime.now(clock);
        for (EventoOfflineDispositivoRequest eventoRequest : eventos) {
            if (eventoRequest == null || eventoRequest.tipoEvento() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento offline inválido");
            }
            OffsetDateTime ocorridoEm = Objects.requireNonNullElse(eventoRequest.ocorridoEm(), registradoEm);
            EventoOfflineDispositivo evento = new EventoOfflineDispositivo(
                    UUID.randomUUID(),
                    dispositivo,
                    token,
                    Objects.requireNonNull(eventoRequest.tipoEvento()),
                    eventoRequest.detalhes(),
                    ocorridoEm,
                    registradoEm);
            eventoOfflineRepositorio.save(evento);
        }

        auditoriaService.registrarEvento(
                "DISPOSITIVO_EVENTOS_OFFLINE_REGISTRADOS",
                usuarioSub,
                "Eventos offline reconciliados: " + eventos.size());
    }
}
