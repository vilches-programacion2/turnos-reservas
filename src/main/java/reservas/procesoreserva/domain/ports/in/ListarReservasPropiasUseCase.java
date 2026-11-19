package reservas.procesoreserva.domain.ports.in;

import java.util.List;
import reservas.procesoreserva.domain.model.ReservaPropia;

public interface ListarReservasPropiasUseCase {
    List<ReservaPropia> listar(Long userId, int page, int size);
}
