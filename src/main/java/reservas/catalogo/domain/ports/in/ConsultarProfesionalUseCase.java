package reservas.catalogo.domain.ports.in;

import reservas.catalogo.domain.model.Profesional;

public interface ConsultarProfesionalUseCase {
    Profesional consultar(Long id);
}
