package reservas.usuario.domain.ports.in;

public interface AutenticarUsuarioUseCase {
    String autenticar(String login, String password);
}
