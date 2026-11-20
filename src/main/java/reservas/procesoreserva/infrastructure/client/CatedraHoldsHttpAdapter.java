package reservas.procesoreserva.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import reservas.procesoreserva.application.exception.HoldCatedraException;
import reservas.procesoreserva.application.exception.HoldCatedraException.Error;
import reservas.procesoreserva.domain.ports.out.HoldsCatedraClient;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;

@Component
@RequiredArgsConstructor
public class CatedraHoldsHttpAdapter implements HoldsCatedraClient {
    private final RestClient catedraRestClient;
    private final CatedraTokenFile tokenFile;

    @Override
    public Hold crear(Long professionalId, LocalDate date, LocalTime startTime, String externalPatientId) {
        var response = enviar("/api/appointment-holds", new CrearRequest(professionalId, date, startTime, externalPatientId),
                201, "HELD");
        if (!identificadorValido(response.holdId())) throw fallo(Error.RESULTADO_INDETERMINADO);
        return new Hold(response.holdId(), response.reservationProcessId(), response.expiresAt());
    }

    @Override
    public Confirmacion confirmar(String holdId, String externalPatientId, String firstName, String lastName) {
        var response = enviar("/api/appointment-holds/{id}/confirm",
                new ConfirmarRequest(externalPatientId, firstName, lastName), 202, "WAITING_FOR_PHONE", holdId);
        return new Confirmacion(response.reservationProcessId(), response.expiresAt());
    }

    private HoldResponse enviar(String ruta, Object datos, int esperado, String estado, Object... variables) {
        try {
            return catedraRestClient.post().uri(ruta, variables)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(leerToken())).body(datos)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != esperado) {
                            if (status == 401) throw fallo(Error.CATEDRA_NO_AUTORIZADA);
                            if (status == 400 || status == 403 || status == 404 || status == 409) {
                                var body = response.bodyTo(ErrorResponse.class);
                                if (body == null || body.code() == null) {
                                    throw fallo(status == 403 ? Error.CATEDRA_NO_AUTORIZADA : Error.RESULTADO_INDETERMINADO);
                                }
                                throw fallo(errorContractual(status, body.code()));
                            }
                            throw fallo(Error.RESULTADO_INDETERMINADO);
                        }
                        var body = response.bodyTo(HoldResponse.class);
                        if (body == null || !estado.equals(body.status())
                                || !identificadorValido(body.reservationProcessId()) || body.expiresAt() == null) {
                            throw fallo(Error.RESULTADO_INDETERMINADO);
                        }
                        return body;
                    });
        } catch (RestClientException e) {
            throw fallo(Error.RESULTADO_INDETERMINADO);
        }
    }

    private String leerToken() {
        try {
            return tokenFile.leer();
        } catch (IOException e) {
            throw fallo(Error.CONFIGURACION_INVALIDA);
        }
    }

    private Error errorContractual(int status, String code) {
        return switch (status) {
            case 400 -> switch (code) {
                case "PROFESSIONAL_DISABLED" -> Error.PROFESIONAL_DESHABILITADO;
                case "INVALID_SLOT" -> Error.SLOT_INVALIDO;
                case "EXTERNAL_PATIENT_MISMATCH" -> Error.PROPIETARIO_INVALIDO;
                default -> Error.RESULTADO_INDETERMINADO;
            };
            case 403 -> "HOLD_OWNER_MISMATCH".equals(code) ? Error.PROPIETARIO_INVALIDO : Error.CATEDRA_NO_AUTORIZADA;
            case 404 -> switch (code) {
                case "PROFESSIONAL_NOT_FOUND" -> Error.PROFESIONAL_NO_ENCONTRADO;
                case "HOLD_NOT_FOUND" -> Error.HOLD_NO_ENCONTRADO;
                case "HOLD_EXPIRED" -> Error.HOLD_VENCIDO;
                default -> Error.RESULTADO_INDETERMINADO;
            };
            case 409 -> switch (code) {
                case "SLOT_ALREADY_RESERVED", "SLOT_ALREADY_HELD" -> Error.SLOT_OCUPADO;
                case "RESERVATION_PROCESS_ALREADY_CONFIRMED" -> Error.YA_CONFIRMADO;
                default -> Error.RESULTADO_INDETERMINADO;
            };
            default -> Error.RESULTADO_INDETERMINADO;
        };
    }

    private boolean identificadorValido(String value) {
        return value != null && !value.isBlank() && value.length() <= 255;
    }

    private HoldCatedraException fallo(Error error) {
        return new HoldCatedraException(error);
    }

    private record CrearRequest(Long professionalId, LocalDate date, LocalTime startTime, String externalPatientId) {}
    private record ConfirmarRequest(String externalPatientId, String patientFirstName, String patientLastName) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HoldResponse(String holdId, String reservationProcessId, String status, Instant expiresAt) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorResponse(String code) {}
}
