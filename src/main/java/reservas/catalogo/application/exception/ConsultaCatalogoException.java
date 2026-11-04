package reservas.catalogo.application.exception;

import lombok.Getter;

@Getter
public class ConsultaCatalogoException extends RuntimeException {
    public enum Error {
        AUTENTICACION_REQUERIDA, ACCESO_DENEGADO, SOLICITUD_INVALIDA,
        PROFESIONAL_NO_ENCONTRADO, PROFESIONAL_DESHABILITADO, NO_INICIALIZADO,
        NO_DISPONIBLE, RESPUESTA_INVALIDA, ERROR_CONTRATO, CONFIGURACION_INVALIDA
    }

    private final Error error;
    private final Integer httpStatus;

    public ConsultaCatalogoException(Error error) {
        this(error, null);
    }

    public ConsultaCatalogoException(Error error, Integer httpStatus) {
        // Los cuerpos y causas del cliente HTTP pueden contener datos sensibles.
        super("Consulta de catálogo fallida: " + error);
        this.error = error;
        this.httpStatus = httpStatus;
    }
}
