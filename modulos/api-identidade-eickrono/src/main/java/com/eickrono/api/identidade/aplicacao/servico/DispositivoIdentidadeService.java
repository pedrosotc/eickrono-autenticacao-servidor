package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusDispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.DispositivoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PessoaRepositorio;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Mantém a identidade explícita do aparelho vinculada à pessoa.
 */
@Service
public class DispositivoIdentidadeService {

    private final DispositivoIdentidadeRepositorio dispositivoRepositorio;
    private final PessoaRepositorio pessoaRepositorio;
    private final Clock clock;

    public DispositivoIdentidadeService(DispositivoIdentidadeRepositorio dispositivoRepositorio,
                                        PessoaRepositorio pessoaRepositorio,
                                        Clock clock) {
        this.dispositivoRepositorio = dispositivoRepositorio;
        this.pessoaRepositorio = pessoaRepositorio;
        this.clock = clock;
    }

    @Transactional
    public DispositivoIdentidade garantirDispositivo(Pessoa pessoa, RegistroDispositivo registro) {
        Pessoa pessoaObrigatoria = Objects.requireNonNull(pessoa, "pessoa é obrigatória");
        RegistroDispositivo registroObrigatorio = Objects.requireNonNull(registro, "registro é obrigatório");
        OffsetDateTime agora = OffsetDateTime.now(clock);

        DispositivoIdentidade dispositivo = dispositivoRepositorio
                .findByPessoaAndFingerprint(pessoaObrigatoria, registroObrigatorio.getFingerprint())
                .orElseGet(() -> new DispositivoIdentidade(
                        pessoaObrigatoria,
                        registroObrigatorio.getFingerprint(),
                        registroObrigatorio.getPlataforma(),
                        registroObrigatorio.getVersaoAplicativo().orElse(null),
                        registroObrigatorio.getChavePublica().orElse(null),
                        StatusDispositivoIdentidade.ATIVO,
                        agora,
                        agora));

        dispositivo.atualizarMetadados(
                registroObrigatorio.getPlataforma(),
                registroObrigatorio.getVersaoAplicativo().orElse(null),
                registroObrigatorio.getChavePublica().orElse(null),
                agora);

        return dispositivoRepositorio.save(dispositivo);
    }

    @Transactional
    public DispositivoIdentidade garantirDispositivoParaToken(TokenDispositivo token) {
        TokenDispositivo tokenObrigatorio = Objects.requireNonNull(token, "token é obrigatório");
        return tokenObrigatorio.getDispositivo().orElseGet(() -> {
            Pessoa pessoa = pessoaRepositorio.findBySub(tokenObrigatorio.getUsuarioSub())
                    .orElseThrow(() -> new IllegalStateException("Pessoa não encontrada para o token do dispositivo"));
            return garantirDispositivo(pessoa, tokenObrigatorio.getRegistro());
        });
    }
}
