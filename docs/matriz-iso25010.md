# Matriz de cumplimiento ISO/IEC 25010

Evaluación del sistema contra las características de calidad de producto de
ISO/IEC 25010, con la evidencia concreta implementada durante las fases 1-30,
revisada el 2026-07-13.

Leyenda: ✅ cubierto para el alcance MVP · 🟡 parcial (pendiente conocido) ·
⬜ fuera de alcance del MVP.

## 1. Adecuación funcional

| Subcaracterística | Estado | Evidencia |
|---|:-:|---|
| Completitud funcional | ✅ | Flujo académico completo operativo: documentos → postulación meritocrática → procesamiento/ajuste manual → consolidación → tutor → bitácoras/asistencias → calificaciones por parcial → encuesta. Trazabilidad pantalla↔API↔tabla en `matriz-trazabilidad-fase3.md`. |
| Corrección funcional | ✅ | Reglas de negocio validadas en el backend (Fase 6): exclusividad de proceso activo, fechas de convocatoria, secuencialidad de parciales, transiciones de estado, rangos de notas y horas. |
| Pertinencia funcional | ✅ | El score meritocrático usa notas académicas verificadas del semestre anterior, cargadas manualmente o por importación institucional CSV/API-ready. Las respuestas Likert y el comentario de la encuesta se persisten en `ENCUESTAS_SATISFACCION`, además del estado de encuesta completada. |

## 2. Eficiencia de desempeño

| Subcaracterística | Estado | Evidencia |
|---|:-:|---|
| Utilización de recursos | ✅ | Pool de conexiones dimensionado al límite del pooler de Supabase (Hikari 5/15; tests 2 con contexto compartido). Carga de datos con `forkJoin` (peticiones paralelas, una sola pasada). |
| Capacidad | ✅ | Listados masivos (usuarios, estudiantes, empresas, fundaciones, prácticas y vinculación —listado y seguimiento—) paginados, filtrados y ordenados en PostgreSQL vía `Specification` + `Pageable`, con el alcance del coordinador aplicado en SQL y lista blanca de campos de orden. Búsqueda de estudiantes por autocompletado asíncrono (10 resultados por consulta) y caché `shareReplay` con invalidación en mutaciones. Reportes agregados y catálogos pequeños paginan en memoria de forma deliberada. |

## 3. Compatibilidad

| Subcaracterística | Estado | Evidencia |
|---|:-:|---|
| Interoperabilidad | ✅ | API REST JSON estándar; CORS configurable por variable (`APP_CORS_ALLOWED_ORIGINS`) para localhost y ngrok; errores en formato JSON uniforme (`GlobalExceptionHandler`). |
| Coexistencia | ✅ | Frontend y backend desacoplados (proxy dev / mismo origen en prod); la BD admite acceso concurrente de app, tests y SQL Editor. |

## 4. Capacidad de interacción (usabilidad)

| Subcaracterística | Estado | Evidencia |
|---|:-:|---|
| Operabilidad | ✅ | Dashboard por rol con pestañas guiadas por etapa, seguimiento unificado de coordinación, navegación móvil desplegable, selects para asignación de tutor y mensajes de negocio legibles (`err.error.error`). |
| Protección frente a errores del usuario | ✅ | Validaciones espejo en frontend y backend; confirmaciones antes de eliminar; toasts en lugar de `alert()`. |
| Reconocibilidad / aprendizaje | ✅ | Guion de pruebas por rol (`guion-pruebas-fase9.md`) documenta el flujo esperado paso a paso. |

## 5. Fiabilidad

| Subcaracterística | Estado | Evidencia |
|---|:-:|---|
| Madurez | ✅ | 101 pruebas backend sin fallos, errores ni casos omitidos; 25 pruebas frontend en verde. Incluyen matriz de autorización, alcance por carrera/proceso, cierres, documentos, importación idempotente, seguimiento unificado y reglas académicas. |
| Disponibilidad | ✅ | BD gestionada (Supabase); la app se recupera de reinicios sin estado local (sesión JWT stateless). |
| Tolerancia a fallos | ✅ | `catchError` en loaders, errores HTTP centralizados y correo SMTP asíncrono/tolerante: una caída del proveedor no interrumpe el flujo académico. |
| Capacidad de recuperación | ✅ | Datos 100 % en BD (Fase 5 eliminó localStorage); F5 o cambio de navegador no pierde estado; scripts de reset repetibles (`fase4_reset_estudiante.sql`). |

