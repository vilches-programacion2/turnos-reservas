package reservas.catalogo.application.usecases;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reservas.catalogo.domain.model.Profesional;
import reservas.catalogo.domain.ports.in.ConsultarProfesionalUseCase;
import reservas.catalogo.domain.ports.out.CatalogoClient;

@Component
@RequiredArgsConstructor
public class ConsultarProfesionalUseCaseImpl implements ConsultarProfesionalUseCase {
    private final CatalogoClient catalogo;

    @Override
    public Profesional consultar(Long id) {
        return catalogo.consultarProfesional(id);
    }
}
