# Fase 3 — Matriz de Trazabilidad Frontend ↔ Backend ↔ Base de Datos

Fecha: 2026-07-02. Insumo para las Fases 4-7 del plan de mejora ISO/IEC 25010.

## 1. Matriz principal

| Pantalla (frontend) | Servicio Angular | Endpoint backend | Entidad Java | Tabla Supabase | Estado |
|---|---|---|---|---|---|
| `dashboard/usuarios` | `usuario.service` | `/api/usuarios` | `Usuario` | `USUARIOS`, `USUARIOS_ROLES` | ✅ Conectado |
| `dashboard/estudiantes` | `estudiante.service`, `usuario.service` | `/api/estudiantes`, `/api/usuarios` | `Estudiante` | `ESTUDIANTES` | ✅ Conectado |
| `dashboard/empresas` | `empresa.service` | `/api/empresas` | `Empresa` | `EMPRESAS` | ✅ Conectado |
| `dashboard/fundaciones-proyectos` | `fundacion.service`, `proyecto.service` | `/api/fundaciones`, `/api/proyectos` | `Fundacion`, `Proyecto` | `FUNDACIONES`, `PROYECTOS` | ✅ Conectado |
| `dashboard/practicas` (lista/asignación) | `practica.service` | `/api/practicas` | `Practica` | `PRACTICAS` | ✅ Conectado |
| `dashboard/practicas` (fechas convocatoria) | `configuracion.service` | `/api/configuracion/fechas` | `FechasConvocatoria` | `FECHAS_CONVOCATORIA` | ✅ Conectado |
| `dashboard/practicas` (vacantes) | ❌ ninguno (existe `vacante.service` sin usar) | `/api/vacantes` existe sin consumir | `VacantePractica` | `VACANTES_PRACTICAS` | 🔴 SIMULADO — localStorage `pravi_vacantes` |
| `dashboard/practicas` (postulaciones) | ❌ ninguno (existe `postulacion.service` sin usar) | `/api/postulaciones` existe sin consumir | `PostulacionMeritocratica` | `POSTULACIONES_MERITOCRATICAS` | 🔴 SIMULADO — localStorage `pravi_postulaciones_meritocraticas` |
| `dashboard/practicas` (bitácoras) | ❌ ninguno (existe `bitacora.service` sin usar) | `/api/bitacoras` existe sin consumir | `Bitacora` | `BITACORAS` | 🔴 SIMULADO — localStorage `pravi_bitacoras` |
| `dashboard/practicas` (asistencias) | ❌ ninguno (existe `asistencia.service` sin usar) | `/api/asistencias` existe sin consumir | `Asistencia` | `ASISTENCIAS` | 🔴 SIMULADO — localStorage `pravi_asistencias` |
| `dashboard/practicas` (calificaciones) | ❌ ninguno | ❌ no existe | ❌ no existe | `EVALUACIONES_PRACTICAS`, `EVALUACIONES_PRACTICAS_DETALLE` | 🔴 SIMULADO — localStorage `pravi_grades_store` |
| `dashboard/practicas` (fechas límite calificación) | ❌ ninguno | ❌ no existe | ❌ no existe | `FECHAS_LIMITE_CALIFICACION` | 🔴 SIMULADO — localStorage `pravi_deadlines` |
| `dashboard/practicas` (encuestas por parcial) | ❌ ninguno | ❌ no existe | ❌ no existe | ❌ no existe tabla | 🔴 SIMULADO — localStorage `pravi_surveys` |
| `dashboard/practicas` (favoritos vacantes) | ❌ ninguno | ❌ no existe | ❌ no existe | `FAVORITOS_VACANTES` | 🔴 SIMULADO — localStorage `pravi_fav_vacantes` |
| `dashboard/practicas` (auditoría visible) | ❌ ninguno | ❌ no existe | ❌ no existe | `AUDITORIA` | 🔴 SIMULADO — localStorage `pravi_audit_logs` |
| `dashboard/vinculacion` (expedientes) | `vinculacion.service` | `/api/vinculacion` | `Vinculacion` | `VINCULACION` | ✅ Conectado |
| `dashboard/vinculacion` (proyectos ofertados) | ❌ usa arreglo hardcodeado `ProyectoVinculacion` | `/api/proyectos` existe sin consumir aquí | `Proyecto` | `PROYECTOS` | 🔴 SIMULADO — señal con 2 proyectos fijos |
| `dashboard/vinculacion` (fechas convocatoria) | ❌ señales hardcodeadas (`2025-06-20`, `2025-06-25`) | `/api/configuracion/fechas` existe sin consumir aquí | `FechasConvocatoria` | `FECHAS_CONVOCATORIA` | 🔴 SIMULADO |
| `dashboard/vinculacion` (docs estudiante CV/carta/cédula) | ❌ ninguno | ❌ no existe | ❌ no existe | `DOCS_VINCULACION` | 🔴 SIMULADO — localStorage `pravi_vinc_cv_loaded` + señales |
| `dashboard/practicas` (docs estudiante) | ❌ ninguno | ❌ no existe | ❌ no existe | `DOCUMENTOS_PRACTICAS` | 🔴 SIMULADO |
| `dashboard/overview` (stats ADMIN/COORD) | `estudiante/empresa/practica/vinculacion.service` | varios GET | varias | varias | 🟡 Conectado con fallback hardcodeado en error (14/8/7/4) |
| `dashboard/overview` (avance ESTUDIANTE) | ❌ ninguno | ❌ no existe | — | `VINCULACION` + `PRACTICAS` (derivable) | 🔴 SIMULADO — localStorage `mock_student_step` |
| `auth/login` | `auth.service` | `/api/auth/login` | `Usuario`, `Rol` | `USUARIOS`, `ROLES`, `USUARIOS_ROLES` | ✅ Conectado |
| Perfil coordinador (tipo + carreras asignadas) | ❌ `auth.service` usa localStorage `pravi_coord_tipo`, `pravi_coord_carreras` | ❌ no existe | ❌ no existe | ❌ no existe tabla | 🔴 SIMULADO — además define alcance de seguridad |
| Perfil tutor (tipo práctica/vinculación) | ❌ localStorage `pravi_tutor_tipo` | ❌ no existe | ❌ no existe | ❌ no existe tabla | 🔴 SIMULADO |

