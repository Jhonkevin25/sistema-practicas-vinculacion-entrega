-- FASE 15 - Compatibilidad de DOCS_ESTUDIANTE con Storage y procesos
-- Objetivo:
-- - eliminar restricciones antiguas de fase 5 que bloquean documentos por
--   proceso (PRACTICAS/VINCULACION/GENERAL) y tipos posteriores como
--   carta_aceptacion, informe_final o certificado.
-- - conservar la unicidad correcta: estudiante + tipo_documento + proceso.
--
-- Idempotente. Ejecutar en Supabase SQL Editor antes de probar carga real.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'docs_estudiante_estudiante_id_tipo_documento_key'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        DROP CONSTRAINT docs_estudiante_estudiante_id_tipo_documento_key;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'docs_estudiante_estudiante_id_tipo_documento_key'
    ) THEN
        DROP INDEX docs_estudiante_estudiante_id_tipo_documento_key;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'docs_estudiante_tipo_documento_check'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        DROP CONSTRAINT docs_estudiante_tipo_documento_check;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_docs_estudiante_tipo_proceso
ON DOCS_ESTUDIANTE(estudiante_id, tipo_documento, proceso);

CREATE INDEX IF NOT EXISTS idx_docs_estudiante_estado
ON DOCS_ESTUDIANTE(estado);

-- Verificacion esperada:
-- 1) No debe existir la restriccion antigua por estudiante/tipo.
-- 2) Debe existir el indice unico estudiante/tipo/proceso.
SELECT
    'docs_estudiante_estudiante_id_tipo_documento_key' AS objeto,
    EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'docs_estudiante_estudiante_id_tipo_documento_key'
    ) AS existe;

SELECT
    'uq_docs_estudiante_tipo_proceso' AS objeto,
    EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uq_docs_estudiante_tipo_proceso'
    ) AS existe;
