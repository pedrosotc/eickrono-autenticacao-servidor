package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoIdentidadeRepositorio extends JpaRepository<DispositivoIdentidade, Long> {

    Optional<DispositivoIdentidade> findByPessoaAndFingerprint(Pessoa pessoa, String fingerprint);
}
