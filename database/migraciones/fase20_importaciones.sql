-- Fase 20: importacion institucional CSV preparada para futura API.
-- Script idempotente para ejecutar en el SQL Editor de Supabase.

BEGIN;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'notas_academicas'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%fuente%'
    LOOP
        EXECUTE format('ALTER TABLE NOTAS_ACADEMICAS DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

-- Unificar el vocabulario historico después de retirar el CHECK antiguo.
UPDATE NOTAS_ACADEMICAS
SET fuente = 'CSV_UNIVERSIDAD'
WHERE fuente = 'CSV';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_notas_academicas_fuente'
    ) THEN
        ALTER TABLE NOTAS_ACADEMICAS
        ADD CONSTRAINT chk_notas_academicas_fuente
        CHECK (fuente IN ('MANUAL', 'CSV_UNIVERSIDAD', 'API_UNIVERSIDAD'));
    END IF;
END $$;

ALTER TABLE POSTULACIONES_MERITOCRATICAS
ADD COLUMN IF NOT EXISTS asignado_vacante_id INT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_postulacion_asignado_vacante'
    ) THEN
        ALTER TABLE POSTULACIONES_MERITOCRATICAS
        ADD CONSTRAINT fk_postulacion_asignado_vacante
        FOREIGN KEY (asignado_vacante_id)
        REFERENCES VACANTES_PRACTICAS(id)
        ON DELETE SET NULL;
    END IF;
END $$;

-- Backfill seguro: solo cuando una única preferencia pertenece a la empresa asignada.
WITH candidatas AS (
    SELECT p.id AS postulacion_id,
           MIN(v.id) AS vacante_id,
           COUNT(*) AS cantidad
    FROM POSTULACIONES_MERITOCRATICAS p
    JOIN VACANTES_PRACTICAS v
      ON v.id IN (p.pref1_id, p.pref2_id, p.pref3_id)
     AND v.empresa_id = p.asignado_empresa_id
    WHERE p.asignado_vacante_id IS NULL
      AND p.asignado_empresa_id IS NOT NULL
    GROUP BY p.id
)
UPDATE POSTULACIONES_MERITOCRATICAS p
SET asignado_vacante_id = c.vacante_id
FROM candidatas c
WHERE p.id = c.postulacion_id
  AND c.cantidad = 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT LOWER(external_id)
        FROM USUARIOS
        WHERE external_id IS NOT NULL AND BTRIM(external_id) <> ''
        GROUP BY LOWER(external_id)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen usuarios con external_id repetido. Corrige esos datos antes de aplicar la restriccion.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuarios_external_id
    ON USUARIOS(LOWER(external_id))
    WHERE external_id IS NOT NULL AND BTRIM(external_id) <> '';

CREATE TABLE IF NOT EXISTS IMPORTACIONES (
    id              BIGSERIAL PRIMARY KEY,
    archivo_nombre  VARCHAR(255) NOT NULL,
    tipo            VARCHAR(20) NOT NULL
                    CONSTRAINT chk_importaciones_tipo CHECK (tipo IN ('ESTUDIANTES', 'NOTAS')),
    filas_total     INT NOT NULL DEFAULT 0 CHECK (filas_total >= 0),
    filas_ok        INT NOT NULL DEFAULT 0 CHECK (filas_ok >= 0),
    filas_error     INT NOT NULL DEFAULT 0 CHECK (filas_error >= 0),
    creados         INT NOT NULL DEFAULT 0 CHECK (creados >= 0),
    actualizados    INT NOT NULL DEFAULT 0 CHECK (actualizados >= 0),
    enlazados       INT NOT NULL DEFAULT 0 CHECK (enlazados >= 0),
    estado          VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA'
                    CONSTRAINT chk_importaciones_estado CHECK (estado IN ('COMPLETADA', 'CON_ERRORES', 'FALLIDA')),
    detalle_errores JSONB NOT NULL DEFAULT '[]'::jsonb,
    usuario_id      INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    fecha           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (filas_ok + filas_error = filas_total)
);

CREATE INDEX IF NOT EXISTS idx_importaciones_fecha
    ON IMPORTACIONES(fecha DESC);

CREATE INDEX IF NOT EXISTS idx_importaciones_tipo
    ON IMPORTACIONES(tipo, fecha DESC);

CREATE INDEX IF NOT EXISTS idx_postulaciones_asignado_vacante
    ON POSTULACIONES_MERITOCRATICAS(asignado_vacante_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_proc WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_importaciones
            AFTER INSERT OR UPDATE OR DELETE ON IMPORTACIONES
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: las dos primeras consultas deben devolver 0 filas.
SELECT id, fuente
FROM NOTAS_ACADEMICAS
WHERE fuente NOT IN ('MANUAL', 'CSV_UNIVERSIDAD', 'API_UNIVERSIDAD');

SELECT LOWER(external_id) AS external_id, COUNT(*)
FROM USUARIOS
WHERE external_id IS NOT NULL AND BTRIM(external_id) <> ''
GROUP BY LOWER(external_id)
HAVING COUNT(*) > 1;

-- Filas antiguas que requieren seleccionar manualmente la vacante exacta.
SELECT id, estudiante_id, asignado_empresa_id
FROM POSTULACIONES_MERITOCRATICAS
WHERE asignado_empresa_id IS NOT NULL
  AND asignado_vacante_id IS NULL;
