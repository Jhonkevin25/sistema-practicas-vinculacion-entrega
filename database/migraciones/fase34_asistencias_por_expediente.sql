-- ============================================================
-- FASE 34 - ASISTENCIAS ASOCIADAS AL EXPEDIENTE EXACTO
-- ============================================================
-- Ejecutar completo en el SQL Editor de Supabase. Es idempotente.
-- No elimina asistencias y no asigna registros ambiguos.

BEGIN;

ALTER TABLE ASISTENCIAS
    ADD COLUMN IF NOT EXISTS practica_id INT,
    ADD COLUMN IF NOT EXISTS vinculacion_id INT;

-- Primera pasada: asocia por estudiante y rango de fechas solamente cuando
-- existe un unico expediente compatible entre Practicas y Vinculacion.
WITH candidatos AS (
    SELECT a.id AS asistencia_id, 'P'::TEXT AS tipo, p.id AS expediente_id
    FROM ASISTENCIAS a
    JOIN PRACTICAS p ON p.estudiante_id = a.estudiante_id
    WHERE a.practica_id IS NULL
      AND a.vinculacion_id IS NULL
      AND (p.fecha_inicio IS NULL OR a.fecha >= p.fecha_inicio)
      AND (p.fecha_fin IS NULL OR a.fecha <= p.fecha_fin)
    UNION ALL
    SELECT a.id, 'V'::TEXT, v.id
    FROM ASISTENCIAS a
    JOIN VINCULACION v ON v.estudiante_id = a.estudiante_id
    WHERE a.practica_id IS NULL
      AND a.vinculacion_id IS NULL
      AND (v.fecha_inicio IS NULL OR a.fecha >= v.fecha_inicio)
      AND (v.fecha_fin IS NULL OR a.fecha <= v.fecha_fin)
), unicos AS (
    SELECT asistencia_id,
           MAX(tipo) AS tipo,
           MAX(expediente_id) AS expediente_id
    FROM candidatos
    GROUP BY asistencia_id
    HAVING COUNT(*) = 1
)
UPDATE ASISTENCIAS a
SET practica_id = CASE WHEN u.tipo = 'P' THEN u.expediente_id END,
    vinculacion_id = CASE WHEN u.tipo = 'V' THEN u.expediente_id END
FROM unicos u
WHERE a.id = u.asistencia_id;

-- Segunda pasada: si el estudiante solo ha tenido un expediente en todo el
-- sistema, la asociacion tambien es inequivoca aunque falten fechas historicas.
WITH candidatos AS (
    SELECT a.id AS asistencia_id, 'P'::TEXT AS tipo, p.id AS expediente_id
    FROM ASISTENCIAS a
    JOIN PRACTICAS p ON p.estudiante_id = a.estudiante_id
    WHERE a.practica_id IS NULL AND a.vinculacion_id IS NULL
    UNION ALL
    SELECT a.id, 'V'::TEXT, v.id
    FROM ASISTENCIAS a
    JOIN VINCULACION v ON v.estudiante_id = a.estudiante_id
    WHERE a.practica_id IS NULL AND a.vinculacion_id IS NULL
), unicos AS (
    SELECT asistencia_id,
           MAX(tipo) AS tipo,
           MAX(expediente_id) AS expediente_id
    FROM candidatos
    GROUP BY asistencia_id
    HAVING COUNT(*) = 1
)
UPDATE ASISTENCIAS a
SET practica_id = CASE WHEN u.tipo = 'P' THEN u.expediente_id END,
    vinculacion_id = CASE WHEN u.tipo = 'V' THEN u.expediente_id END
FROM unicos u
WHERE a.id = u.asistencia_id;

DO $$
DECLARE
    ids TEXT;
