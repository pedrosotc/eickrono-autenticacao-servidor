package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.apresentacao.dto.VinculosSociaisDto;
import com.eickrono.api.identidade.dominio.modelo.FormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.VinculoSocial;
import com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.VinculoSocialRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.VinculoSocialDto;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Serviço para manutenção de vínculos sociais.
 */
@Service
public class VinculoSocialService {

    private final PerfilIdentidadeRepositorio perfilRepositorio;
    private final VinculoSocialRepositorio vinculoRepositorio;
    private final FormaAcessoRepositorio formaAcessoRepositorio;
    private final AuditoriaService auditoriaService;
    private final ProvisionamentoIdentidadeService provisionamentoIdentidadeService;
    private final ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak;

    public VinculoSocialService(PerfilIdentidadeRepositorio perfilRepositorio,
                                VinculoSocialRepositorio vinculoRepositorio,
                                FormaAcessoRepositorio formaAcessoRepositorio,
                                AuditoriaService auditoriaService,
                                ProvisionamentoIdentidadeService provisionamentoIdentidadeService,
                                ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak) {
        this.perfilRepositorio = perfilRepositorio;
        this.vinculoRepositorio = vinculoRepositorio;
        this.formaAcessoRepositorio = formaAcessoRepositorio;
        this.auditoriaService = auditoriaService;
        this.provisionamentoIdentidadeService = provisionamentoIdentidadeService;
        this.clienteAdministracaoVinculosSociaisKeycloak = clienteAdministracaoVinculosSociaisKeycloak;
    }

    @Transactional
    public VinculosSociaisDto listar(final Jwt jwt) {
        PerfilIdentidade perfil = provisionarELocalizarPerfil(Objects.requireNonNull(jwt, "jwt é obrigatório"));
        return montarResposta(vinculoRepositorio.findByPerfil(perfil));
    }

    @Transactional
    public VinculosSociaisDto sincronizar(final Jwt jwt, final String aliasProvedor) {
        ProvedorVinculoSocial provedor = validarProvedor(aliasProvedor);
        Pessoa pessoa = provisionamentoIdentidadeService.provisionarOuAtualizar(Objects.requireNonNull(jwt, "jwt é obrigatório"));
        PerfilIdentidade perfil = localizarPerfil(jwt.getSubject());
        OffsetDateTime instanteSincronizacao = OffsetDateTime.now();
        List<IdentidadeFederadaKeycloak> identidadesFederadas = clienteAdministracaoVinculosSociaisKeycloak
                .listarIdentidadesFederadas(jwt.getSubject());
        reconciliarVinculosSociais(perfil, identidadesFederadas, instanteSincronizacao);
        reconciliarFormasAcessoSociais(pessoa, identidadesFederadas, instanteSincronizacao);
        auditoriaService.registrarEvento(
                "VINCULO_SOCIAL_SINCRONIZADO",
                jwt.getSubject(),
                "Provedor=" + provedor.getAliasApi() + ", vinculado=" + estaVinculado(identidadesFederadas, provedor));
        return montarResposta(vinculoRepositorio.findByPerfil(perfil));
    }

    @Transactional
    public VinculosSociaisDto remover(final Jwt jwt, final String aliasProvedor) {
        ProvedorVinculoSocial provedor = validarProvedor(aliasProvedor);
        Objects.requireNonNull(jwt, "jwt é obrigatório");
        Pessoa pessoa = provisionamentoIdentidadeService.provisionarOuAtualizar(jwt);
        PerfilIdentidade perfil = localizarPerfil(jwt.getSubject());
        OffsetDateTime instanteSincronizacao = OffsetDateTime.now();
        clienteAdministracaoVinculosSociaisKeycloak.removerIdentidadeFederada(jwt.getSubject(), provedor);
        List<IdentidadeFederadaKeycloak> identidadesFederadas = clienteAdministracaoVinculosSociaisKeycloak
                .listarIdentidadesFederadas(jwt.getSubject());
        reconciliarVinculosSociais(perfil, identidadesFederadas, instanteSincronizacao);
        reconciliarFormasAcessoSociais(pessoa, identidadesFederadas, instanteSincronizacao);
        auditoriaService.registrarEvento("VINCULO_SOCIAL_REMOVIDO", jwt.getSubject(),
                "Provedor=" + provedor.getAliasApi());
        return montarResposta(vinculoRepositorio.findByPerfil(perfil));
    }

