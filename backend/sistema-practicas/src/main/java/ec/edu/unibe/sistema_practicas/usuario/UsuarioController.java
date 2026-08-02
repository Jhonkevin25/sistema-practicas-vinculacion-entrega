package ec.edu.unibe.sistema_practicas.usuario;

import ec.edu.unibe.sistema_practicas.config.JwtTokenProvider;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notificacion.CorreoNotificacionComponent;
import ec.edu.unibe.sistema_practicas.paginacion.PaginaResponse;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);
    private static final Set<String> FUENTES = Set.of("MANUAL", "CSV_UNIVERSIDAD", "API_UNIVERSIDAD");

    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoNotificacionComponent correoNotificacionComponent;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public List<Usuario> getAll() {
        return usuarioRepository.findAll();
    }

    // Fase 43: listado paginado con filtros (texto, rol, activo) resuelto en
    // la base de datos. Solo ADMIN por la matriz de seguridad.
    @GetMapping("/paginado")
    public PaginaResponse<Usuario> getPaginado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Specification<Usuario> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                String like = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), like),
                        cb.like(cb.lower(root.get("apellido")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("cedula")), like)));
            }
            if (rol != null && !rol.isBlank()) {
                query.distinct(true);
                condiciones.add(cb.equal(
                        root.join("roles").get("codigo"), rol.trim().toUpperCase(Locale.ROOT)));
            }
            if (activo != null) {
                condiciones.add(cb.equal(root.get("activo"), activo));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
        
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort;
        if (sortBy.equalsIgnoreCase("nombre") || sortBy.equalsIgnoreCase("apellido")) {
            sort = Sort.by(direction, "apellido").and(Sort.by(direction, "nombre"));
        } else {
            // Lista blanca: un sortBy arbitrario provocaría PropertyReferenceException (500)
            String campoOrden = List.of("id", "email", "activo").contains(sortBy) ? sortBy : "id";
            sort = Sort.by(direction, campoOrden);
        }
        
        return PaginaResponse.desde(usuarioRepository.findAll(spec, PaginaResponse.pageable(
                pagina, tamano, sort)));
    }

    @GetMapping("/tutores")
    public List<TutorResumen> getTutores() {
        return usuarioRepository.findDistinctByRoles_CodigoAndActivoTrueOrderByApellidoAscNombreAsc("TUTOR").stream()
                .map(usuario -> new TutorResumen(
                        usuario.getId(), usuario.getNombre(), usuario.getApellido(), usuario.getEmail(),
                        usuario.getActivo(), usuario.getTutorTipo(), List.of(new RolResumen("TUTOR"))))
                .toList();
    }

    // Candidatos para el alta de expediente: cuentas activas con rol
    // ESTUDIANTE que aun no tienen expediente. Evita que el frontend consuma
    // el listado completo de usuarios para llenar el selector.
    @GetMapping("/candidatos-estudiante")
    public List<CandidatoEstudiante> getCandidatosEstudiante() {
        Set<Integer> conExpediente = Set.copyOf(estudianteRepository.findUsuarioIdsConExpediente());
        return usuarioRepository.findDistinctByRoles_CodigoAndActivoTrueOrderByApellidoAscNombreAsc("ESTUDIANTE").stream()
                .filter(usuario -> !conExpediente.contains(usuario.getId()))
                .map(usuario -> new CandidatoEstudiante(
                        usuario.getId(), usuario.getNombre(), usuario.getApellido(), usuario.getEmail()))
                .toList();
    }

    @GetMapping("/me")
    public ResponseEntity<Usuario> getMe(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(usuario);
    }

    // El propio usuario solo puede editar su correo; el resto de sus datos
    // (nombre, cedula, carrera, etc.) quedan de solo lectura en su perfil.
    // El JWT usa el correo como clave de autenticacion (JwtAuthenticationFilter
    // busca al usuario por el email del token): si no se reemite un token con
    // el correo nuevo, la sesion activa deja de encontrar al usuario en la
    // siguiente peticion y el cliente queda desautenticado sin aviso.
    @PatchMapping("/me")
    public ResponseEntity<ActualizarEmailResponse> updateMe(@AuthenticationPrincipal Usuario actor,
                                                              @RequestBody ActualizarEmailRequest body) {
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Se normaliza a minusculas igual que en el login: evita que un cambio
        // de solo-mayusculas (mismo correo) dispare una reemision de token que
        // invalide sesiones abiertas en otras pestañas/dispositivos sin motivo.
        String email = body.email() == null ? null : body.email().trim().toLowerCase(Locale.ROOT);
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El correo es obligatorio.");
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El correo no tiene un formato válido.");
        }
        usuarioRepository.findByEmailIgnoreCase(email)
                .filter(existente -> !existente.getId().equals(actor.getId()))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("Ese correo ya está en uso.");
                });

        Usuario usuario = usuarioRepository.findById(actor.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        usuario.setEmail(email);
        usuario.setUpdatedAt(LocalDateTime.now());
        Usuario guardado = usuarioRepository.save(usuario);

        // El rol se toma de la sesion ya autenticada (el authority que puso
        // JwtAuthenticationFilter), no de usuario.getRoles(): ese Set no tiene
        // orden garantizado y para una cuenta con varios roles podria reemitir
        // el token con un rol distinto al que inicio sesion.
        String rol = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(autoridad -> autoridad.replaceFirst("^ROLE_", ""))
                .orElse("ESTUDIANTE");
        String token = jwtTokenProvider.generateToken(guardado.getEmail(), rol);
        return ResponseEntity.ok(new ActualizarEmailResponse(guardado, token));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> getById(@PathVariable Integer id) {
        return usuarioRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Usuario create(@Valid @RequestBody Usuario usuario) {
        validarCedula(usuario);
        if (usuario.getPasswordHash() == null || usuario.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("La contraseña inicial es obligatoria.");
        }
        String claveTemporal = usuario.getPasswordHash();
        usuario.setPasswordHash(passwordEncoder.encode(claveTemporal));
        usuario.setCreatedAt(LocalDateTime.now());
        usuario.setUpdatedAt(LocalDateTime.now());
        usuario.setActivo(true);
        usuario.setPrimerLogin(true);
        usuario.setFuente(normalizarFuente(usuario.getFuente()));
        usuario.setTutorTipo(normalizarTutorTipo(usuario.getTutorTipo()));
        Usuario guardado = usuarioRepository.save(usuario);
        enviarBienvenida(guardado, claveTemporal);
        return guardado;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> update(@PathVariable Integer id, @Valid @RequestBody Usuario details,
                                           @AuthenticationPrincipal Usuario actor) {
        return usuarioRepository.findById(id)
                .map(usuario -> {
                    validarCedula(details);
                    Boolean activoSolicitado = details.getActivo() == null ? usuario.getActivo() : details.getActivo();
                    Set<Rol> rolesSolicitados = details.getRoles() == null || details.getRoles().isEmpty()
                            ? usuario.getRoles()
                            : details.getRoles();
                    validarCambioProtegido(usuario, activoSolicitado, rolesSolicitados, actor);

                    usuario.setCedula(details.getCedula());
                    usuario.setNombre(details.getNombre());
                    usuario.setApellido(details.getApellido());
                    usuario.setEmail(details.getEmail());
                    usuario.setActivo(activoSolicitado);
                    usuario.setPrimerLogin(details.getPrimerLogin());
                    if (details.getFuente() != null && !details.getFuente().isBlank()) {
                        usuario.setFuente(normalizarFuente(details.getFuente()));
                    }
                    if (details.getExternalId() != null) {
                        usuario.setExternalId(details.getExternalId().trim());
                    }
                    if (details.getTutorTipo() != null) {
                        usuario.setTutorTipo(normalizarTutorTipo(details.getTutorTipo()));
                    }
                    usuario.setUpdatedAt(LocalDateTime.now());
                    
                    if (details.getPasswordHash() != null && !details.getPasswordHash().isEmpty() && !details.getPasswordHash().startsWith("$2a$")) {
                        usuario.setPasswordHash(passwordEncoder.encode(details.getPasswordHash()));
                    }
                    
                    if (details.getRoles() != null && !details.getRoles().isEmpty()) {
                        usuario.setRoles(details.getRoles());
                    }
                    
                    return ResponseEntity.ok(usuarioRepository.save(usuario));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id,
                                       @AuthenticationPrincipal Usuario actor) {
        return usuarioRepository.findById(id)
                .map(usuario -> {
                    if (Boolean.TRUE.equals(usuario.getActivo())) {
                        validarCambioProtegido(usuario, false, usuario.getRoles(), actor);
                        usuario.setActivo(false);
                        usuario.setUpdatedAt(LocalDateTime.now());
                        usuarioRepository.save(usuario);
                    }
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void validarCambioProtegido(Usuario usuario, Boolean activoSolicitado,
                                        Set<Rol> rolesSolicitados, Usuario actor) {
        boolean desactiva = Boolean.TRUE.equals(usuario.getActivo()) && !Boolean.TRUE.equals(activoSolicitado);
        boolean pierdeRolAdmin = tieneRol(usuario.getRoles(), "ADMIN") && !tieneRol(rolesSolicitados, "ADMIN");
        if (!desactiva && !pierdeRolAdmin) {
            return;
        }

        if (actor != null && actor.getId() != null && actor.getId().equals(usuario.getId())) {
            throw new IllegalArgumentException("No puedes desactivar tu propia cuenta ni retirar tu rol de administrador.");
        }

        if (tieneRol(usuario.getRoles(), "ADMIN")
                && usuarioRepository.countDistinctByActivoTrueAndRoles_Codigo("ADMIN") <= 1) {
            throw new IllegalArgumentException("Debe permanecer al menos un administrador activo en el sistema.");
        }
    }

    private boolean tieneRol(Set<Rol> roles, String codigo) {
        return roles != null && roles.stream().anyMatch(rol -> codigo.equalsIgnoreCase(rol.getCodigo()));
    }

    // Valida contra el CHECK usuarios_tutor_tipo_check para responder 400 y no 500
    private String normalizarTutorTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        String normalizado = tipo.trim().toUpperCase(Locale.ROOT);
        if (!"PRACTICAS".equals(normalizado) && !"VINCULACION".equals(normalizado) && !"AMBOS".equals(normalizado)) {
            throw new IllegalArgumentException("El tipo de tutor debe ser PRACTICAS, VINCULACION o AMBOS.");
        }
        return normalizado;
    }

    private String normalizarFuente(String fuente) {
        String normalizada = fuente == null || fuente.isBlank()
                ? "MANUAL"
                : fuente.trim().toUpperCase(Locale.ROOT);
        if (!FUENTES.contains(normalizada)) {
            throw new IllegalArgumentException("La fuente del usuario no es válida.");
        }
        return normalizada;
    }

    private void validarCedula(Usuario usuario) {
        if (usuario.getCedula() == null || usuario.getCedula().isBlank()) {
            throw new IllegalArgumentException("La cédula es obligatoria.");
        }
        String cedula = usuario.getCedula().trim();
        if (cedula.length() > 20) {
            throw new IllegalArgumentException("La cédula no puede superar 20 caracteres.");
        }
        usuario.setCedula(cedula);
    }

    private void enviarBienvenida(Usuario usuario, String claveTemporal) {
        try {
            correoNotificacionComponent.enviarBienvenida(usuario, claveTemporal);
        } catch (RuntimeException e) {
            log.warn("La cuenta {} fue creada, pero no se pudo programar su correo de bienvenida: {}",
                    usuario.getId(), e.getMessage());
            log.debug("Detalle del fallo al programar bienvenida", e);
        }
    }

    public record TutorResumen(Integer id, String nombre, String apellido, String email,
                               Boolean activo, String tutorTipo, List<RolResumen> roles) {}

    public record CandidatoEstudiante(Integer id, String nombre, String apellido, String email) {}

    public record ActualizarEmailRequest(String email) {}

    public record ActualizarEmailResponse(Usuario usuario, String token) {}

    public record RolResumen(String codigo) {}
}
