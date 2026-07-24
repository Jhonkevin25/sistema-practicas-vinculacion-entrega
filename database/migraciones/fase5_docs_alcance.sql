-- ============================================================
-- FASE 5 (prioridad 2) — Tablas pendientes para eliminar los
-- últimos datos simulados en localStorage:
--   1. DOCS_ESTUDIANTE      → documentos del expediente (cv, carta, cedula)
--                             hoy en signals que se pierden con F5
--   2. COORDINADOR_CARRERAS → alcance del coordinador (carreras y tipo)
--                             hoy en localStorage 'pravi_coord_carreras'
--
-- Ejecutar en Supabase SQL Editor. Es idempotente (IF NOT EXISTS).
-- Después de aplicarlo, avisar para conectar el backend (las entidades
-- JPA se agregan recién cuando estas tablas existan, por ddl-auto=validate).
-- ============================================================

-- 1. Documentos del expediente del estudiante (previos a la postulación,
--    por eso referencian ESTUDIANTES y no PRACTICAS/VINCULACION)
CREATE TABLE IF NOT EXISTS DOCS_ESTUDIANTE (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    requerido_id        INT,
    tipo_documento      VARCHAR(100) NOT NULL,
    url_archivo         VARCHAR(300),
    proceso             VARCHAR(30) DEFAULT 'GENERAL'
                        CHECK (proceso IN ('GENERAL', 'PRACTICAS', 'VINCULACION')),
    carrera             VARCHAR(150),
    etapa               VARCHAR(80),
    estado              VARCHAR(30) DEFAULT 'cargado'
                        CHECK (estado IN ('pendiente', 'cargado', 'en_revision', 'aprobado', 'rechazado', 'requiere_correccion')),
    observacion         TEXT,
    revisado_por        INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    fecha_revision      TIMESTAMP,
    fecha_subida        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(estudiante_id, tipo_documento, proceso)
);

-- 2. Alcance del coordinador: carreras asignadas y tipo de coordinación.
--    El tipo se repite por fila para no alterar USUARIOS; se lee de la
--    primera fila del usuario.
CREATE TABLE IF NOT EXISTS COORDINADOR_CARRERAS (
    id                  SERIAL PRIMARY KEY,
    usuario_id          INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    carrera             VARCHAR(200) NOT NULL,
    coordinacion_tipo   VARCHAR(20) NOT NULL DEFAULT 'AMBOS'
                        CHECK (coordinacion_tipo IN ('PRACTICAS', 'VINCULACION', 'AMBOS')),
    UNIQUE(usuario_id, carrera)
);

-- ============================================================
-- VERIFICACIÓN (debe devolver las 2 tablas con 0 filas)
-- ============================================================
SELECT 'DOCS_ESTUDIANTE' AS tabla, COUNT(*) AS filas FROM DOCS_ESTUDIANTE
UNION ALL
SELECT 'COORDINADOR_CARRERAS', COUNT(*) FROM COORDINADOR_CARRERAS;
