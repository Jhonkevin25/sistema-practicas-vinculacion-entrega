# Sistema de Prácticas y Vinculación — UNIBE

Sistema de gestión para la Universidad Iberoamericana del Ecuador (UNIBE) que
administra el ciclo académico completo de **vinculación con la comunidad** y
**prácticas preprofesionales**: convocatorias, vacantes, postulación
meritocrática, asignación de tutores, bitácoras, asistencias, calificaciones
por parciales y encuestas de pertinencia.

## Arquitectura

| Capa | Tecnología | Ubicación |
|---|---|---|
| API REST | Spring Boot 4 (Java 17), Spring Security + JWT, Gradle | `backend/sistema-practicas` |
| SPA | Angular 21 (standalone components + signals), Tailwind | `frontend` |
| Base de datos | PostgreSQL alojado en Supabase (SQL crudo, sin framework de migraciones) | `database/*.sql` |

- **Backend**: paquete por dominio (`empresa`, `estudiante`, `practica`,
  `vinculacion`, `vacante`, `postulacion`, `bitacora`, `asistencia`,
  `evaluacion`, `documento`, `favorito`, `auditoria`, `coordinador`, …), cada
  uno con Entidad JPA + Repositorio + Controlador (sin capa de servicios;
  la única excepción es `auth`). Las reglas de negocio viven en los
  controladores y las reglas de autorización por rol en
  `config/SecurityConfig`.
- **Frontend**: un servicio Angular por recurso en `core/services/`
  (envoltorios de `HttpClient` sobre `/api/<recurso>`), guard de sesión,
  interceptores de token y de errores, y componentes de dashboard por módulo.
  La lógica académica compartida está en `core/services/expediente.service.ts`.
- **Esquema**: `spring.jpa.hibernate.ddl-auto=validate` — la BD es la fuente
  de verdad; los cambios se hacen primero en SQL (Supabase SQL Editor) y
  después se actualizan las entidades.

## Puesta en marcha

### 1. Base de datos (Supabase)

Ejecutar en el SQL Editor, en orden: `database/schema.sql`,
`database/seed.sql`, `database/migration_v2_iso25010.sql`,
`database/fase5_docs_alcance.sql` y `database/fase4_usuarios_prueba.sql`
(usuarios de prueba + convocatoria).

> Conexión: usar el **pooler** de Supabase (el host directo es solo IPv6).
> El pooler en modo sesión admite máx. 15 clientes; por eso Hikari está
> limitado a 5 conexiones en `application.properties`.

### 2. Backend

```bash
cd backend/sistema-practicas
cp .env.properties.example .env.properties   # completar credenciales
./gradlew.bat bootRun                        # API en http://localhost:8080
```

`.env.properties` (no versionado) define `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `APP_JWT_SECRET` y `APP_CORS_ALLOWED_ORIGINS`.

### 3. Frontend

```bash
cd frontend
npm install
npm start        # http://localhost:4200 (proxy /api -> localhost:8080)
```

## Roles y permisos (MVP)

| Acción | ADMIN | COORDINADOR | TUTOR | ESTUDIANTE |
|---|:-:|:-:|:-:|:-:|
| Administrar usuarios | ✔ | lectura | — | — |
| Estudiantes (crear/editar) | ✔ | ✔ | lectura | solo `/me` |
| Catálogos y vacantes (escritura) | ✔ | ✔ | lectura | lectura |
| Fechas de convocatoria y plazos | ✔ | ✔ | lectura | lectura |
| Postular (meritocracia) | ✔ | ✔ | — | ✔ |
| Procesar/consolidar postulaciones | ✔ | ✔ | — | — |
| Prácticas/vinculación (crear/eliminar) | ✔ | ✔ | — | — |
| Prácticas/vinculación (actualizar) | ✔ | ✔ | ✔ | — |
| Calificar parciales | ✔ | ✔ | ✔ | — |
| Encuesta de pertinencia | — | — | — | ✔ |
| Bitácoras/asistencias (registrar) | ✔ | ✔ | ✔ | ✔ |
| Aprobar bitácoras | ✔ | ✔ | ✔ | — |
| Documentos del expediente / favoritos | — | — | — | ✔ |
| Auditoría | ✔ | ✔ | — | — |

- El rol **EMPRESA** existe en la BD pero está fuera del MVP (sin acceso al API).
- El **alcance por carrera** del coordinador (tabla `COORDINADOR_CARRERAS`)
  se aplica en el backend: sus listados solo devuelven sus carreras.

Usuarios de prueba (tras `fase4_usuarios_prueba.sql`):
`coordinador@sistema.edu.ec` / `Coordinador2026*`,
`tutor@sistema.edu.ec` / `Tutor2026*`,
`estudiante@sistema.edu.ec` / `Estudiante2026*`.

## Reglas de negocio (validadas en el backend)

- Documentación obligatoria (cv, carta, cédula) antes de postular.
- Una sola práctica/vinculación activa y una sola postulación en proceso
  por estudiante; postulación solo dentro de fechas de convocatoria.
- Transiciones de estado solo hacia adelante
  (`pendiente → en_curso → completado`;
  `Pendiente → Procesado → Aprobado/Rechazado`).
- Notas 0-10, parciales secuenciales (P2 exige P1 completo), horas
  requeridas > 0, bitácoras de 1-24 horas.
- Etapa académica derivada del expediente:
  Vinculación (160 h) → Práctica I (240 h) → Práctica II (240 h).

## Pruebas

```bash
cd backend/sistema-practicas && ./gradlew.bat test   # matriz de autorización por rol (MockMvc)
cd frontend && npm test                              # lógica académica y loaders por rol (vitest)
```

Guion manual por rol: `docs/guion-pruebas-fase9.md`.

## Documentación adicional

- `docs/matriz-iso25010.md` — matriz de cumplimiento ISO/IEC 25010.
- `docs/matriz-trazabilidad-fase3.md` — trazabilidad pantalla ↔ API ↔ tabla.
- `docs/guion-pruebas-fase9.md` — guion de pruebas manuales vigente.
- `database/fase4_reset_estudiante.sql` — reinicia el expediente del
  estudiante de prueba para repetir el flujo completo.

## Exponer por ngrok

Agregar la URL de ngrok a `APP_CORS_ALLOWED_ORIGINS` en `.env.properties`
(lista separada por comas) y reiniciar el backend.