## 2. Tablas de Supabase sin API (backend no las usa)

| Tabla | Observación |
|---|---|
| `EVALUACIONES_PRACTICAS`, `EVALUACIONES_PRACTICAS_DETALLE` | Notas por parcial simuladas en `pravi_grades_store` |
| `EVAL_VINCULACION` | Sin uso en ninguna capa |
| `DOCUMENTOS_PRACTICAS`, `DOCS_VINCULACION` | Carga documental simulada con señales/localStorage |
| `FECHAS_LIMITE_CALIFICACION` | Simulada en `pravi_deadlines` |
| `FAVORITOS_VACANTES` | Simulada en `pravi_fav_vacantes` |
| `AUDITORIA` | Log visible simulado en `pravi_audit_logs`; nada escribe auditoría real |
| `TUTORES_EMPRESA` | Sin uso en ninguna capa |
| `SESIONES` | Sin uso; el JWT es stateless (decidir en Fase 7 si se elimina o se usa para revocación) |
| `MODULOS`, `PERMISOS`, `ROLES_MODULOS_PERMISOS` | El backend aplica roles por código (Fase 2), no permisos granulares por módulo |

## 3. API/servicios existentes sin consumo real

| Pieza | Situación |
|---|---|
| `/api/vacantes` + `vacante.service.ts` | Controlador, entidad, tabla y servicio listos; la pantalla usa localStorage |
| `/api/postulaciones` + `postulacion.service.ts` | Ídem |
| `/api/bitacoras` + `bitacora.service.ts` | Ídem |
| `/api/asistencias` + `asistencia.service.ts` | Ídem |
| `/api/proyectos` en pantalla vinculación | La pantalla fundaciones-proyectos sí lo usa; la oferta de proyectos en vinculación no |
| `/api/configuracion/fechas` en pantalla vinculación | practicas.ts sí lo usa; vinculacion.ts no |

