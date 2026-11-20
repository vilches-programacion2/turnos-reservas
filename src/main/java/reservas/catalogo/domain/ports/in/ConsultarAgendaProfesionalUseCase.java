package reservas.catalogo.domain.ports.in;

import reservas.catalogo.domain.model.AgendaProfesional;

public interface ConsultarAgendaProfesionalUseCase {
    AgendaProfesional consultar(Long id);
}
