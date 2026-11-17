package reservas.integracion.infrastructure.client.adapter;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import reservas.integracion.application.exception.IntegracionCatedraException;
import reservas.integracion.application.exception.IntegracionCatedraException.Error;
import reservas.integracion.domain.model.IntegracionCatedra;
import reservas.integracion.domain.ports.out.CatedraClient;
import reservas.integracion.infrastructure.client.dto.IntegracionCatedraResponse;
import reservas.integracion.infrastructure.client.mapper.IntegracionCatedraMapper;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;

@Component
@RequiredArgsConstructor
public class CatedraHttpAdapter implements CatedraClient {
    private final RestClient catedraRestClient;
    private final CatedraTokenFile tokenFile;
    private final IntegracionCatedraMapper mapper;

    @Override
    public IntegracionCatedra obtenerIntegracion() {
        String token = leerToken();
        try {
            return catedraRestClient.get().uri("/api/student/integration")
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            throw new IntegracionCatedraException(errorHttp(status), status);
                        }
                        try {
                            return mapper.toDomain(response.bodyTo(IntegracionCatedraResponse.class));
                        } catch (RestClientException e) {
                            throw new IntegracionCatedraException(Error.RESPUESTA_INVALIDA);
                        }
                    });
        } catch (RestClientException e) {
            throw new IntegracionCatedraException(Error.NO_DISPONIBLE);
        }
    }

    private String leerToken() {
        try {
            return tokenFile.leer();
        } catch (IOException e) {
            throw new IntegracionCatedraException(Error.CONFIGURACION_INVALIDA);
        }
    }

    private Error errorHttp(int status) {
        return switch (status) {
            case 401 -> Error.NO_AUTENTICADO;
            case 403 -> Error.ACCESO_DENEGADO;
            case 404 -> Error.INTEGRACION_NO_ENCONTRADA;
            case 429, 500, 502, 503, 504 -> Error.NO_DISPONIBLE;
            default -> Error.ERROR_HTTP;
        };
    }
}
