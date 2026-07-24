-- ============================================================
-- FASE 42 — Ciclo de vida de vacantes/proyectos y cierre de periodos
-- Script idempotente: se puede ejecutar dos veces sin error.
-- Pegar completo en el SQL Editor de Supabase.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Liberación de cupos retirados en VINCULACION
--    Un retiro conserva el cupo por defecto; el coordinador puede
--    liberarlo UNA sola vez, con justificación, para un reemplazo.
-- ------------------------------------------------------------
ALTER TABLE VINCULACION ADD COLUMN IF NOT EXISTS cupo_liberado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE VINCULACION ADD COLUMN IF NOT EXISTS cupo_liberado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL;
ALTER TABLE VINCULACION ADD COLUMN IF NOT EXISTS cupo_liberado_en TIMESTAMP;
ALTER TABLE VINCULACION ADD COLUMN IF NOT EXISTS motivo_liberacion_cupo TEXT;

-- Solo una vinculación RETIRADA puede tener el cupo liberado, siempre
-- con motivo (>= 10 caracteres), actor y fecha registrados.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_vinculacion_liberacion_cupo'
          AND conrelid = 'vinculacion'::regclass
    ) THEN
        ALTER TABLE VINCULACION ADD CONSTRAINT chk_vinculacion_liberacion_cupo
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

-- ------------------------------------------------------------
-- 2. Pausa y reactivación de vacantes de prácticas
--    activa = FALSE oculta la vacante a estudiantes y bloquea
--    postulaciones/consolidaciones sin devolver los cupos a la empresa.
-- ------------------------------------------------------------
ALTER TABLE VACANTES_PRACTICAS ADD COLUMN IF NOT EXISTS activa BOOLEAN NOT NULL DEFAULT TRUE;

-- ------------------------------------------------------------
-- 3. Estado 'Expirada' en las postulaciones de ambos procesos
--    Al cerrar un periodo, las postulaciones pendientes expiran.
--    Se reemplaza el CHECK de estado por uno con nombre estable.
-- ------------------------------------------------------------
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'postulaciones_vinculacion'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%estado%'
    LOOP
        EXECUTE format('ALTER TABLE POSTULACIONES_VINCULACION DROP CONSTRAINT %I', c.conname);
    END LOOP;
    ALTER TABLE POSTULACIONES_VINCULACION ADD CONSTRAINT chk_postulacion_vinculacion_estado
        CHECK (estado IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo', 'Expirada'));
END $$;

DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'postulaciones_meritocraticas'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%estado%'
    LOOP
        EXECUTE format('ALTER TABLE POSTULACIONES_MERITOCRATICAS DROP CONSTRAINT %I', c.conname);
    END LOOP;
    ALTER TABLE POSTULACIONES_MERITOCRATICAS ADD CONSTRAINT chk_postulacion_merito_estado
        CHECK (estado IN ('Pendiente', 'Procesado', 'Aprobado', 'Rechazado', 'Expirada'));
END $$;

-- ============================================================
-- CONSULTAS DE VALIDACIÓN (todas deben devolver 0 filas inválidas)
-- ============================================================

-- 4.1 Vinculaciones con liberación inconsistente (debe ser 0)
SELECT COUNT(*) AS vinculaciones_liberacion_invalida
FROM VINCULACION
WHERE cupo_liberado = TRUE
  AND (
      estado <> 'retirado'
      OR motivo_liberacion_cupo IS NULL
      OR CHAR_LENGTH(BTRIM(motivo_liberacion_cupo)) < 10
      OR cupo_liberado_por IS NULL
      OR cupo_liberado_en IS NULL
  );

-- 4.2 Vacantes sin bandera de activación (debe ser 0)
SELECT COUNT(*) AS vacantes_sin_bandera_activa
FROM VACANTES_PRACTICAS
WHERE activa IS NULL;

-- 4.3 Postulaciones con estados fuera del catálogo (debe ser 0)
SELECT COUNT(*) AS postulaciones_vinculacion_estado_invalido
FROM POSTULACIONES_VINCULACION
WHERE estado NOT IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo', 'Expirada');

SELECT COUNT(*) AS postulaciones_merito_estado_invalido
FROM POSTULACIONES_MERITOCRATICAS
WHERE estado NOT IN ('Pendiente', 'Procesado', 'Aprobado', 'Rechazado', 'Expirada');

-- 4.4 Resumen informativo de periodos y su estado
SELECT codigo, estado, fecha_inicio, fecha_fin
FROM PERIODOS_ACADEMICOS
ORDER BY fecha_inicio DESC;
