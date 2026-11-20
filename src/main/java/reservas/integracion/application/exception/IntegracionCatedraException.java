package reservas.integracion.application.exception;

import lombok.Getter;

@Getter
public class IntegracionCatedraException extends RuntimeException {
    public enum Error {
        CONFIGURACION_INVALIDA, NO_AUTENTICADO, ACCESO_DENEGADO,
        INTEGRACION_NO_ENCONTRADA, NO_DISPONIBLE, RESPUESTA_INVALIDA, ERROR_HTTP,
        KAFKA_NO_DISPONIBLE
    }

    private final Error error;
    private final Integer httpStatus;

    public IntegracionCatedraException(Error error) {
        this(error, null);
    }

    public IntegracionCatedraException(Error error, Integer httpStatus) {
        // No conservar cuerpos, mensajes ni causas del cliente HTTP: pueden incluir secretos.
        super("No se pudo recuperar la integración de cátedra: " + error);
        this.error = error;
        this.httpStatus = httpStatus;
    }
}
