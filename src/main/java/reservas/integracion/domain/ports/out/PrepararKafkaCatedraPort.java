package reservas.integracion.domain.ports.out;

import reservas.integracion.domain.model.IntegracionCatedra;

public interface PrepararKafkaCatedraPort {
    void preparar(IntegracionCatedra integracion);
}
