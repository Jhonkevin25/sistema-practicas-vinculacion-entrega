-- Fase 4: limpieza idempotente de postulaciones meritocraticas de prueba
-- Ejecutar en Supabase antes de database/fase4_meritocracia_backend.sql
--
-- Motivo:
-- La nueva regla exige minimo dos preferencias y que no esten repetidas:
-- pref1_id IS NOT NULL, pref2_id IS NOT NULL, pref1_id <> pref2_id,
-- y pref3_id distinto de pref1_id/pref2_id cuando exista.
--
-- Este script respalda y elimina las filas invalidas existentes. Usarlo solo
-- para datos de prueba o datos historicos que no deban conservarse.

BEGIN;

CREATE TABLE IF NOT EXISTS POSTULACIONES_MERITOCRATICAS_LIMPIEZA_FASE4 AS
SELECT
    p.*,
    CURRENT_TIMESTAMP::TIMESTAMP AS respaldado_en,
    ''::TEXT AS motivo_limpieza
FROM POSTULACIONES_MERITOCRATICAS p
WITH NO DATA;

WITH invalidas AS (
    SELECT p.*
    FROM POSTULACIONES_MERITOCRATICAS p
    WHERE p.pref1_id IS NULL
       OR p.pref2_id IS NULL
       OR p.pref1_id = p.pref2_id
       OR p.pref3_id = p.pref1_id
       OR p.pref3_id = p.pref2_id
),
motivos AS (
    SELECT
        p.id,
        CONCAT_WS(
            '; ',
            CASE WHEN p.pref1_id IS NULL THEN 'pref1_id nulo' END,
            CASE WHEN p.pref2_id IS NULL THEN 'pref2_id nulo' END,
            CASE WHEN p.pref1_id IS NOT NULL AND p.pref1_id = p.pref2_id THEN 'pref1_id repetido con pref2_id' END,
            CASE WHEN p.pref3_id IS NOT NULL AND p.pref3_id = p.pref1_id THEN 'pref3_id repetido con pref1_id' END,
            CASE WHEN p.pref3_id IS NOT NULL AND p.pref3_id = p.pref2_id THEN 'pref3_id repetido con pref2_id' END
        ) AS motivo_limpieza
    FROM POSTULACIONES_MERITOCRATICAS p
    WHERE p.pref1_id IS NULL
       OR p.pref2_id IS NULL
       OR p.pref1_id = p.pref2_id
       OR p.pref3_id = p.pref1_id
       OR p.pref3_id = p.pref2_id
)
INSERT INTO POSTULACIONES_MERITOCRATICAS_LIMPIEZA_FASE4
SELECT
    invalidas.*,
    CURRENT_TIMESTAMP,
    motivos.motivo_limpieza
FROM invalidas
JOIN motivos ON motivos.id = invalidas.id
WHERE NOT EXISTS (
    SELECT 1
    FROM POSTULACIONES_MERITOCRATICAS_LIMPIEZA_FASE4 respaldo
    WHERE respaldo.id = invalidas.id
);

DELETE FROM POSTULACIONES_MERITOCRATICAS p
WHERE p.pref1_id IS NULL
   OR p.pref2_id IS NULL
   OR p.pref1_id = p.pref2_id
   OR p.pref3_id = p.pref1_id
   OR p.pref3_id = p.pref2_id;

-- Debe devolver 0.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM POSTULACIONES_MERITOCRATICAS
        WHERE pref1_id IS NULL
           OR pref2_id IS NULL
           OR pref1_id = pref2_id
           OR pref3_id = pref1_id
           OR pref3_id = pref2_id
    ) THEN
        RAISE EXCEPTION 'Aun existen postulaciones meritocraticas invalidas para la constraint de preferencias.';
    END IF;
END $$;

COMMIT;

-- Reporte de control: filas respaldadas por esta limpieza.
SELECT id, estudiante_id, pref1_id, pref2_id, pref3_id, estado, motivo_limpieza, respaldado_en
FROM POSTULACIONES_MERITOCRATICAS_LIMPIEZA_FASE4
ORDER BY respaldado_en DESC, id;

-- Validacion final: debe devolver 0 filas.
SELECT id, estudiante_id, pref1_id, pref2_id, pref3_id, estado
FROM POSTULACIONES_MERITOCRATICAS
WHERE pref1_id IS NULL
   OR pref2_id IS NULL
   OR pref1_id = pref2_id
   OR pref3_id = pref1_id
   OR pref3_id = pref2_id;
