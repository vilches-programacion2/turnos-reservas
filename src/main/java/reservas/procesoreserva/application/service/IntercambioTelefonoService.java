package reservas.procesoreserva.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reservas.procesoreserva.domain.model.EstadoTelefono;
import reservas.procesoreserva.domain.ports.in.ConsultarTelefonoUseCase;
import reservas.procesoreserva.domain.ports.in.EnviarTelefonoUseCase;

@Service
@RequiredArgsConstructor
public class IntercambioTelefonoService {
    private final ConsultarTelefonoUseCase consulta;
    private final EnviarTelefonoUseCase envio;

    public EstadoTelefono consultar(Long userId, Long id) {
        return consulta.consultar(userId, id);
    }

    public EstadoTelefono enviar(Long userId, Long id, String telefono) {
        return envio.enviar(userId, id, telefono);
    }
}
