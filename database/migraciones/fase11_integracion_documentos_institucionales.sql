-- Fase 11: integracion institucional y documentos configurables
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - preparar usuarios para sincronizacion futura con la base institucional
-- - ampliar documentos del estudiante con proceso, estado y revision
-- - permitir requisitos documentales estandar por proceso/carrera
-- - incluir carta de aceptacion como requisito opcional/configurable

BEGIN;

ALTER TABLE USUARIOS
ADD COLUMN IF NOT EXISTS fuente VARCHAR(30) DEFAULT 'MANUAL',
ADD COLUMN IF NOT EXISTS external_id VARCHAR(120);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_usuarios_fuente'
    ) THEN
        ALTER TABLE USUARIOS
        ADD CONSTRAINT chk_usuarios_fuente
        CHECK (fuente IN ('MANUAL', 'CSV_UNIVERSIDAD', 'API_UNIVERSIDAD'));
    END IF;
END $$;

ALTER TABLE DOCS_ESTUDIANTE
ADD COLUMN IF NOT EXISTS proceso VARCHAR(30) DEFAULT 'GENERAL',
ADD COLUMN IF NOT EXISTS carrera VARCHAR(150),
ADD COLUMN IF NOT EXISTS etapa VARCHAR(80),
ADD COLUMN IF NOT EXISTS estado VARCHAR(30) DEFAULT 'cargado',
ADD COLUMN IF NOT EXISTS observacion TEXT,
ADD COLUMN IF NOT EXISTS revisado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS fecha_revision TIMESTAMP,
ADD COLUMN IF NOT EXISTS requerido_id INT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_docs_estudiante_estado'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        ADD CONSTRAINT chk_docs_estudiante_estado
        CHECK (estado IN ('pendiente', 'cargado', 'en_revision', 'aprobado', 'rechazado', 'requiere_correccion'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_docs_estudiante_proceso'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        ADD CONSTRAINT chk_docs_estudiante_proceso
        CHECK (proceso IN ('GENERAL', 'PRACTICAS', 'VINCULACION'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS DOCUMENTOS_REQUERIDOS (
    id                  SERIAL PRIMARY KEY,
    proceso             VARCHAR(30) NOT NULL CHECK (proceso IN ('GENERAL', 'PRACTICAS', 'VINCULACION')),
    carrera             VARCHAR(150),
    etapa               VARCHAR(80),
    tipo_documento      VARCHAR(80) NOT NULL,
    nombre              VARCHAR(180) NOT NULL,
    descripcion         TEXT,
    obligatorio         BOOLEAN NOT NULL DEFAULT TRUE,
    momento             VARCHAR(40) NOT NULL DEFAULT 'PRE_POSTULACION'
                        CHECK (momento IN ('PRE_POSTULACION', 'POST_ACEPTACION', 'CIERRE')),
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    solicitado_por      INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_docs_estudiante_requerido'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        ADD CONSTRAINT fk_docs_estudiante_requerido
        FOREIGN KEY (requerido_id) REFERENCES DOCUMENTOS_REQUERIDOS(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_documentos_requeridos_definicion
ON DOCUMENTOS_REQUERIDOS(
    proceso,
    COALESCE(carrera, ''),
    COALESCE(etapa, ''),
    tipo_documento,
    momento
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_docs_estudiante_tipo_proceso
ON DOCS_ESTUDIANTE(estudiante_id, tipo_documento, proceso);

CREATE INDEX IF NOT EXISTS idx_docs_estudiante_estado
ON DOCS_ESTUDIANTE(estado);

CREATE INDEX IF NOT EXISTS idx_documentos_requeridos_proceso
ON DOCUMENTOS_REQUERIDOS(proceso, activo);

INSERT INTO DOCUMENTOS_REQUERIDOS
    (proceso, carrera, etapa, tipo_documento, nombre, descripcion, obligatorio, momento)
VALUES
    ('GENERAL', NULL, NULL, 'cv', 'Hoja de vida', 'Documento base del expediente estudiantil.', TRUE, 'PRE_POSTULACION'),
    ('GENERAL', NULL, NULL, 'cedula', 'Copia de cédula', 'Identificación oficial del estudiante.', TRUE, 'PRE_POSTULACION'),
    ('PRACTICAS', NULL, NULL, 'carta', 'Carta de solicitud de prácticas', 'Solicitud inicial para participar en convocatorias de prácticas.', TRUE, 'PRE_POSTULACION'),
    ('VINCULACION', NULL, NULL, 'carta', 'Carta de solicitud de vinculación', 'Solicitud inicial para participar en proyectos de vinculación.', TRUE, 'PRE_POSTULACION'),
    ('PRACTICAS', NULL, NULL, 'carta_aceptacion', 'Carta de aceptación del cupo', 'Documento posterior a la aceptación del cupo; puede marcarse obligatorio por carrera si aplica.', FALSE, 'POST_ACEPTACION'),
    ('VINCULACION', NULL, NULL, 'carta_aceptacion', 'Carta de aceptación del proyecto', 'Documento posterior a la aceptación del proyecto de vinculación; puede marcarse obligatorio por carrera si aplica.', FALSE, 'POST_ACEPTACION')
ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc
        WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_documentos_requeridos
            AFTER INSERT OR UPDATE OR DELETE ON DOCUMENTOS_REQUERIDOS
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver los documentos estandar activos.
SELECT proceso, carrera, tipo_documento, nombre, obligatorio, momento
FROM DOCUMENTOS_REQUERIDOS
WHERE activo = TRUE
ORDER BY proceso, momento, tipo_documento;
