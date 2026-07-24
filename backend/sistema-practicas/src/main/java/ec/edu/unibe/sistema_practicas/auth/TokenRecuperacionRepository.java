package ec.edu.unibe.sistema_practicas.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Integer> {
    Optional<TokenRecuperacion> findByTokenHashAndUsadoFalse(String tokenHash);
    List<TokenRecuperacion> findByUsuarioIdAndUsadoFalse(Integer usuarioId);
}
