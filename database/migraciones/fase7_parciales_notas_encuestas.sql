-- Fase 7: parciales, notas por rol y encuesta persistente
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - una evaluacion por practica/parcial
-- - notas 0-10 reforzadas en base de datos
-- - encuesta persistente por practica o vinculacion/parcial
-- - evitar doble encuesta del mismo expediente/parcial

BEGIN;

CREATE TABLE IF NOT EXISTS EVALUACIONES_PRACTICAS_DETALLE_DUP_FASE7 AS
SELECT
    e.*,
    CURRENT_TIMESTAMP::TIMESTAMP AS respaldado_en,
    'duplicado practica/parcial removido antes de UNIQUE'::TEXT AS motivo_limpieza
FROM EVALUACIONES_PRACTICAS_DETALLE e
WITH NO DATA;

WITH duplicadas AS (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY practica_id, parcial
                ORDER BY id DESC
            ) AS rn
        FROM EVALUACIONES_PRACTICAS_DETALLE
        WHERE practica_id IS NOT NULL
          AND parcial IS NOT NULL
    ) ranked
    WHERE rn > 1
),
respaldo AS (
    INSERT INTO EVALUACIONES_PRACTICAS_DETALLE_DUP_FASE7
    SELECT
        e.*,
        CURRENT_TIMESTAMP,
        'duplicado practica/parcial removido antes de UNIQUE'
    FROM EVALUACIONES_PRACTICAS_DETALLE e
    JOIN duplicadas d ON d.id = e.id
    WHERE NOT EXISTS (
        SELECT 1
        FROM EVALUACIONES_PRACTICAS_DETALLE_DUP_FASE7 b
        WHERE b.id = e.id
    )
    RETURNING id
)
DELETE FROM EVALUACIONES_PRACTICAS_DETALLE e
USING duplicadas d
WHERE e.id = d.id;

UPDATE EVALUACIONES_PRACTICAS_DETALLE
SET nota_tutor = 0
WHERE nota_tutor IS NULL;

UPDATE EVALUACIONES_PRACTICAS_DETALLE
SET nota_coord = 0
WHERE nota_coord IS NULL;

UPDATE EVALUACIONES_PRACTICAS_DETALLE
SET nota_final = ROUND(((nota_tutor * 0.5) + (nota_coord * 0.5))::numeric, 2);

UPDATE EVALUACIONES_PRACTICAS_DETALLE
SET encuesta_completada = FALSE
WHERE encuesta_completada IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_evaluaciones_practica_parcial'
    ) THEN
        ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
        ADD CONSTRAINT uq_evaluaciones_practica_parcial
        UNIQUE (practica_id, parcial);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_evaluaciones_notas_0_10'
    ) THEN
        ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
        ADD CONSTRAINT chk_evaluaciones_notas_0_10
        CHECK (
            nota_tutor BETWEEN 0 AND 10
            AND nota_coord BETWEEN 0 AND 10
            AND nota_final BETWEEN 0 AND 10
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS ENCUESTAS_SATISFACCION (
    id                              SERIAL PRIMARY KEY,
    estudiante_id                   INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    practica_id                     INT REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    vinculacion_id                  INT REFERENCES VINCULACION(id) ON DELETE CASCADE,
    parcial                         INT NOT NULL CHECK (parcial IN (1, 2, 3)),
    satisfaccion_tutor              INT NOT NULL CHECK (satisfaccion_tutor BETWEEN 1 AND 5),
    satisfaccion_empresa_proyecto   INT NOT NULL CHECK (satisfaccion_empresa_proyecto BETWEEN 1 AND 5),
    relacion_carrera                INT NOT NULL CHECK (relacion_carrera BETWEEN 1 AND 5),
    claridad_instrucciones          INT NOT NULL CHECK (claridad_instrucciones BETWEEN 1 AND 5),
    comentario                      TEXT,
    fecha_envio                     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (practica_id IS NOT NULL AND vinculacion_id IS NULL)
        OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_encuestas_practica_parcial
ON ENCUESTAS_SATISFACCION(practica_id, parcial)
WHERE practica_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_encuestas_vinculacion_parcial
ON ENCUESTAS_SATISFACCION(vinculacion_id, parcial)
WHERE vinculacion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_encuestas_estudiante
ON ENCUESTAS_SATISFACCION(estudiante_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc
        WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_encuestas_satisfaccion
            AFTER INSERT OR UPDATE OR DELETE ON ENCUESTAS_SATISFACCION
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, practica_id, parcial, nota_tutor, nota_coord, nota_final
FROM EVALUACIONES_PRACTICAS_DETALLE
WHERE nota_tutor NOT BETWEEN 0 AND 10
   OR nota_coord NOT BETWEEN 0 AND 10
   OR nota_final NOT BETWEEN 0 AND 10
   OR parcial NOT IN (1, 2, 3);
