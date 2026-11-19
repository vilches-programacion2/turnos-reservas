package reservas.procesoreserva.application.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ReservaPropiaException extends RuntimeException {
    private final Error error;

    public enum Error {
        SOLICITUD_INVALIDA, NO_ENCONTRADA, NO_CANCELABLE, CANCELACION_EN_CURSO, CANCELACION_INDETERMINADA,
        CATEDRA_NO_DISPONIBLE, CATEDRA_INCONSISTENTE
    }
}
