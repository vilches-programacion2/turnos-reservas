package reservas.catalogo.domain.ports.out;

import reservas.catalogo.domain.model.AgendaProfesional;
import reservas.catalogo.domain.model.Profesional;

public interface CatalogoClient {
    Profesional consultarProfesional(Long id);
    AgendaProfesional consultarAgenda(Long id);
}
