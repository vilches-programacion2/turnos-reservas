package reservas.disponibilidad.infrastructure.client.adapter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import reservas.disponibilidad.application.exception.ConsultaOcupacionesException;
import reservas.disponibilidad.application.exception.ConsultaOcupacionesException.Error;
import reservas.disponibilidad.domain.ports.out.OcupacionesClient;
import reservas.disponibilidad.infrastructure.client.dto.OcupacionesResponse;
import reservas.disponibilidad.infrastructure.client.mapper.OcupacionesMapper;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatedraOcupacionesHttpAdapter implements OcupacionesClient {
    private final RestClient catedraRestClient;
    private final CatedraTokenFile tokenFile;
    private final OcupacionesMapper mapper;

    @Override
    public Set<LocalTime> consultar(Long professionalId, LocalDate date) {
        try {
            String token = tokenFile.leer();
            return catedraRestClient.get().uri(builder -> builder.path("/api/appointment-occupancies")
                            .queryParam("professionalId", professionalId).queryParam("from", date).queryParam("to", date).build())
                    .accept(MediaType.APPLICATION_JSON).headers(headers -> headers.setBearerAuth(token))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) throw new ConsultaOcupacionesException(errorHttp(status), status);
                        try {
                            return mapper.toDomain(response.bodyTo(OcupacionesResponse.class), professionalId, date);
                        } catch (RestClientException e) {
                            throw new ConsultaOcupacionesException(Error.RESPUESTA_INVALIDA);
                        }
                    });
        } catch (IOException e) {
            throw fallo(professionalId, date, new ConsultaOcupacionesException(Error.CONFIGURACION_INVALIDA));
        } catch (RestClientException e) {
            throw fallo(professionalId, date, new ConsultaOcupacionesException(Error.NO_DISPONIBLE));
        } catch (ConsultaOcupacionesException e) {
            throw fallo(professionalId, date, e);
        }
    }

    private Error errorHttp(int status) {
        return switch (status) {
            case 401 -> Error.NO_AUTENTICADO;
            case 403 -> Error.ACCESO_DENEGADO;
            case 429, 500, 502, 503, 504 -> Error.NO_DISPONIBLE;
            default -> Error.ERROR_CONTRATO;
        };
    }

    private ConsultaOcupacionesException fallo(Long id, LocalDate date, ConsultaOcupacionesException error) {
        log.warn("Consulta de ocupaciones fallida: operacion=ocupaciones profesionalId={} fecha={} codigo={} httpStatus={}",
                id, date, error.getError(), error.getHttpStatus());
        return error;
    }
}
