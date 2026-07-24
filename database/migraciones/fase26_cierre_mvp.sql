-- FASE 26: cierre de integridad de bitacoras para el MVP.
-- Idempotente y listo para ejecutar en Supabase SQL Editor.
--
-- El backfill a parcial 1 replica la decision historica de la fase 6 para
-- registros antiguos. Antes de imponer NOT NULL se rechaza cualquier valor
-- distinto de 1, 2 o 3.

BEGIN;

UPDATE BITACORAS
SET parcial = 1
WHERE parcial IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM BITACORAS
        WHERE parcial NOT IN (1, 2, 3)
    ) THEN
        RAISE EXCEPTION
            'Existen bitacoras con parcial fuera del rango 1..3. Corrige esos datos antes de aplicar NOT NULL.';
    END IF;
END $$;

ALTER TABLE BITACORAS
ALTER COLUMN parcial SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_bitacoras_parcial'
          AND conrelid = 'bitacoras'::regclass
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_parcial
        CHECK (parcial IN (1, 2, 3)) NOT VALID;
    END IF;
END $$;

ALTER TABLE BITACORAS
VALIDATE CONSTRAINT chk_bitacoras_parcial;

COMMIT;

-- Validacion 1: is_nullable debe ser NO.
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'bitacoras'
  AND column_name = 'parcial';

-- Validacion 2: debe devolver 0.
SELECT COUNT(*) AS bitacoras_invalidas
FROM BITACORAS
WHERE parcial IS NULL
   OR parcial NOT IN (1, 2, 3);
