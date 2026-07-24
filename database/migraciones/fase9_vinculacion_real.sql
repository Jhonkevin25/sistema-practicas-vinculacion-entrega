-- Fase 9: vinculacion real con postulacion persistente
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - guardar postulaciones de estudiantes a proyectos de vinculacion
-- - evitar postulaciones activas duplicadas por estudiante
-- - permitir que coordinacion apruebe y cree el expediente oficial
-- - enlazar la postulacion aprobada con la vinculacion creada

BEGIN;

ALTER TABLE VINCULACION
ADD COLUMN IF NOT EXISTS periodo_academico VARCHAR(20);

CREATE TABLE IF NOT EXISTS POSTULACIONES_VINCULACION (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    proyecto_id         INT NOT NULL REFERENCES PROYECTOS(id) ON DELETE RESTRICT,
    estado              VARCHAR(30) NOT NULL DEFAULT 'Pendiente',
    periodo_academico   VARCHAR(20),
    vinculacion_id      INT REFERENCES VINCULACION(id) ON DELETE SET NULL,
    observacion         TEXT,
    aprobado_por        INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    aprobado_en         TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_postulaciones_vinculacion_estado'
    ) THEN
        ALTER TABLE POSTULACIONES_VINCULACION
        ADD CONSTRAINT chk_postulaciones_vinculacion_estado
        CHECK (estado IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_postulacion_vinculacion_activa_estudiante
ON POSTULACIONES_VINCULACION(estudiante_id)
WHERE estado = 'Pendiente';

CREATE UNIQUE INDEX IF NOT EXISTS uq_postulacion_vinculacion_vinculacion
ON POSTULACIONES_VINCULACION(vinculacion_id)
WHERE vinculacion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_postulaciones_vinculacion_proyecto
ON POSTULACIONES_VINCULACION(proyecto_id);

CREATE INDEX IF NOT EXISTS idx_postulaciones_vinculacion_estado
ON POSTULACIONES_VINCULACION(estado);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc
        WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_postulaciones_vinculacion
            AFTER INSERT OR UPDATE OR DELETE ON POSTULACIONES_VINCULACION
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, estudiante_id, proyecto_id, estado
FROM POSTULACIONES_VINCULACION
WHERE estado NOT IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo');
