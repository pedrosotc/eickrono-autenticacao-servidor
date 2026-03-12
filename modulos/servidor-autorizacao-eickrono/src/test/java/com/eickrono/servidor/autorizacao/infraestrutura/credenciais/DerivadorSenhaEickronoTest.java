package com.eickrono.servidor.autorizacao.infraestrutura.credenciais;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DerivadorSenhaEickronoTest {

    @Test
    void deveDerivarSenhaConcatenandoSenhaPepperEDataNascimento() {
        String derivada = DerivadorSenhaEickrono.derivar("Senha@123", "1990-05-20", "pepper-secreto");
        assertEquals("Senha@123pepper-secreto1990-05-20", derivada);
    }
}