    private PerfilIdentidade provisionarELocalizarPerfil(Jwt jwt) {
        provisionamentoIdentidadeService.provisionarOuAtualizar(jwt);
        return localizarPerfil(jwt.getSubject());
    }

    private PerfilIdentidade localizarPerfil(String sub) {
        return perfilRepositorio.findBySub(sub)
                .orElseThrow(() -> new IllegalArgumentException("Perfil não encontrado para o usuário informado"));
    }

    private void reconciliarVinculosSociais(final PerfilIdentidade perfil,
                                            final List<IdentidadeFederadaKeycloak> identidadesFederadas,
                                            final OffsetDateTime instanteSincronizacao) {
        Map<ProvedorVinculoSocial, VinculoSocial> existentes = vinculoRepositorio.findByPerfil(perfil).stream()
                .filter(vinculo -> resolverProvedor(vinculo.getProvedor()).isPresent())
                .collect(LinkedHashMap::new,
                        (mapa, vinculo) -> resolverProvedor(vinculo.getProvedor())
                                .ifPresent(provedor -> mapa.put(provedor, vinculo)),
                        Map::putAll);
        Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> remotos = indexarPorProvedor(identidadesFederadas);

        for (ProvedorVinculoSocial provedor : ProvedorVinculoSocial.values()) {
            IdentidadeFederadaKeycloak identidadeFederada = remotos.get(provedor);
            VinculoSocial existente = existentes.get(provedor);
            if (identidadeFederada == null) {
                continue;
            }
            String identificadorExibicao = identidadeFederada.identificadorExibicao();
            if (existente == null) {
                vinculoRepositorio.save(new VinculoSocial(
                        perfil,
                        provedor.getAliasApi(),
                        identificadorExibicao,
                        instanteSincronizacao));
                continue;
            }
            existente.atualizarIdentificador(identificadorExibicao);
            vinculoRepositorio.save(existente);
        }

        List<VinculoSocial> obsoletos = existentes.entrySet().stream()
                .filter(entry -> !remotos.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!obsoletos.isEmpty()) {
            vinculoRepositorio.deleteAll(obsoletos);
        }
    }

    private void reconciliarFormasAcessoSociais(final Pessoa pessoa,
                                                final List<IdentidadeFederadaKeycloak> identidadesFederadas,
                                                final OffsetDateTime instanteSincronizacao) {
        Map<ProvedorVinculoSocial, FormaAcesso> existentes = formaAcessoRepositorio.findByPessoa(pessoa).stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .filter(forma -> ProvedorVinculoSocial.fromAlias(forma.getProvedor().toLowerCase(Locale.ROOT)).isPresent())
                .collect(LinkedHashMap::new,
                        (mapa, forma) -> ProvedorVinculoSocial.fromAlias(forma.getProvedor().toLowerCase(Locale.ROOT))
                                .ifPresent(provedor -> mapa.put(provedor, forma)),
                        Map::putAll);
        Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> remotos = indexarPorProvedor(identidadesFederadas);

        for (ProvedorVinculoSocial provedor : ProvedorVinculoSocial.values()) {
            IdentidadeFederadaKeycloak identidadeFederada = remotos.get(provedor);
            FormaAcesso existente = existentes.get(provedor);
            if (identidadeFederada == null) {
                continue;
            }
            String identificadorCanonico = identidadeFederada.identificadorCanonico();
            Optional<FormaAcesso> conflito = formaAcessoRepositorio.findByTipoAndProvedorAndIdentificador(
                    TipoFormaAcesso.SOCIAL,
                    provedor.getAliasFormaAcesso(),
                    identificadorCanonico);
            if (conflito.isPresent() && !Objects.equals(conflito.orElseThrow().getPessoa().getId(), pessoa.getId())) {
                throw new IllegalStateException("Forma de acesso social já vinculada a outra pessoa");
            }
            if (existente == null) {
                formaAcessoRepositorio.save(new FormaAcesso(
                        pessoa,
                        TipoFormaAcesso.SOCIAL,
                        provedor.getAliasFormaAcesso(),
                        identificadorCanonico,
                        false,
                        instanteSincronizacao,
                        instanteSincronizacao));
                continue;
            }
            existente.atualizarIdentificador(identificadorCanonico, false, instanteSincronizacao);
            formaAcessoRepositorio.save(existente);
        }

        List<FormaAcesso> obsoletos = existentes.entrySet().stream()
                .filter(entry -> !remotos.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!obsoletos.isEmpty()) {
            formaAcessoRepositorio.deleteAll(obsoletos);
        }
    }

