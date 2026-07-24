-- Fase 6: bitacoras reales asociadas a practica o vinculacion
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - cada bitacora pertenece a una practica o vinculacion concreta
-- - cada bitacora tiene parcial academico
-- - el tutor revisa con estados controlados
-- - las horas aprobadas se calculan desde bitacoras aprobadas en backend

BEGIN;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS practica_id INT REFERENCES PRACTICAS(id) ON DELETE CASCADE;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS vinculacion_id INT REFERENCES VINCULACION(id) ON DELETE CASCADE;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS parcial INT;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS observaciones_tutor TEXT;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS revisado_por INT REFERENCES USUARIOS(id);

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS fecha_revision TIMESTAMP;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE BITACORAS
DROP CONSTRAINT IF EXISTS bitacoras_estado_check;

ALTER TABLE BITACORAS
DROP CONSTRAINT IF EXISTS chk_bitacoras_estado;

-- Backfill para datos existentes: asociar bitacoras antiguas al proceso activo
-- o mas reciente del estudiante. Si hay practica y vinculacion simultaneas,
-- se prioriza practica porque el modulo actual de UI es practicas.
UPDATE BITACORAS b
SET practica_id = p.id
FROM PRACTICAS p
WHERE b.practica_id IS NULL
  AND b.vinculacion_id IS NULL
  AND p.estudiante_id = b.estudiante_id
  AND p.id = (
      SELECT p2.id
      FROM PRACTICAS p2
      WHERE p2.estudiante_id = b.estudiante_id
      ORDER BY
          CASE WHEN p2.estado IN ('en_curso', 'pendiente') THEN 0 ELSE 1 END,
          p2.id DESC
      LIMIT 1
  );

UPDATE BITACORAS b
SET vinculacion_id = v.id
FROM VINCULACION v
WHERE b.practica_id IS NULL
  AND b.vinculacion_id IS NULL
  AND v.estudiante_id = b.estudiante_id
  AND v.id = (
      SELECT v2.id
      FROM VINCULACION v2
      WHERE v2.estudiante_id = b.estudiante_id
      ORDER BY
          CASE WHEN v2.estado IN ('en_curso', 'pendiente') THEN 0 ELSE 1 END,
          v2.id DESC
      LIMIT 1
  );

UPDATE BITACORAS
SET parcial = 1
WHERE parcial IS NULL;

ALTER TABLE BITACORAS
ALTER COLUMN parcial SET NOT NULL;

UPDATE BITACORAS
SET estado = 'aprobada'
WHERE estado = 'aprobado';

UPDATE BITACORAS
SET estado = 'rechazada'
WHERE estado = 'rechazado';

UPDATE BITACORAS
SET estado = 'requiere_correccion'
WHERE estado = 'correccion';

CREATE INDEX IF NOT EXISTS idx_bitacoras_practica
ON BITACORAS(practica_id);

CREATE INDEX IF NOT EXISTS idx_bitacoras_vinculacion
ON BITACORAS(vinculacion_id);

CREATE INDEX IF NOT EXISTS idx_bitacoras_estado
ON BITACORAS(estado);

CREATE INDEX IF NOT EXISTS idx_bitacoras_parcial
ON BITACORAS(parcial);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM BITACORAS
        WHERE (practica_id IS NULL AND vinculacion_id IS NULL)
           OR (practica_id IS NOT NULL AND vinculacion_id IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'Existen bitacoras sin expediente o con doble expediente. Corrige esos datos antes de aplicar constraints.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_expediente_unico'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_expediente_unico
        CHECK (
            (practica_id IS NOT NULL AND vinculacion_id IS NULL)
            OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_parcial'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_parcial
        CHECK (parcial IN (1, 2, 3));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_horas'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_horas
        CHECK (horas BETWEEN 1 AND 24);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_estado'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_estado
        CHECK (estado IN ('pendiente', 'aprobada', 'rechazada', 'requiere_correccion'));
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, estudiante_id, practica_id, vinculacion_id, parcial, estado
FROM BITACORAS
WHERE (practica_id IS NULL AND vinculacion_id IS NULL)
   OR (practica_id IS NOT NULL AND vinculacion_id IS NOT NULL)
   OR parcial NOT IN (1, 2, 3)
   OR horas NOT BETWEEN 1 AND 24
   OR estado NOT IN ('pendiente', 'aprobada', 'rechazada', 'requiere_correccion');
