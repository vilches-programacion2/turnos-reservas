package reservas.procesoreserva.application.exception;

import lombok.Getter;

@Getter
public class ProcesoHoldException extends RuntimeException {
    private final Error error;

    public ProcesoHoldException(Error error) {
        super(error.name());
        this.error = error;
    }

    public enum Error {
        SOLICITUD_INVALIDA, USUARIO_NO_DISPONIBLE, PERFIL_INCOMPLETO, HOLD_NO_ENCONTRADO,
        HOLD_VENCIDO, CONFIRMACION_EN_CURSO, RESULTADO_INDETERMINADO, ESTADO_INVALIDO
    }
}
