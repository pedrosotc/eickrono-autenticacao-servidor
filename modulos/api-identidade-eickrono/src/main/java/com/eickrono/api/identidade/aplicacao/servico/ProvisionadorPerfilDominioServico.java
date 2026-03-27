package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilRealizado;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;

public interface ProvisionadorPerfilDominioServico {

    boolean usuarioDisponivel(String usuario);

    ProvisionamentoPerfilRealizado provisionarCadastroConfirmado(CadastroConta cadastroConta);
}
