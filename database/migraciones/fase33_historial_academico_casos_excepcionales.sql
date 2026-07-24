-- ============================================================
-- FASE 33 - HISTORIAL ACADEMICO Y CASOS EXCEPCIONALES
-- ============================================================
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
--
-- ADVERTENCIA: reemplaza exclusivamente los CHECK heredados de estado
-- para admitir REPROBADO/RETIRADO; no elimina filas ni historial.
--
-- La linea de tiempo se deriva de PRACTICAS/VINCULACION, sus periodos,
-- cierre_snapshot y AUDITORIA. No se crea una tabla de eventos adicional.

BEGIN;

ALTER TABLE PRACTICAS
    ADD COLUMN IF NOT EXISTS motivo_finalizacion TEXT;

ALTER TABLE VINCULACION
    ADD COLUMN IF NOT EXISTS motivo_finalizacion TEXT;

-- Los CHECK originales fueron declarados sin nombre explícito por el
-- schema base, por lo que PostgreSQL los nombra automaticamente así.
ALTER TABLE PRACTICAS
    DROP CONSTRAINT IF EXISTS practicas_estado_check,
    DROP CONSTRAINT IF EXISTS chk_practicas_estado;

ALTER TABLE VINCULACION
    DROP CONSTRAINT IF EXISTS vinculacion_estado_check,
    DROP CONSTRAINT IF EXISTS chk_vinculacion_estado;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'PRACTICAS'::regclass
          AND conname = 'chk_practicas_estado'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT chk_practicas_estado
            CHECK (estado IN ('pendiente', 'en_curso', 'completado', 'reprobado', 'retirado'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'VINCULACION'::regclass
          AND conname = 'chk_vinculacion_estado'
    ) THEN
        ALTER TABLE VINCULACION
            ADD CONSTRAINT chk_vinculacion_estado
            CHECK (estado IN ('pendiente', 'en_curso', 'completado', 'reprobado', 'retirado'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'PRACTICAS'::regclass
          AND conname = 'chk_practicas_finalizacion_excepcional'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT chk_practicas_finalizacion_excepcional
            CHECK (
                estado NOT IN ('reprobado', 'retirado')
                OR (
                    motivo_finalizacion IS NOT NULL
                    AND CHAR_LENGTH(BTRIM(motivo_finalizacion)) >= 10
                    AND cerrado_en IS NOT NULL
                    AND cerrado_por IS NOT NULL
                    AND cierre_snapshot IS NOT NULL
                )
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'VINCULACION'::regclass
          AND conname = 'chk_vinculacion_finalizacion_excepcional'
    ) THEN
        ALTER TABLE VINCULACION
            ADD CONSTRAINT chk_vinculacion_finalizacion_excepcional
            CHECK (
                estado NOT IN ('reprobado', 'retirado')
                OR (
                    motivo_finalizacion IS NOT NULL
                    AND CHAR_LENGTH(BTRIM(motivo_finalizacion)) >= 10
                    AND cerrado_en IS NOT NULL
                    AND cerrado_por IS NOT NULL
                    AND cierre_snapshot IS NOT NULL
                )
            );
    END IF;
END $$;

-- Cierra la carrera entre dos solicitudes simultaneas del mismo proceso.
-- La exclusividad cruzada PRACTICAS/VINCULACION se valida en backend.
DO $$
BEGIN
    IF EXISTS (
        SELECT estudiante_id
        FROM PRACTICAS
        WHERE estado IN ('pendiente', 'en_curso')
        GROUP BY estudiante_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen estudiantes con mas de una practica activa. Corrige esos datos antes de aplicar la Fase 33.';
    END IF;

    IF EXISTS (
        SELECT estudiante_id
        FROM VINCULACION
        WHERE estado IN ('pendiente', 'en_curso')
        GROUP BY estudiante_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen estudiantes con mas de una vinculacion activa. Corrige esos datos antes de aplicar la Fase 33.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_practicas_estudiante_activa
    ON PRACTICAS(estudiante_id)
    WHERE estado IN ('pendiente', 'en_curso');

CREATE UNIQUE INDEX IF NOT EXISTS ux_vinculacion_estudiante_activa
    ON VINCULACION(estudiante_id)
    WHERE estado IN ('pendiente', 'en_curso');

CREATE INDEX IF NOT EXISTS idx_practicas_estudiante_periodo
    ON PRACTICAS(estudiante_id, periodo_academico);

CREATE INDEX IF NOT EXISTS idx_vinculacion_estudiante_periodo
    ON VINCULACION(estudiante_id, periodo_academico);

COMMENT ON COLUMN PRACTICAS.motivo_finalizacion IS
    'Motivo obligatorio cuando el expediente termina como reprobado o retirado.';

COMMENT ON COLUMN VINCULACION.motivo_finalizacion IS
    'Motivo obligatorio cuando el expediente termina como reprobado o retirado.';

COMMIT;

-- Resultado esperado: cuatro constraints con validada = true.
SELECT conrelid::regclass AS tabla,
       conname AS nombre_constraint,
       convalidated AS validada
FROM pg_constraint
WHERE conname IN (
    'chk_practicas_estado',
    'chk_practicas_finalizacion_excepcional',
    'chk_vinculacion_estado',
    'chk_vinculacion_finalizacion_excepcional'
)
ORDER BY 1, 2;

-- Resultado esperado: cero filas.
SELECT 'PRACTICAS' AS tabla, estudiante_id, COUNT(*) AS procesos_activos
FROM PRACTICAS
WHERE estado IN ('pendiente', 'en_curso')
GROUP BY estudiante_id
HAVING COUNT(*) > 1
UNION ALL
SELECT 'VINCULACION' AS tabla, estudiante_id, COUNT(*) AS procesos_activos
FROM VINCULACION
WHERE estado IN ('pendiente', 'en_curso')
GROUP BY estudiante_id
HAVING COUNT(*) > 1;
