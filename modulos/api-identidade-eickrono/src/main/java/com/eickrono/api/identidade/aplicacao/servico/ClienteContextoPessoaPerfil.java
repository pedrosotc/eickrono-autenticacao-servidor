package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfil;
import java.util.Optional;

public interface ClienteContextoPessoaPerfil {

    Optional<ContextoPessoaPerfil> buscarPorPessoaId(Long pessoaId);

    Optional<ContextoPessoaPerfil> buscarPorSub(String sub);

    Optional<ContextoPessoaPerfil> buscarPorEmail(String email);
}
