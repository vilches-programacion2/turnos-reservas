package reservas.procesoreserva.application.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TelefonoReservaException extends RuntimeException {
    private final Error error;

    public enum Error { PROCESO_NO_ENCONTRADO, ESTADO_INVALIDO, TELEFONO_INVALIDO, KAFKA_NO_DISPONIBLE }
}
