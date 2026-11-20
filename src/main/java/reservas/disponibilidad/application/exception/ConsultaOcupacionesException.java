package reservas.disponibilidad.application.exception;

import lombok.Getter;

@Getter
public class ConsultaOcupacionesException extends RuntimeException {
    public enum Error {
        CONFIGURACION_INVALIDA, NO_AUTENTICADO, ACCESO_DENEGADO,
        NO_DISPONIBLE, RESPUESTA_INVALIDA, ERROR_CONTRATO
    }

    private final Error error;
    private final Integer httpStatus;

    public ConsultaOcupacionesException(Error error) {
        this(error, null);
    }

    public ConsultaOcupacionesException(Error error, Integer httpStatus) {
        super("Consulta de ocupaciones fallida: " + error);
        this.error = error;
        this.httpStatus = httpStatus;
    }
}
