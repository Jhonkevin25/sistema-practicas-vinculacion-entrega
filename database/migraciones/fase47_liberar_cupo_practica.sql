-- ============================================================
-- FASE 47 — Liberación del cupo de empresa de una práctica retirada
-- Espejo de la fase 42 (liberación de cupos en VINCULACION).
-- Script idempotente: se puede ejecutar dos veces sin error.
-- Pegar completo en el SQL Editor de Supabase.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Liberación de cupos retirados en PRACTICAS
--    Un retiro conserva el cupo de la empresa por defecto (los cupos
--    ocupados se derivan contando las prácticas del periodo); gestión
--    académica puede liberarlo UNA sola vez, con justificación, para
--    asignar un reemplazo dentro del mismo periodo. Una práctica con
--    cupo_liberado = TRUE deja de contar como cupo ocupado.
-- ------------------------------------------------------------
ALTER TABLE PRACTICAS ADD COLUMN IF NOT EXISTS cupo_liberado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE PRACTICAS ADD COLUMN IF NOT EXISTS cupo_liberado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL;
ALTER TABLE PRACTICAS ADD COLUMN IF NOT EXISTS cupo_liberado_en TIMESTAMP;
ALTER TABLE PRACTICAS ADD COLUMN IF NOT EXISTS motivo_liberacion_cupo TEXT;

-- Solo una práctica RETIRADA puede tener el cupo liberado, siempre
-- con motivo (>= 10 caracteres), actor y fecha registrados.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_practica_liberacion_cupo'
          AND conrelid = 'practicas'::regclass
    ) THEN
        ALTER TABLE PRACTICAS ADD CONSTRAINT chk_practica_liberacion_cupo
            CHECK (
                cupo_liberado = FALSE
                OR (
                    estado = 'retirado'
                    AND motivo_liberacion_cupo IS NOT NULL
                    AND CHAR_LENGTH(BTRIM(motivo_liberacion_cupo)) >= 10
                    AND cupo_liberado_por IS NOT NULL
                    AND cupo_liberado_en IS NOT NULL
                )
            );
    END IF;
END $$;

-- ============================================================
-- CONSULTAS DE VALIDACIÓN
-- ============================================================

-- 2.1 Prácticas con liberación inconsistente (debe ser 0)
SELECT COUNT(*) AS practicas_liberacion_invalida
FROM PRACTICAS
WHERE cupo_liberado = TRUE
  AND (
      estado <> 'retirado'
      OR motivo_liberacion_cupo IS NULL
      OR CHAR_LENGTH(BTRIM(motivo_liberacion_cupo)) < 10
      OR cupo_liberado_por IS NULL
      OR cupo_liberado_en IS NULL
  );

-- 2.2 Columnas nuevas presentes (debe devolver 4 filas)
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'practicas'
  AND column_name IN ('cupo_liberado', 'cupo_liberado_por', 'cupo_liberado_en', 'motivo_liberacion_cupo')
ORDER BY column_name;