## 6. Seguridad

| Subcaracterística | Estado | Evidencia |
|---|:-:|---|
| Confidencialidad | ✅ | JWT stateless con expiración; `passwordHash` nunca se serializa; auditoría reservada a ADMIN; COORDINADOR recibe un directorio mínimo de tutores y el alcance por carrera y proceso se aplica en el servidor. |
| Integridad | ✅ | Autorización por método HTTP y rol en `SecurityConfig` (matriz probada por tests); transiciones de estado y reglas de negocio en el backend, no solo en la UI; contraseñas con BCrypt. |
| Responsabilidad (accountability) | ✅ | `AUDITORIA` registra actor, fecha y snapshots en importaciones, cierres, notas, requisitos documentales, cambios de tutor y ajustes meritocráticos; el cliente no puede insertar auditoría. |
| Autenticidad | ✅ | Login contra BD con BCrypt; el filtro JWT verifica que el usuario exista y esté activo en cada petición. |
| Resistencia (hardening) | ✅ | Secretos fuera del repositorio (`.env.properties` + plantilla); credenciales de BD y secreto JWT rotados tras haber quedado en el historial de git. El login usa respuesta genérica, bloqueo temporal persistente bajo bloqueo de fila y rate limiting por origen configurable para la instancia única del MVP. |

## 7. Mantenibilidad

| Subcaracterística | Estado | Evidencia |
|---|:-:|---|
| Modularidad | ✅ | Backend organizado por dominio y frontend con servicios por recurso y componentes standalone compartidos. El monolito mantiene límites claros sin introducir microservicios prematuros. |
| Reusabilidad | ✅ | `ExpedienteService`, `AlcanceCoordinador`, `NotificacionEmitter`, `CierreExpedienteComponent` e `ImportacionInstitucionalComponent` concentran reglas compartidas. |
| Analizabilidad | ✅ | Matriz de trazabilidad, README con arquitectura y reglas, logs SLF4J (sin `printStackTrace`). |
| Capacidad de modificación | ✅ | `ddl-auto=validate` detecta divergencias entidad↔esquema al arrancar; configuración externalizada. |
| Capacidad de prueba | ✅ | Suites backend y frontend (`gradlew test`, `npm test`), Playwright en Chromium para acceso público y navegación por rol, workflow CI reproducible y guion manual por rol. |

## 8. Portabilidad / Flexibilidad

| Subcaracterística | Estado | Evidencia |
|---|:-:|---|
| Adaptabilidad | ✅ | Configuración externalizada para BD, JWT, CORS, SMTP y Storage. La importación institucional centraliza el upsert y distingue fuentes `CSV_UNIVERSIDAD`/`API_UNIVERSIDAD`, de modo que un cliente HTTP futuro no cambia el esquema ni las reglas de identidad. |
| Instalabilidad | ✅ | README con puesta en marcha en 3 pasos; SQL idempotente (`IF NOT EXISTS`) listo para pegar en Supabase. |
| Reemplazabilidad | ✅ | PostgreSQL estándar (portable fuera de Supabase); API REST sin dependencias propietarias. |

## Pendientes conocidos (fuera del cierre del MVP)

1. Conexión HTTP con la **API real de la universidad**: el upsert ya acepta la
   fuente `API_UNIVERSIDAD`, pero faltan el contrato oficial, URL, método de
   autenticación, paginación y política de reintentos del proveedor.
2. ~~**Paginación** de listados~~ — CUBIERTO (2026-07-17): paginación, filtros
   y orden resueltos en la base de datos para los listados masivos.
3. Generar el acta final en PDF firmado; el MVP conserva el resumen imprimible
   en JSON como alternativa aceptada.
