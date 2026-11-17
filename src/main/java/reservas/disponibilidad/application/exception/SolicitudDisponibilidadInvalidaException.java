package reservas.disponibilidad.application.exception;

public class SolicitudDisponibilidadInvalidaException extends RuntimeException {
    public SolicitudDisponibilidadInvalidaException() {
        super("Se requiere un profesional positivo y una fecha válida que no sea pasada en Argentina.");
    }
}
