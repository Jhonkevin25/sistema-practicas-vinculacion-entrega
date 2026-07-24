package ec.edu.unibe.sistema_practicas.auth;

import ec.edu.unibe.sistema_practicas.config.JwtTokenProvider;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EstudianteRepository estudianteRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private TokenRecuperacionRepository tokenRecuperacionRepository;
    @Mock private CorreoRecuperacionPasswordComponent correoRecuperacionPasswordComponent;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(authService, "loginMaxAttempts", 5);
        ReflectionTestUtils.setField(authService, "loginBlockMinutes", 15L);
    }

    @Test
    void credencial_incorrecta_incrementa_contador_y_responde_generico() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmailIgnoreCaseForUpdate("persona@unibe.edu.ec"))
                .thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.login(request("PERSONA@UNIBE.EDU.EC", "incorrecta")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        assertEquals("No se pudo iniciar sesión. Verifica tus credenciales o inténtalo más tarde.", error.getReason());
        assertEquals(1, usuario.getIntentosLoginFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void quinto_fallo_bloquea_temporalmente_la_cuenta() {
        Usuario usuario = usuarioActivo();
        usuario.setIntentosLoginFallidos(4);
        when(usuarioRepository.findByEmailIgnoreCaseForUpdate(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> authService.login(request(usuario.getEmail(), "incorrecta")));

        assertEquals(5, usuario.getIntentosLoginFallidos());
        assertNotNull(usuario.getBloqueadoHasta());
        assertTrue(usuario.getBloqueadoHasta().isAfter(LocalDateTime.now().plusMinutes(14)));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void bloqueo_activo_no_evalua_el_hash_real_ni_incrementa_contador() {
        Usuario usuario = usuarioActivo();
        usuario.setIntentosLoginFallidos(5);
        usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findByEmailIgnoreCaseForUpdate(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));

        assertThrows(ResponseStatusException.class,
                () -> authService.login(request(usuario.getEmail(), "cualquiera")));

        assertEquals(5, usuario.getIntentosLoginFallidos());
        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("cualquiera"), anyString());
        verify(usuarioRepository, never()).save(usuario);
    }

    @Test
    void acceso_correcto_tras_expirar_bloqueo_limpia_la_proteccion() {
        Usuario usuario = usuarioActivo();
        usuario.setIntentosLoginFallidos(5);
        usuario.setBloqueadoHasta(LocalDateTime.now().minusMinutes(1));
        Rol admin = new Rol();
        admin.setCodigo("ADMIN");
        usuario.getRoles().add(admin);
        when(usuarioRepository.findByEmailIgnoreCaseForUpdate(usuario.getEmail()))
                .thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("correcta", "hash")).thenReturn(true);
        when(tokenProvider.generateToken(usuario.getEmail(), "ADMIN")).thenReturn("jwt");

        AuthResponse response = authService.login(request(usuario.getEmail(), "correcta"));

        assertEquals("jwt", response.getToken());
        assertEquals(0, usuario.getIntentosLoginFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void usuario_inexistente_responde_igual_y_ejecuta_verificacion_simulada() {
        when(usuarioRepository.findByEmailIgnoreCaseForUpdate("nadie@unibe.edu.ec"))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authService.login(request("nadie@unibe.edu.ec", "secreta")));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        assertEquals("No se pudo iniciar sesión. Verifica tus credenciales o inténtalo más tarde.", error.getReason());
        verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("secreta"), anyString());
        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void error_de_credenciales_no_revierte_el_contador_transaccional() throws Exception {
        Method login = AuthService.class.getMethod("login", AuthRequest.class);
        Transactional transactional = login.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertTrue(Arrays.asList(transactional.noRollbackFor()).contains(ResponseStatusException.class));
    }

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setId(7);
        usuario.setEmail("persona@unibe.edu.ec");
        usuario.setNombre("Persona");
        usuario.setApellido("Prueba");
        usuario.setPasswordHash("hash");
        usuario.setActivo(true);
        usuario.setIntentosLoginFallidos(0);
        return usuario;
    }

    private AuthRequest request(String email, String password) {
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