BEGIN
    SELECT STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO ids
    FROM (
        SELECT id
        FROM ASISTENCIAS
        WHERE (practica_id IS NULL AND vinculacion_id IS NULL)
           OR (practica_id IS NOT NULL AND vinculacion_id IS NOT NULL)
        ORDER BY id
        LIMIT 20
    ) pendientes;

    IF ids IS NOT NULL THEN
        RAISE EXCEPTION
            'Hay asistencias sin expediente unico o asociadas a ambos procesos. IDs: %. Corrige esos datos antes de aplicar la Fase 34.',
            ids;
    END IF;

    SELECT STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO ids
    FROM (
        SELECT a.id
        FROM ASISTENCIAS a
        LEFT JOIN PRACTICAS p ON p.id = a.practica_id
        LEFT JOIN VINCULACION v ON v.id = a.vinculacion_id
        WHERE (a.practica_id IS NOT NULL AND p.estudiante_id IS DISTINCT FROM a.estudiante_id)
           OR (a.vinculacion_id IS NOT NULL AND v.estudiante_id IS DISTINCT FROM a.estudiante_id)
        ORDER BY a.id
        LIMIT 20
    ) inconsistentes;

    IF ids IS NOT NULL THEN
        RAISE EXCEPTION
            'Hay asistencias cuyo estudiante no coincide con el expediente. IDs: %.', ids;
    END IF;

    SELECT STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO ids
    FROM (
        SELECT MIN(id) AS id
        FROM ASISTENCIAS
        GROUP BY practica_id, fecha
        HAVING practica_id IS NOT NULL AND COUNT(*) > 1
        UNION ALL
        SELECT MIN(id) AS id
        FROM ASISTENCIAS
        GROUP BY vinculacion_id, fecha
        HAVING vinculacion_id IS NOT NULL AND COUNT(*) > 1
        ORDER BY id
        LIMIT 20
    ) duplicadas;

    IF ids IS NOT NULL THEN
        RAISE EXCEPTION
            'Hay asistencias duplicadas para el mismo expediente y fecha. Revisa los grupos que incluyen los IDs: %.',
            ids;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ASISTENCIAS'::regclass
          AND conname = 'fk_asistencias_practica'
    ) THEN
        ALTER TABLE ASISTENCIAS
            ADD CONSTRAINT fk_asistencias_practica
            FOREIGN KEY (practica_id) REFERENCES PRACTICAS(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ASISTENCIAS'::regclass
          AND conname = 'fk_asistencias_vinculacion'
    ) THEN
        ALTER TABLE ASISTENCIAS
            ADD CONSTRAINT fk_asistencias_vinculacion
            FOREIGN KEY (vinculacion_id) REFERENCES VINCULACION(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ASISTENCIAS'::regclass
          AND conname = 'chk_asistencias_expediente_unico'
    ) THEN
        ALTER TABLE ASISTENCIAS
            ADD CONSTRAINT chk_asistencias_expediente_unico
            CHECK (
                (practica_id IS NOT NULL AND vinculacion_id IS NULL)
                OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
            );
    END IF;
END $$;

ALTER TABLE ASISTENCIAS VALIDATE CONSTRAINT fk_asistencias_practica;
ALTER TABLE ASISTENCIAS VALIDATE CONSTRAINT fk_asistencias_vinculacion;
ALTER TABLE ASISTENCIAS VALIDATE CONSTRAINT chk_asistencias_expediente_unico;

CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencias_practica_fecha
    ON ASISTENCIAS(practica_id, fecha)
    WHERE practica_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencias_vinculacion_fecha
    ON ASISTENCIAS(vinculacion_id, fecha)
    WHERE vinculacion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_asistencias_practica
    ON ASISTENCIAS(practica_id);

CREATE INDEX IF NOT EXISTS idx_asistencias_vinculacion
    ON ASISTENCIAS(vinculacion_id);

CREATE OR REPLACE FUNCTION fn_validar_asistencia_expediente()
RETURNS TRIGGER AS $$
DECLARE
    estudiante_expediente INT;
BEGIN
    IF (NEW.practica_id IS NULL) = (NEW.vinculacion_id IS NULL) THEN
        RAISE EXCEPTION 'La asistencia debe pertenecer exactamente a una practica o vinculacion.';
    END IF;

    IF NEW.practica_id IS NOT NULL THEN
        SELECT estudiante_id INTO estudiante_expediente
        FROM PRACTICAS WHERE id = NEW.practica_id;
    ELSE
        SELECT estudiante_id INTO estudiante_expediente
        FROM VINCULACION WHERE id = NEW.vinculacion_id;
    END IF;

    IF estudiante_expediente IS NULL THEN
        RAISE EXCEPTION 'El expediente asociado a la asistencia no existe.';
    END IF;

    IF NEW.estudiante_id IS DISTINCT FROM estudiante_expediente THEN
        RAISE EXCEPTION 'El estudiante de la asistencia no coincide con el expediente asociado.';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_validar_asistencia_expediente ON ASISTENCIAS;
CREATE TRIGGER trg_validar_asistencia_expediente
    BEFORE INSERT OR UPDATE OF estudiante_id, practica_id, vinculacion_id
    ON ASISTENCIAS
    FOR EACH ROW
    EXECUTE FUNCTION fn_validar_asistencia_expediente();

COMMENT ON COLUMN ASISTENCIAS.practica_id IS
    'Practica exacta a la que pertenece la asistencia; excluyente con vinculacion_id.';
COMMENT ON COLUMN ASISTENCIAS.vinculacion_id IS
    'Vinculacion exacta a la que pertenece la asistencia; excluyente con practica_id.';

COMMIT;

-- Resultado esperado: tres constraints con validada = true.
SELECT conname AS nombre_constraint, convalidated AS validada
FROM pg_constraint
WHERE conrelid = 'ASISTENCIAS'::regclass
  AND conname IN (
      'fk_asistencias_practica',
      'fk_asistencias_vinculacion',
      'chk_asistencias_expediente_unico'
  )
ORDER BY 1;

-- Resultado esperado: una fila con invalidas = 0.
SELECT COUNT(*) AS invalidas
FROM ASISTENCIAS
WHERE (practica_id IS NULL AND vinculacion_id IS NULL)
   OR (practica_id IS NOT NULL AND vinculacion_id IS NOT NULL);
