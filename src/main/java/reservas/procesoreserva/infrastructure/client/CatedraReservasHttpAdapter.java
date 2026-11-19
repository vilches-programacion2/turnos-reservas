package reservas.procesoreserva.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import reservas.procesoreserva.application.exception.ReservaPropiaException;
import reservas.procesoreserva.application.exception.ReservaPropiaException.Error;
import reservas.procesoreserva.domain.ports.out.ReservasCatedraClient;
import reservas.shared.infrastructure.catedra.CatedraTokenFile;

@Component
@RequiredArgsConstructor
public class CatedraReservasHttpAdapter implements ReservasCatedraClient {
    private final RestClient catedraRestClient;
    private final CatedraTokenFile tokenFile;

    @Override
    public List<Reserva> listar(String externalPatientId, int page, int size) {
        String token = leerToken();
        try {
            return catedraRestClient.get().uri(uri -> uri.path("/api/appointments")
                            .queryParam("externalPatientId", externalPatientId).queryParam("page", page)
                            .queryParam("size", size).build())
                    .accept(MediaType.APPLICATION_JSON).headers(headers -> headers.setBearerAuth(token))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() != 200) {
                            throw new ReservaPropiaException(Error.CATEDRA_NO_DISPONIBLE);
                        }
                        try {
                            var reservas = response.bodyTo(ReservaResponse[].class);
                            if (reservas == null) throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
                            return Arrays.stream(reservas).map(this::aReserva).toList();
                        } catch (RestClientException e) {
                            throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
                        }
                    });
        } catch (RestClientException e) {
            throw new ReservaPropiaException(Error.CATEDRA_NO_DISPONIBLE);
        }
    }

    @Override
    public Cancelacion cancelar(String reservationProcessId, String reason) {
        String token = leerToken();
        try {
            return catedraRestClient.post().uri("/api/appointments/{id}/cancel", reservationProcessId)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token)).body(new CancelacionRequest(reason))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        switch (status) {
                            case 401, 403 -> throw new ReservaPropiaException(Error.CATEDRA_NO_DISPONIBLE);
                            case 404 -> throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
                            case 409 -> {
                                var error = response.bodyTo(ErrorResponse.class);
                                if (error != null && "RESERVATION_NOT_CANCELLABLE".equals(error.code())) {
                                    throw new ReservaPropiaException(Error.NO_CANCELABLE);
                                }
                                throw new ReservaPropiaException(Error.CANCELACION_INDETERMINADA);
                            }
                            case 200 -> {
                                var resultado = response.bodyTo(CancelacionResponse.class);
                                if (resultado == null || !"CANCELLED".equals(resultado.status())
                                        || !reservationProcessId.equals(resultado.reservationProcessId())
                                        || resultado.reservationId() == null || resultado.cancelledAt() == null) {
                                    throw new ReservaPropiaException(Error.CANCELACION_INDETERMINADA);
                                }
                                return new Cancelacion(resultado.reservationId(), resultado.reservationProcessId(),
                                        resultado.cancelledAt());
                            }
                            default -> throw new ReservaPropiaException(Error.CANCELACION_INDETERMINADA);
                        }
                    });
        } catch (RestClientException e) {
            throw new ReservaPropiaException(Error.CANCELACION_INDETERMINADA);
        }
    }

    private String leerToken() {
        try {
            return tokenFile.leer();
        } catch (IOException e) {
            throw new ReservaPropiaException(Error.CATEDRA_NO_DISPONIBLE);
        }
    }

    private Reserva aReserva(ReservaResponse response) {
        if (response == null) throw new ReservaPropiaException(Error.CATEDRA_INCONSISTENTE);
        return new Reserva(response.reservationId(), response.reservationProcessId(), response.externalPatientId(),
                response.professionalId(), response.professionalFirstName(), response.professionalLastName(),
                response.appointmentDate(), response.startTime(), response.endTime(), response.status(),
                response.confirmedAt(), response.cancelledAt());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CancelacionRequest(String reason) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorResponse(String code) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CancelacionResponse(Long reservationId, String reservationProcessId, String status, Instant cancelledAt) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReservaResponse(Long reservationId, String reservationProcessId, String externalPatientId, Long professionalId,
            String professionalFirstName, String professionalLastName, LocalDate appointmentDate, LocalTime startTime,
            LocalTime endTime, String status, Instant confirmedAt, Instant cancelledAt) {}
}
