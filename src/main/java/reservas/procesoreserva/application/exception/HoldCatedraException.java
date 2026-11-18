package reservas.procesoreserva.application.exception;

import lombok.Getter;

@Getter
public class HoldCatedraException extends RuntimeException {
    private final Error error;

    public HoldCatedraException(Error error) {
        super("Operación de hold fallida: " + error);
        this.error = error;
    }

    public enum Error {
        CONFIGURACION_INVALIDA,
        CATEDRA_NO_AUTORIZADA,
        PROFESIONAL_NO_ENCONTRADO,
        PROFESIONAL_DESHABILITADO,
        SLOT_INVALIDO,
        SLOT_OCUPADO,
        HOLD_NO_ENCONTRADO,
        HOLD_VENCIDO,
        PROPIETARIO_INVALIDO,
        YA_CONFIRMADO,
        RESULTADO_INDETERMINADO
    }
}