    private Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> indexarPorProvedor(
            final List<IdentidadeFederadaKeycloak> identidadesFederadas) {
        Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> remotos = new LinkedHashMap<>();
        for (IdentidadeFederadaKeycloak identidadeFederada : identidadesFederadas) {
            remotos.put(identidadeFederada.provedor(), identidadeFederada);
        }
        return remotos;
    }

    private VinculosSociaisDto montarResposta(final List<VinculoSocial> vinculosPersistidos) {
        Map<ProvedorVinculoSocial, VinculoSocial> vinculosPorProvedor = vinculosPersistidos.stream()
                .filter(vinculo -> resolverProvedor(vinculo.getProvedor()).isPresent())
                .collect(LinkedHashMap::new,
                        (mapa, vinculo) -> resolverProvedor(vinculo.getProvedor())
                                .ifPresent(provedor -> mapa.put(provedor, vinculo)),
                        Map::putAll);
        List<VinculoSocialDto> provedores = Arrays.stream(ProvedorVinculoSocial.values())
                .map(provedor -> {
                    VinculoSocial vinculo = vinculosPorProvedor.get(provedor);
                    return new VinculoSocialDto(
                            provedor.getAliasApi(),
                            true,
                            vinculo != null,
                            vinculo == null ? null : vinculo.getVinculadoEm(),
                            vinculo == null ? null : mascararIdentificador(vinculo.getIdentificador()));
                })
                .toList();
        return new VinculosSociaisDto(provedores);
    }

    private ProvedorVinculoSocial validarProvedor(final String aliasProvedor) {
        return resolverProvedor(aliasProvedor)
                .orElseThrow(() -> new ResponseStatusException(
                        BAD_REQUEST,
                        "Provedor social não suportado: " + aliasProvedor));
    }

    private Optional<ProvedorVinculoSocial> resolverProvedor(final String aliasProvedor) {
        if (aliasProvedor == null || aliasProvedor.isBlank()) {
            return Optional.empty();
        }
        return ProvedorVinculoSocial.fromAlias(aliasProvedor.trim().toLowerCase(Locale.ROOT));
    }

    private boolean estaVinculado(final List<IdentidadeFederadaKeycloak> identidadesFederadas,
                                  final ProvedorVinculoSocial provedor) {
        return identidadesFederadas.stream()
                .map(IdentidadeFederadaKeycloak::provedor)
                .anyMatch(provedor::equals);
    }

    private String mascararIdentificador(final String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return null;
        }
        String valor = identificador.trim();
        int indiceArroba = valor.indexOf('@');
        if (indiceArroba > 0) {
            String inicio = valor.substring(0, 1);
            return inicio + "***" + valor.substring(indiceArroba);
        }
        if (valor.length() == 1) {
            return "*";
        }
        if (valor.length() == 2) {
            return valor.substring(0, 1) + "*";
        }
        return valor.substring(0, 1) + "***" + valor.substring(valor.length() - 1);
    }
}
