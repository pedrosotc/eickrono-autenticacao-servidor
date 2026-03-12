package com.eickrono.api.contas.configuracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige e valida remotamente o X-Device-Token em APIs user-facing.
 */
@Component
public class DeviceTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_DEVICE_TOKEN = "X-Device-Token";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ValidadorTokenDispositivoRemoto validadorTokenDispositivoRemoto;
    private final ObjectMapper objectMapper;

    public DeviceTokenFilter(ValidadorTokenDispositivoRemoto validadorTokenDispositivoRemoto,
                             ObjectMapper objectMapper) {
        this.validadorTokenDispositivoRemoto = validadorTokenDispositivoRemoto;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper e obrigatorio").copy();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (jwtAuthenticationToken.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch("ROLE_cliente"::equals)) {
            filterChain.doFilter(request, response);
            return;
        }

        String deviceToken = request.getHeader(HEADER_DEVICE_TOKEN);
        if (!StringUtils.hasText(deviceToken)) {
            responder(response, HttpStatus.PRECONDITION_REQUIRED,
                    new ValidacaoTokenDispositivoResponse(false,
                            "DEVICE_TOKEN_MISSING",
                            "Cabecalho X-Device-Token e obrigatorio",
                            null));
            return;
        }

        ResultadoValidacaoTokenDispositivoRemoto resultado = validadorTokenDispositivoRemoto.validar(
                request.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION),
                deviceToken);
        if (resultado.statusHttp() >= 400) {
            responder(response, HttpStatus.valueOf(resultado.statusHttp()), resultado.payload());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            throw new IllegalStateException("Request URI obrigatoria.");
        }
        String method = request.getMethod();
        if (method == null) {
            throw new IllegalStateException("Metodo HTTP obrigatorio.");
        }
        return HttpMethod.OPTIONS.matches(method) || PATH_MATCHER.match("/actuator/**", path);
    }

    private void responder(HttpServletResponse response,
            HttpStatus status,
            ValidacaoTokenDispositivoResponse payload) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), payload);
    }
}
