package reservas.usuario.application.exception;

public class LoginYaRegistradoException extends RuntimeException {

    public LoginYaRegistradoException() {
        super("El login ya está registrado.");
    }
}