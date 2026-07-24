-- Fase 17: paridad de vinculación con prácticas en parciales/notas.
-- Idempotente: permite que EVALUACIONES_PRACTICAS_DETALLE se asocie a
-- práctica o vinculación, exactamente a uno de los dos expedientes.

ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
ADD COLUMN IF NOT EXISTS vinculacion_id INT;

ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
ALTER COLUMN practica_id DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_eval_detalle_vinculacion'
    ) THEN
        ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
        ADD CONSTRAINT fk_eval_detalle_vinculacion
        FOREIGN KEY (vinculacion_id) REFERENCES VINCULACION(id) ON DELETE CASCADE;
    END IF;
END $$;

ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
DROP CONSTRAINT IF EXISTS evaluaciones_practicas_detalle_practica_id_parcial_key;

ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
DROP CONSTRAINT IF EXISTS uq_evaluacion_practica_parcial;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_eval_detalle_un_solo_expediente'
    ) THEN
        ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
        ADD CONSTRAINT chk_eval_detalle_un_solo_expediente
        CHECK (
            (practica_id IS NOT NULL AND vinculacion_id IS NULL)
            OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
        );
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_eval_detalle_practica_parcial
ON EVALUACIONES_PRACTICAS_DETALLE(practica_id, parcial)
WHERE practica_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_eval_detalle_vinculacion_parcial
ON EVALUACIONES_PRACTICAS_DETALLE(vinculacion_id, parcial)
WHERE vinculacion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_eval_detalle_vinculacion
ON EVALUACIONES_PRACTICAS_DETALLE(vinculacion_id);
