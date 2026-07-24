-- Fase 2: limpieza de datos de prueba para calendario academico
-- Ejecutar manualmente en el SQL Editor de Supabase.
-- Script idempotente: puede ejecutarse mas de una vez.
--
-- Regla conservada:
-- convocatoria_inicio <= fecha_limite_documentos <= fecha_inicio_postulacion <= convocatoria_fin

-- Corrige filas existentes de prueba que rompen el orden logico.
WITH normalizadas AS (
    SELECT
        id,
        GREATEST(fecha_limite_documentos, convocatoria_inicio) AS fecha_limite_documentos_ok,
        GREATEST(
            fecha_inicio_postulacion,
            GREATEST(fecha_limite_documentos, convocatoria_inicio)
        ) AS fecha_inicio_postulacion_ok
    FROM FECHAS_CONVOCATORIA
    WHERE fecha_limite_documentos < convocatoria_inicio
       OR fecha_inicio_postulacion < fecha_limite_documentos
       OR fecha_inicio_postulacion > convocatoria_fin
)
UPDATE FECHAS_CONVOCATORIA f
SET
    fecha_limite_documentos = n.fecha_limite_documentos_ok,
    fecha_inicio_postulacion = n.fecha_inicio_postulacion_ok,
    convocatoria_fin = GREATEST(f.convocatoria_fin, n.fecha_inicio_postulacion_ok)
FROM normalizadas n
WHERE f.id = n.id;

-- Valida la constraint de orden si ya fue creada por fase2_calendario_academico.sql.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fechas_convocatoria_orden'
    ) THEN
        ALTER TABLE FECHAS_CONVOCATORIA
        VALIDATE CONSTRAINT chk_fechas_convocatoria_orden;
    END IF;
END $$;

-- Valida la constraint de tipo si ya fue creada por fase2_calendario_academico.sql.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fechas_convocatoria_tipo'
    ) THEN
        ALTER TABLE FECHAS_CONVOCATORIA
        VALIDATE CONSTRAINT chk_fechas_convocatoria_tipo;
    END IF;
END $$;

-- Verificacion final: debe devolver 0 filas.
SELECT *
FROM FECHAS_CONVOCATORIA
WHERE NOT (
    convocatoria_inicio <= fecha_limite_documentos
    AND fecha_limite_documentos <= fecha_inicio_postulacion
    AND fecha_inicio_postulacion <= convocatoria_fin
);
