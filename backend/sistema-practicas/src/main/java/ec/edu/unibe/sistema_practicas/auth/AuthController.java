package ec.edu.unibe.sistema_practicas.auth;

import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LimiteIntentosLoginComponent limiteIntentosLoginComponent;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        limiteIntentosLoginComponent.verificar(httpRequest);
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/cambiar-password")
    public ResponseEntity<MensajeAuthResponse> cambiarPassword(@AuthenticationPrincipal Usuario usuario,
                                                               @RequestBody CambiarPasswordRequest request) {
        return ResponseEntity.ok(authService.cambiarPassword(usuario, request));
    }

    @PostMapping("/recuperar")
    public ResponseEntity<MensajeAuthResponse> recuperar(@RequestBody RecuperarPasswordRequest request) {
        return ResponseEntity.ok(authService.recuperarPassword(request));
    }

    @PostMapping("/restablecer")
    public ResponseEntity<MensajeAuthResponse> restablecer(@RequestBody RestablecerPasswordRequest request) {
        return ResponseEntity.ok(authService.restablecerPassword(request));
    }
}
