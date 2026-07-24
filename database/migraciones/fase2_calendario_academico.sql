-- Fase 2: calendario academico y validaciones de orden logico
-- Ejecutar manualmente en el SQL Editor de Supabase.
-- Script idempotente: puede ejecutarse mas de una vez.

-- Fechas de convocatoria por periodo/tipo: el orden minimo debe ser
-- convocatoria -> documentos -> postulacion -> cierre.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fechas_convocatoria_orden'
    ) THEN
        ALTER TABLE FECHAS_CONVOCATORIA
        ADD CONSTRAINT chk_fechas_convocatoria_orden
        CHECK (
            convocatoria_inicio <= fecha_limite_documentos
            AND fecha_limite_documentos <= fecha_inicio_postulacion
            AND fecha_inicio_postulacion <= convocatoria_fin
        ) NOT VALID;
    END IF;
END $$;

-- Refuerzo idempotente del tipo de proceso esperado por el calendario.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fechas_convocatoria_tipo'
    ) THEN
        ALTER TABLE FECHAS_CONVOCATORIA
        ADD CONSTRAINT chk_fechas_convocatoria_tipo
        CHECK (tipo IN ('PRACTICAS', 'VINCULACION')) NOT VALID;
    END IF;
END $$;

-- Cada periodo academico debe tener una sola fecha limite por parcial.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = current_schema()
          AND indexname = 'uq_fechas_limite_periodo_parcial'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM FECHAS_LIMITE_CALIFICACION
        GROUP BY periodo_academico, parcial
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX uq_fechas_limite_periodo_parcial
            ON FECHAS_LIMITE_CALIFICACION(periodo_academico, parcial);
    END IF;
END $$;

-- Indices de consulta por calendario vigente.
CREATE INDEX IF NOT EXISTS idx_fechas_convocatoria_periodo_tipo
    ON FECHAS_CONVOCATORIA(periodo_academico, tipo);

CREATE INDEX IF NOT EXISTS idx_fechas_limite_periodo
    ON FECHAS_LIMITE_CALIFICACION(periodo_academico);
