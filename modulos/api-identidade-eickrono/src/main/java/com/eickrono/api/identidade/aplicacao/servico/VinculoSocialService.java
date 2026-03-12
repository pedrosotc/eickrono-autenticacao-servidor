package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.VinculoSocial;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.VinculoSocialRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.CriarVinculoSocialRequisicao;
import com.eickrono.api.identidade.apresentacao.dto.VinculoSocialDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço para manutenção de vínculos sociais.
 */
@Service
public class VinculoSocialService {

    private final PerfilIdentidadeRepositorio perfilRepositorio;
    private final VinculoSocialRepositorio vinculoRepositorio;
    private final AuditoriaService auditoriaService;
    private final ProvisionamentoIdentidadeService provisionamentoIdentidadeService;

    public VinculoSocialService(PerfilIdentidadeRepositorio perfilRepositorio,
                                VinculoSocialRepositorio vinculoRepositorio,
                                AuditoriaService auditoriaService,
                                ProvisionamentoIdentidadeService provisionamentoIdentidadeService) {
        this.perfilRepositorio = perfilRepositorio;
        this.vinculoRepositorio = vinculoRepositorio;
        this.auditoriaService = auditoriaService;
        this.provisionamentoIdentidadeService = provisionamentoIdentidadeService;
    }

    @Transactional(readOnly = true)
    public List<VinculoSocialDto> listar(Jwt jwt) {
        PerfilIdentidade perfil = provisionarELocalizarPerfil(jwt);
        return vinculoRepositorio.findByPerfil(perfil)
                .stream()
                .map(vinculo -> new VinculoSocialDto(
                        vinculo.getId(),
                        vinculo.getProvedor(),
                        vinculo.getIdentificador(),
                        vinculo.getVinculadoEm()))
                .collect(Collectors.toList());
    }

    @Transactional
    public VinculoSocialDto criar(Jwt jwt, CriarVinculoSocialRequisicao requisicao) {
        Pessoa pessoa = provisionamentoIdentidadeService.provisionarOuAtualizar(jwt);
        PerfilIdentidade perfil = localizarPerfil(jwt.getSubject());
        VinculoSocial novoVinculo = new VinculoSocial(
                perfil,
                requisicao.provedor(),
                requisicao.identificador(),
                OffsetDateTime.now());
        VinculoSocial salvo = vinculoRepositorio.save(novoVinculo);
        provisionamentoIdentidadeService.registrarFormaAcessoSocial(
                pessoa,
                requisicao.provedor(),
                requisicao.identificador(),
                salvo.getVinculadoEm());
        auditoriaService.registrarEvento("VINCULO_SOCIAL_CRIADO", jwt.getSubject(),
                "Provedor=" + requisicao.provedor());
        return new VinculoSocialDto(
                salvo.getId(),
                salvo.getProvedor(),
                salvo.getIdentificador(),
                salvo.getVinculadoEm());
    }

    private PerfilIdentidade provisionarELocalizarPerfil(Jwt jwt) {
        provisionamentoIdentidadeService.provisionarOuAtualizar(jwt);
        return localizarPerfil(jwt.getSubject());
    }

    private PerfilIdentidade localizarPerfil(String sub) {
        return perfilRepositorio.findBySub(sub)
                .orElseThrow(() -> new IllegalArgumentException("Perfil não encontrado para o usuário informado"));
    }
}