## 4. Desalineaciones de contrato detectadas

1. **`Vacante` local vs `VacantePractica`**: `practicas.ts` define una interfaz propia con `empresa: string` (nombre plano); el backend y `vacante.service.ts` usan `empresa: Empresa` (objeto con `id`). Al conectar, hay que migrar la pantalla a la interfaz del servicio y resolver la empresa por id.
2. **`PostulacionMeritocratica` local vs backend**: la interfaz local de `practicas.ts` difiere de la del servicio (backend exige `estudiante`, `pref1`/`pref2` como objetos `VacantePractica`, `promedio`, `score`; el flujo local guarda estructuras propias).
3. **Fechas**: el backend usa `LocalDate` (ISO `yyyy-MM-dd`) — compatible con los strings del frontend, pero hay fechas hardcodeadas (`2025-07-31`, `2025-06-20`…) que en 2026 ya quedaron en el pasado y bloquean/permiten flujos incorrectamente.
4. **`mock_student_step`**: la etapa académica del estudiante (Vinculación → Práctica I → Práctica II) no existe como dato persistido; se deriva de localStorage. La fuente real debería derivarse de `VINCULACION`/`PRACTICAS` completadas o persistirse en `ESTUDIANTES`.
5. **Alcance del coordinador**: `pravi_coord_carreras`/`pravi_coord_tipo` filtran datos en el cliente; cualquier coordinador ve por API todos los datos. Falta soporte en BD (p. ej. tabla `COORDINADORES_CARRERAS`) y filtro en backend.
6. **`AuthService.login` (backend)** hace `estudianteRepository.findAll().stream()` para hallar la carrera; existe `findByUsuarioEmail` — arreglo trivial de eficiencia (Fase 6).
7. **Encuestas por parcial** (`pravi_surveys`): no existe tabla; decidir en Fase 7 si se crea o se elimina la funcionalidad del MVP.

## 5. Conclusión operativa (qué conectar, qué crear, qué eliminar)

**Conectar ya (todo existe, solo falta usar la API) — Fase 5 prioridad 1:**
1. Vacantes → `vacante.service` (migrar interfaz local a la del servicio).
2. Postulaciones → `postulacion.service`.
3. Bitácoras → `bitacora.service`.
4. Asistencias → `asistencia.service`.
5. Proyectos ofertados en vinculación → `proyecto.service`.
6. Fechas de convocatoria en vinculación → `configuracion.service` (tipo `VINCULACION`).

**Crear endpoints/entidades (tabla ya existe) — Fases 5-6:**
7. Evaluaciones/notas (`EVALUACIONES_PRACTICAS[_DETALLE]`, `EVAL_VINCULACION`).
8. Documentos (`DOCUMENTOS_PRACTICAS`, `DOCS_VINCULACION`) — al menos estado documental sin archivo binario para el MVP.
9. Fechas límite de calificación (`FECHAS_LIMITE_CALIFICACION`).
10. Favoritos (`FAVORITOS_VACANTES`) — prioridad 2.
11. Auditoría (`AUDITORIA`) — escritura desde backend; lectura para ADMIN — prioridad 2.

**Requiere SQL nuevo en Supabase — Fase 7:**
12. Alcance del coordinador (tipo + carreras) — tabla nueva.
13. Perfil del tutor (tipo) — columna o tabla nueva.
14. Encuestas por parcial — tabla nueva o descartar del MVP.
15. Etapa académica del estudiante — decidir: derivada o columna en `ESTUDIANTES`.

**Eliminar como simulación (sin reemplazo):**
16. Fallback de stats hardcodeadas en `overview.ts` (mostrar error real en su lugar).
17. `fechaActual` manipulable en `vinculacion.ts` ("For testing gating") — usar fecha del sistema/servidor.
