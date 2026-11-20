package reservas.catalogo.infrastructure.client.adapter;

import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import reservas.catalogo.application.exception.ConsultaCatalogoException;
import reservas.catalogo.application.exception.ConsultaCatalogoException.Error;
import reservas.catalogo.domain.model.AgendaProfesional;
import reservas.catalogo.domain.model.Profesional;
import reservas.catalogo.domain.ports.out.CatalogoClient;
import reservas.catalogo.infrastructure.client.dto.AgendaProfesionalResponse;
import reservas.catalogo.infrastructure.client.dto.CatalogoErrorResponse;
import reservas.catalogo.infrastructure.client.dto.ProfesionalResponse;
import reservas.catalogo.infrastructure.client.mapper.CatalogoMapper;
import reservas.catalogo.infrastructure.config.CatalogoProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogoHttpAdapter implements CatalogoClient {
    private final RestClient catalogoRestClient;
    private final CatalogoProperties properties;
    private final CatalogoMapper mapper;

    @Override
    public Profesional consultarProfesional(Long id) {
        return consultar(id, "profesional", "", ProfesionalResponse.class, dto -> mapper.toDomain(dto, id));
    }

    @Override
    public AgendaProfesional consultarAgenda(Long id) {
        return consultar(id, "agenda", "/schedule", AgendaProfesionalResponse.class, dto -> mapper.toDomain(dto, id));
    }

    private <T, R> R consultar(Long id, String operacion, String suffix, Class<T> type, Function<T, R> mapping) {
        Jwt jwt = usuarioAutenticado();
        if (id == null || id <= 0) {
            throw new ConsultaCatalogoException(Error.SOLICITUD_INVALIDA);
        }
        if (!properties.configurado()) {
            throw new ConsultaCatalogoException(Error.CONFIGURACION_INVALIDA);
        }
        try {
            return catalogoRestClient.get().uri("/api/professionals/{id}" + suffix, id)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            String code = codigo(response);
                            throw new ConsultaCatalogoException(errorHttp(status, code), status);
                        }
                        try {
                            return mapping.apply(response.bodyTo(type));
                        } catch (RestClientException e) {
                            throw new ConsultaCatalogoException(Error.RESPUESTA_INVALIDA);
                        }
                    });
        } catch (RestClientException e) {
            var error = new ConsultaCatalogoException(Error.NO_DISPONIBLE);
            registrarFallo(operacion, id, jwt, error);
            throw error;
        } catch (ConsultaCatalogoException e) {
            registrarFallo(operacion, id, jwt, e);
            throw e;
        }
    }

    private Jwt usuarioAutenticado() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            throw new ConsultaCatalogoException(Error.AUTENTICACION_REQUERIDA);
        }
        if (authentication.getAuthorities().stream().noneMatch(authority -> "ROLE_USER".equals(authority.getAuthority()))) {
            throw new ConsultaCatalogoException(Error.ACCESO_DENEGADO);
        }
        Jwt jwt = jwtAuthentication.getToken();
        if (jwt.getAudience() == null || !jwt.getAudience().contains(properties.audience())) {
            throw new ConsultaCatalogoException(Error.AUTENTICACION_REQUERIDA);
        }
        return jwt;
    }

    private String codigo(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            var error = response.bodyTo(CatalogoErrorResponse.class);
            return error == null ? null : error.getCode();
        } catch (RestClientException e) {
            return null;
        }
    }

    private Error errorHttp(int status, String code) {
        if (status == 401) return Error.AUTENTICACION_REQUERIDA;
        if (status == 403) return Error.ACCESO_DENEGADO;
        if (status == 404 && "PROFESSIONAL_NOT_FOUND".equals(code)) return Error.PROFESIONAL_NO_ENCONTRADO;
        if (status == 409 && "PROFESSIONAL_DISABLED".equals(code)) return Error.PROFESIONAL_DESHABILITADO;
        if (status == 503 && "CATALOG_NOT_READY".equals(code)) return Error.NO_INICIALIZADO;
        if (status >= 500 || status == 429) return Error.NO_DISPONIBLE;
        return Error.ERROR_CONTRATO;
    }

    private void registrarFallo(String operacion, Long id, Jwt jwt, ConsultaCatalogoException error) {
        log.warn("Consulta a catálogo fallida: operacion={} profesionalId={} usuarioId={} codigo={} httpStatus={}",
                operacion, id, jwt.getSubject(), error.getError(), error.getHttpStatus());
    }
}
