package reservas.catalogo.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reservas.catalogo.domain.model.AgendaProfesional;
import reservas.catalogo.domain.ports.in.ConsultarAgendaProfesionalUseCase;
import reservas.catalogo.domain.ports.out.CatalogoClient;

@Component
@RequiredArgsConstructor
public class ConsultarAgendaProfesionalUseCaseImpl implements ConsultarAgendaProfesionalUseCase {
    private final CatalogoClient catalogo;

    @Override
    public AgendaProfesional consultar(Long id) {
        return catalogo.consultarAgenda(id);
    }
}
