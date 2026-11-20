package reservas.catalogo.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reservas.catalogo.domain.model.AgendaProfesional;
import reservas.catalogo.domain.model.Profesional;
import reservas.catalogo.domain.ports.in.ConsultarAgendaProfesionalUseCase;
import reservas.catalogo.domain.ports.in.ConsultarProfesionalUseCase;

@Service
@RequiredArgsConstructor
public class CatalogoService {
    private final ConsultarProfesionalUseCase consultarProfesional;
    private final ConsultarAgendaProfesionalUseCase consultarAgenda;

    public Profesional consultarProfesional(Long id) {
        return consultarProfesional.consultar(id);
    }

    public AgendaProfesional consultarAgenda(Long id) {
        return consultarAgenda.consultar(id);
    }
}
