package ec.edu.unibe.sistema_practicas.auth;

import ec.edu.unibe.sistema_practicas.config.JwtTokenProvider;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.UsuarioRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String MENSAJE_RECUPERACION =
            "Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.";
    private static final String MENSAJE_LOGIN_INVALIDO =
            "No se pudo iniciar sesión. Verifica tus credenciales o inténtalo más tarde.";
    private static final String PASSWORD_HASH_SIMULADO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenRecuperacionRepository tokenRecuperacionRepository;
    private final CorreoRecuperacionPasswordComponent correoRecuperacionPasswordComponent;

    @Value("${app.auth.password-reset-minutes:30}")
    private long passwordResetMinutes;

    @Value("${app.auth.login-max-attempts:5}")
    private int loginMaxAttempts;

    @Value("${app.auth.login-block-minutes:15}")
    private long loginBlockMinutes;

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse login(AuthRequest request) {
        String email = request == null || request.getEmail() == null
                ? ""
                : request.getEmail().trim().toLowerCase(Locale.ROOT);
        String password = request == null || request.getPassword() == null ? "" : request.getPassword();
        if (email.isBlank() || password.isBlank()) {
            passwordEncoder.matches(password, PASSWORD_HASH_SIMULADO);
            throw credencialesInvalidas();
        }

        Optional<Usuario> encontrado = usuarioRepository.findByEmailIgnoreCaseForUpdate(email);
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(password, PASSWORD_HASH_SIMULADO);
            throw credencialesInvalidas();
        }
        Usuario usuario = encontrado.get();

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            passwordEncoder.matches(password, usuario.getPasswordHash());
            throw credencialesInvalidas();
        }

        LocalDateTime ahora = LocalDateTime.now();
        boolean teniaProteccion = intentosFallidos(usuario) > 0 || usuario.getBloqueadoHasta() != null;
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            passwordEncoder.matches(password, PASSWORD_HASH_SIMULADO);
            throw credencialesInvalidas();
        }
        if (usuario.getBloqueadoHasta() != null) {
            reiniciarProteccionLogin(usuario);
        }

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            registrarFalloLogin(usuario, ahora);
            throw credencialesInvalidas();
        }

        reiniciarProteccionLogin(usuario);
        if (teniaProteccion) {
            usuario.setUpdatedAt(ahora);
            usuarioRepository.save(usuario);
        }

        String rol = usuario.getRoles().stream()
            .map(Rol::getCodigo)
            .findFirst()
            .orElse("ESTUDIANTE");

        String token = tokenProvider.generateToken(usuario.getEmail(), rol);

        String carrera = null;
        if ("ESTUDIANTE".equals(rol)) {
            carrera = estudianteRepository.findAll().stream()
                .filter(e -> e.getUsuario() != null && e.getUsuario().getId().equals(usuario.getId()))
                .map(Estudiante::getCarrera)
                .findFirst()
                .orElse(null);
        }

        return new AuthResponse(
            token,
            usuario.getNombre(),
            usuario.getApellido(),
            usuario.getEmail(),
            rol,
            carrera,
            Boolean.TRUE.equals(usuario.getPrimerLogin()),
            "TUTOR".equals(rol) ? (usuario.getTutorTipo() == null ? "AMBOS" : usuario.getTutorTipo()) : null
        );
    }

    @Transactional
    public MensajeAuthResponse cambiarPassword(Usuario principal, CambiarPasswordRequest request) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión para cambiar tu contraseña.");
        }
        Usuario usuario = usuarioRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado."));
        if (request == null || request.getPasswordActual() == null
                || !passwordEncoder.matches(request.getPasswordActual(), usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        validarNuevaPassword(request.getNuevaPassword());
        if (passwordEncoder.matches(request.getNuevaPassword(), usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser diferente a la actual.");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));
        usuario.setPrimerLogin(false);
        reiniciarProteccionLogin(usuario);
        usuario.setUpdatedAt(LocalDateTime.now());
        usuarioRepository.save(usuario);
        invalidarTokensPendientes(usuario);
        return new MensajeAuthResponse("Contraseña actualizada correctamente.");
    }

    @Transactional
    public MensajeAuthResponse recuperarPassword(RecuperarPasswordRequest request) {
        try {
            if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
                return new MensajeAuthResponse(MENSAJE_RECUPERACION);
            }
            usuarioRepository.findByEmailIgnoreCase(request.getEmail().trim())
                    .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                    .ifPresent(this::crearTokenYEnviarCorreo);
        } catch (Exception e) {
            log.warn("No se pudo procesar solicitud de recuperación de contraseña: {}", e.getMessage());
            log.debug("Detalle de recuperación de contraseña", e);
        }
        return new MensajeAuthResponse(MENSAJE_RECUPERACION);
    }

    @Transactional
    public MensajeAuthResponse restablecerPassword(RestablecerPasswordRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalArgumentException("El token de recuperación es obligatorio.");
        }
        validarNuevaPassword(request.getNuevaPassword());

        String tokenHash = hashToken(request.getToken().trim());
        TokenRecuperacion token = tokenRecuperacionRepository.findByTokenHashAndUsadoFalse(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("El token de recuperación no es válido o expiró."));
        if (token.getFechaExpiracion() == null || token.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            marcarTokenUsado(token);
            throw new IllegalArgumentException("El token de recuperación no es válido o expiró.");
        }
        Usuario usuario = token.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));
        usuario.setPrimerLogin(false);
        reiniciarProteccionLogin(usuario);
        usuario.setUpdatedAt(LocalDateTime.now());
        usuarioRepository.save(usuario);
        marcarTokenUsado(token);
        invalidarTokensPendientes(usuario);
        return new MensajeAuthResponse("Contraseña restablecida correctamente.");
    }

    private void crearTokenYEnviarCorreo(Usuario usuario) {
        invalidarTokensPendientes(usuario);
        String tokenPlano = generarTokenPlano();
        LocalDateTime ahora = LocalDateTime.now();

        TokenRecuperacion token = new TokenRecuperacion();
        token.setUsuario(usuario);
        token.setTokenHash(hashToken(tokenPlano));
        token.setFechaCreacion(ahora);
        token.setFechaExpiracion(ahora.plusMinutes(passwordResetMinutes));
        token.setUsado(false);
        tokenRecuperacionRepository.save(token);

        correoRecuperacionPasswordComponent.enviar(usuario, tokenPlano);
    }

    private void validarNuevaPassword(String nuevaPassword) {
        if (nuevaPassword == null || nuevaPassword.isBlank()) {
            throw new IllegalArgumentException("La nueva contraseña es obligatoria.");
        }
        if (nuevaPassword.length() < 8) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres.");
        }
        boolean tieneLetra = nuevaPassword.chars().anyMatch(Character::isLetter);
        boolean tieneNumero = nuevaPassword.chars().anyMatch(Character::isDigit);
        if (!tieneLetra || !tieneNumero) {
            throw new IllegalArgumentException("La nueva contraseña debe incluir letras y números.");
        }
    }

    private void registrarFalloLogin(Usuario usuario, LocalDateTime ahora) {
        int intentos = intentosFallidos(usuario) + 1;
        usuario.setIntentosLoginFallidos(intentos);
        if (intentos >= Math.max(1, loginMaxAttempts)) {
            usuario.setBloqueadoHasta(ahora.plusMinutes(Math.max(1, loginBlockMinutes)));
            log.warn("Cuenta bloqueada temporalmente tras intentos fallidos. usuarioId={}, bloqueadoHasta={}",
                    usuario.getId(), usuario.getBloqueadoHasta());
        }
        usuario.setUpdatedAt(ahora);
        usuarioRepository.save(usuario);
    }

    private int intentosFallidos(Usuario usuario) {
        return usuario.getIntentosLoginFallidos() == null ? 0 : usuario.getIntentosLoginFallidos();
    }

    private void reiniciarProteccionLogin(Usuario usuario) {
        usuario.setIntentosLoginFallidos(0);
        usuario.setBloqueadoHasta(null);
    }

    private ResponseStatusException credencialesInvalidas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_LOGIN_INVALIDO);
    }

    private String generarTokenPlano() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar hash del token de recuperación.", e);
        }
    }

    private void invalidarTokensPendientes(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) return;
        List<TokenRecuperacion> pendientes = tokenRecuperacionRepository.findByUsuarioIdAndUsadoFalse(usuario.getId());
        pendientes.forEach(this::marcarTokenUsado);
    }

    private void marcarTokenUsado(TokenRecuperacion token) {
        token.setUsado(true);
        token.setUsadoEn(LocalDateTime.now());
        tokenRecuperacionRepository.save(token);
    }
}
