package ec.edu.unibe.sistema_practicas.importacion;

import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import ec.edu.unibe.sistema_practicas.notaacademica.NotaAcademica;
import ec.edu.unibe.sistema_practicas.notaacademica.NotaAcademicaRepository;
import ec.edu.unibe.sistema_practicas.notificacion.CorreoNotificacionComponent;
import ec.edu.unibe.sistema_practicas.rol.Rol;
import ec.edu.unibe.sistema_practicas.rol.RolRepository;
import ec.edu.unibe.sistema_practicas.usuario.Usuario;
import ec.edu.unibe.sistema_practicas.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ImportacionInstitucionalComponent {

    private static final String CARACTERES_CLAVE =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;
    private final RolRepository rolRepository;
    private final NotaAcademicaRepository notaAcademicaRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoNotificacionComponent correoNotificacionComponent;

    @Transactional
    public AccionImportacion importarEstudiante(FilaEstudianteInstitucional fila) {
        return importarEstudiante(fila, FuenteInstitucional.CSV_UNIVERSIDAD);
    }

    @Transactional
    public AccionImportacion importarEstudiante(FilaEstudianteInstitucional fila,
                                                 FuenteInstitucional fuente) {
        FuenteInstitucional fuenteFinal = fuenteValida(fuente);
        String externalId = normalizarExternalId(fila.externalId());
        String email = normalizarEmail(fila.emailInstitucional());
        String cedula = maximo(requerido(fila.cedula(), "La cédula es obligatoria."), 20, "cédula");
        String matricula = fila.matricula() == null || fila.matricula().isBlank()
                ? externalId
                : fila.matricula().trim();
        matricula = maximo(matricula, 20, "matrícula");
        if (fila.semestre() == null || fila.semestre() < 1 || fila.semestre() > 20) {
            throw new IllegalArgumentException("El semestre debe estar entre 1 y 20.");
        }

        Optional<Usuario> porExternal = usuarioRepository.findByExternalIdIgnoreCase(externalId);
        Optional<Usuario> porEmail = usuarioRepository.findByEmailIgnoreCase(email);
        if (porExternal.isPresent() && porEmail.isPresent()
                && !porExternal.get().getId().equals(porEmail.get().getId())) {
            throw new IllegalArgumentException("El ID externo y el correo pertenecen a usuarios diferentes.");
        }

        boolean nuevo = porExternal.isEmpty() && porEmail.isEmpty();
        Usuario usuario = porExternal.or(() -> porEmail).orElseGet(Usuario::new);
        boolean enlazado = !nuevo
                && porExternal.isEmpty()
                && "MANUAL".equalsIgnoreCase(usuario.getFuente());

        if (!nuevo && porExternal.isEmpty()
                && usuario.getExternalId() != null
                && !usuario.getExternalId().isBlank()
                && !usuario.getExternalId().equalsIgnoreCase(externalId)
                && !"MANUAL".equalsIgnoreCase(usuario.getFuente())) {
            throw new IllegalArgumentException("El correo ya está enlazado a otro ID institucional.");
        }

        usuarioRepository.findByCedula(cedula)
                .filter(existente -> usuario.getId() == null || !existente.getId().equals(usuario.getId()))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("La cédula pertenece a otro usuario.");
                });

        Rol rolEstudiante = rolRepository.findByCodigo("ESTUDIANTE")
                .orElseThrow(() -> new IllegalArgumentException("No existe el rol ESTUDIANTE en el sistema."));
        validarRoles(usuario);
        Set<Rol> roles = usuario.getRoles() == null ? new HashSet<>() : new HashSet<>(usuario.getRoles());
        roles.add(rolEstudiante);

        String claveTemporal = null;
        LocalDateTime ahora = LocalDateTime.now();
        if (nuevo) {
            claveTemporal = generarClaveTemporal();
            usuario.setPasswordHash(passwordEncoder.encode(claveTemporal));
            usuario.setPrimerLogin(true);
            usuario.setCreatedAt(ahora);
        }
        usuario.setCedula(cedula);
        usuario.setNombre(maximo(requerido(fila.nombre(), "El nombre es obligatorio."), 100, "nombre"));
        usuario.setApellido(maximo(requerido(fila.apellido(), "El apellido es obligatorio."), 100, "apellido"));
        usuario.setEmail(email);
        usuario.setActivo(fila.matriculaActiva());
        usuario.setFuente(fuenteFinal.name());
        usuario.setExternalId(externalId);
        usuario.setUpdatedAt(ahora);
        usuario.setRoles(roles);
        Usuario guardado = usuarioRepository.save(usuario);

        Optional<Estudiante> porUsuario = guardado.getId() == null
                ? Optional.empty()
                : estudianteRepository.findByUsuarioId(guardado.getId());
        Optional<Estudiante> porMatricula = estudianteRepository.findByMatricula(matricula);
        if (porUsuario.isPresent() && porMatricula.isPresent()
                && !porUsuario.get().getId().equals(porMatricula.get().getId())) {
            throw new IllegalArgumentException("La matrícula pertenece a otro expediente estudiantil.");
        }
        if (porMatricula.isPresent()
                && porMatricula.get().getUsuario() != null
                && !porMatricula.get().getUsuario().getId().equals(guardado.getId())) {
            throw new IllegalArgumentException("La matrícula pertenece a otro usuario.");
        }

        Estudiante estudiante = porUsuario.or(() -> porMatricula).orElseGet(Estudiante::new);
        estudiante.setUsuario(guardado);
        estudiante.setMatricula(matricula);
        estudiante.setCarrera(maximo(requerido(fila.carrera(), "La carrera es obligatoria."), 200, "carrera"));
        estudiante.setSemestre(fila.semestre());
        estudiante.setPeriodoAcademico(maximoOpcional(vacioANull(fila.periodoAcademico()), 20, "periodo_academico"));
        estudianteRepository.save(estudiante);

        if (nuevo && claveTemporal != null) {
            programarBienvenida(guardado, claveTemporal);
        }
        if (nuevo) return AccionImportacion.CREADO;
        return enlazado ? AccionImportacion.ENLAZADO : AccionImportacion.ACTUALIZADO;
    }

    @Transactional
    public AccionImportacion importarNota(FilaNotaInstitucional fila, Usuario verificadoPor) {
        return importarNota(fila, verificadoPor, FuenteInstitucional.CSV_UNIVERSIDAD);
    }

    @Transactional
    public AccionImportacion importarNota(FilaNotaInstitucional fila, Usuario verificadoPor,
                                           FuenteInstitucional fuente) {
        FuenteInstitucional fuenteFinal = fuenteValida(fuente);
        String externalId = vacioANull(fila.externalId());
        String email = vacioANull(fila.emailInstitucional());
        Usuario usuario = buscarUsuario(externalId, email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe un usuario enlazado al ID externo o correo indicado."));
        Estudiante estudiante = estudianteRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El usuario institucional no tiene expediente de estudiante."));

        int semestre = fila.semestre() == null
                ? (estudiante.getSemestre() == null ? 0 : estudiante.getSemestre() - 1)
                : fila.semestre();
        if (semestre < 1) {
            throw new IllegalArgumentException(
                    "No se pudo inferir el semestre de la nota; incluye la columna semestre.");
        }

        String periodo = maximo(requerido(fila.periodoAcademico(), "El periodo académico es obligatorio."),
                20, "periodo_academico");
        if (fila.promedio() == null || fila.promedio() < 0 || fila.promedio() > 10) {
            throw new IllegalArgumentException("El promedio debe estar entre 0 y 10.");
        }
        Optional<NotaAcademica> existente = notaAcademicaRepository
                .findByEstudianteIdAndPeriodoAcademicoAndSemestre(estudiante.getId(), periodo, semestre);
        NotaAcademica nota = existente.orElseGet(NotaAcademica::new);
        LocalDateTime ahora = LocalDateTime.now();
        if (nota.getId() == null) {
            nota.setCreatedAt(ahora);
        }
        nota.setEstudiante(estudiante);
        nota.setCarrera(estudiante.getCarrera());
        nota.setSemestre(semestre);
        nota.setPeriodoAcademico(periodo);
        nota.setPromedio(fila.promedio());
        nota.setFuente(fuenteFinal.name());
        String notaExternalId = usuario.getExternalId() == null || usuario.getExternalId().isBlank()
                ? vacioANull(externalId)
                : normalizarExternalId(usuario.getExternalId());
        nota.setExternalId(notaExternalId == null ? null : notaExternalId.toUpperCase(Locale.ROOT));
        nota.setEmailInstitucional(usuario.getEmail());
        nota.setEstado("VERIFICADO");
        nota.setObservaciones("Nota importada desde " + fuenteFinal.name() + ".");
        nota.setVerificadoPor(verificadoPor);
        nota.setFechaVerificacion(ahora);
        nota.setUpdatedAt(ahora);
        notaAcademicaRepository.save(nota);
        return existente.isPresent() ? AccionImportacion.ACTUALIZADO : AccionImportacion.CREADO;
    }

    private FuenteInstitucional fuenteValida(FuenteInstitucional fuente) {
        if (fuente == null) {
            throw new IllegalArgumentException("La fuente institucional es obligatoria.");
        }
        return fuente;
    }

    private Optional<Usuario> buscarUsuario(String externalId, String email) {
        Optional<Usuario> porExternal = externalId == null
                ? Optional.empty()
                : usuarioRepository.findByExternalIdIgnoreCase(normalizarExternalId(externalId));
        if (porExternal.isPresent()) return porExternal;
        return email == null ? Optional.empty() : usuarioRepository.findByEmailIgnoreCase(normalizarEmail(email));
    }

    private void validarRoles(Usuario usuario) {
        if (usuario.getRoles() == null || usuario.getRoles().isEmpty()) return;
        boolean tieneRolNoEstudiante = usuario.getRoles().stream()
                .anyMatch(rol -> rol.getCodigo() != null && !"ESTUDIANTE".equalsIgnoreCase(rol.getCodigo()));
        if (tieneRolNoEstudiante) {
            throw new IllegalArgumentException(
                    "El correo institucional pertenece a una cuenta con un rol distinto de ESTUDIANTE.");
        }
    }

    private void programarBienvenida(Usuario usuario, String claveTemporal) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    correoNotificacionComponent.enviarBienvenida(usuario, claveTemporal);
                }
            });
        } else {
            correoNotificacionComponent.enviarBienvenida(usuario, claveTemporal);
        }
    }

    private String generarClaveTemporal() {
        StringBuilder clave = new StringBuilder("A7!");
        for (int i = 3; i < 14; i++) {
            clave.append(CARACTERES_CLAVE.charAt(RANDOM.nextInt(CARACTERES_CLAVE.length())));
        }
        return clave.toString();
    }

    private String normalizarExternalId(String externalId) {
        return maximo(requerido(externalId, "El external_id es obligatorio."), 120, "external_id")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizarEmail(String email) {
        String normalizado = requerido(email, "El correo institucional es obligatorio.")
                .toLowerCase(Locale.ROOT);
        if (!normalizado.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("El correo institucional no es válido.");
        }
        return maximo(normalizado, 150, "correo institucional");
    }

    private String requerido(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }

    private String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private String maximo(String valor, int longitud, String campo) {
        if (valor.length() > longitud) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " supera el máximo de " + longitud + " caracteres.");
        }
        return valor;
    }

    private String maximoOpcional(String valor, int longitud, String campo) {
        return valor == null ? null : maximo(valor, longitud, campo);
    }
}
