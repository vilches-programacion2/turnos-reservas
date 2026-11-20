package reservas.usuario.domain.ports.out;

import reservas.usuario.domain.model.Usuario;

public interface EmisorToken {
    String emitir(Usuario usuario);
}
