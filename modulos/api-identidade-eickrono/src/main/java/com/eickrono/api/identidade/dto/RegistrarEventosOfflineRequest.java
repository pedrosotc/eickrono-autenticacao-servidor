package com.eickrono.api.identidade.dto;

import java.util.List;

/**
 * Payload de reconciliação de eventos offline.
 */
public record RegistrarEventosOfflineRequest(List<EventoOfflineDispositivoRequest> eventos) {
}
