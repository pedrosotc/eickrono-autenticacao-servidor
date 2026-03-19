package com.eickrono.servidor.autorizacao.infraestrutura.credenciais;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DerivadorSenhaEickronoTest {

    @Test
    void deveDerivarSenhaConcatenandoSenhaPepperEMarcadorDeCriacao() {
        String derivada = DerivadorSenhaEickrono.derivar("Senha@123", "1710792000000", "pepper-secreto");
        assertEquals("Senha@123pepper-secreto1710792000000", derivada);
    }
}
