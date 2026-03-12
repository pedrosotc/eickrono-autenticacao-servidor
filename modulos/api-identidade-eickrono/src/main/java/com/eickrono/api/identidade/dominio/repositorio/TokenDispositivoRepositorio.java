package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenDispositivoRepositorio extends JpaRepository<TokenDispositivo, UUID> {

    List<TokenDispositivo> findByUsuarioSubAndStatus(String usuarioSub, StatusTokenDispositivo status);

    Optional<TokenDispositivo> findByUsuarioSubAndTokenHash(String usuarioSub,
                                                            String tokenHash);

    Optional<TokenDispositivo> findByUsuarioSubAndTokenHashAndStatus(String usuarioSub,
                                                                     String tokenHash,
                                                                     StatusTokenDispositivo status);

    Optional<TokenDispositivo> findByTokenHash(String tokenHash);
}
