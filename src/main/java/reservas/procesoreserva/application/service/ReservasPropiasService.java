package reservas.procesoreserva.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.domain.model.ReservaPropia;
import reservas.procesoreserva.domain.ports.in.CancelarReservaPropiaUseCase;
import reservas.procesoreserva.domain.ports.in.ListarReservasPropiasUseCase;

@Service
@RequiredArgsConstructor
public class ReservasPropiasService {
    private final ListarReservasPropiasUseCase listado;
    private final CancelarReservaPropiaUseCase cancelacion;

    public List<ReservaPropia> listar(Long userId, int page, int size) {
        return listado.listar(userId, page, size);
    }

    public ProcesoReserva cancelar(Long userId, Long id, String reason) {
        return cancelacion.cancelar(userId, id, reason);
    }
}
