-- ============================================================
-- FASE 48 - Un expediente de estudiante por cuenta de usuario
-- Script idempotente para ejecutar en el SQL Editor de Supabase.
-- No elimina ni modifica datos existentes.
-- ============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM ESTUDIANTES
        GROUP BY usuario_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existen cuentas de usuario asociadas a mas de un expediente de estudiante. Corrige los duplicados antes de aplicar la constraint.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'estudiantes'::regclass
          AND c.contype = 'u'
          AND (
              SELECT ARRAY_AGG(a.attname::TEXT ORDER BY u.ordinality)
              FROM UNNEST(c.conkey) WITH ORDINALITY AS u(attnum, ordinality)
              JOIN pg_attribute a
                ON a.attrelid = c.conrelid
               AND a.attnum = u.attnum
          ) = ARRAY['usuario_id']::TEXT[]
    ) THEN
        ALTER TABLE ESTUDIANTES
            ADD CONSTRAINT estudiantes_usuario_id_uk UNIQUE (usuario_id);
    END IF;
END $$;

-- Debe devolver cero filas.
SELECT usuario_id, COUNT(*) AS expedientes
FROM ESTUDIANTES
GROUP BY usuario_id
HAVING COUNT(*) > 1;

-- Debe devolver una fila para usuario_id.
SELECT c.conname AS constraint_name,
       pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c
WHERE c.conrelid = 'estudiantes'::regclass
  AND c.contype = 'u'
  AND pg_get_constraintdef(c.oid) ILIKE '%(usuario_id)%';
