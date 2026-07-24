-- ============================================================
-- FASE 35 - HORAS OFICIALES DERIVADAS DE BITACORAS APROBADAS
-- ============================================================
-- Ejecutar completo en el SQL Editor de Supabase. Es idempotente.
-- Las horas completadas se recalculan; no se eliminan bitacoras.

BEGIN;

DO $$
DECLARE
    ids_practicas TEXT;
    ids_vinculaciones TEXT;
BEGIN
    SELECT STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO ids_practicas
    FROM (
        SELECT id
        FROM PRACTICAS
        WHERE horas_requeridas IS NULL OR horas_requeridas <= 0
        ORDER BY id
        LIMIT 20
    ) invalidas;

    SELECT STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO ids_vinculaciones
    FROM (
        SELECT id
        FROM VINCULACION
        WHERE horas_requeridas IS NULL OR horas_requeridas <= 0
        ORDER BY id
        LIMIT 20
    ) invalidas;

    IF ids_practicas IS NOT NULL OR ids_vinculaciones IS NOT NULL THEN
        RAISE EXCEPTION
            'Existen expedientes con horas requeridas nulas o no positivas. Practicas: %. Vinculaciones: %. Corrige esos datos antes de aplicar la Fase 35.',
            COALESCE(ids_practicas, 'ninguna'), COALESCE(ids_vinculaciones, 'ninguna');
    END IF;
END $$;

-- El total oficial es la suma aprobada, limitada a las horas requeridas.
UPDATE PRACTICAS p
SET horas_completadas = LEAST(
    p.horas_requeridas,
    COALESCE((
        SELECT SUM(b.horas)
        FROM BITACORAS b
        WHERE b.practica_id = p.id
          AND b.estado = 'aprobada'
    ), 0)::INT
);

UPDATE VINCULACION v
SET horas_completadas = LEAST(
    v.horas_requeridas,
    COALESCE((
        SELECT SUM(b.horas)
        FROM BITACORAS b
        WHERE b.vinculacion_id = v.id
          AND b.estado = 'aprobada'
    ), 0)::INT
);

ALTER TABLE PRACTICAS
    ALTER COLUMN horas_requeridas SET NOT NULL,
    ALTER COLUMN horas_completadas SET DEFAULT 0,
    ALTER COLUMN horas_completadas SET NOT NULL;

ALTER TABLE VINCULACION
    ALTER COLUMN horas_requeridas DROP DEFAULT,
    ALTER COLUMN horas_requeridas SET NOT NULL,
    ALTER COLUMN horas_completadas SET DEFAULT 0,
    ALTER COLUMN horas_completadas SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'PRACTICAS'::regclass
          AND conname = 'chk_practicas_horas_validas'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT chk_practicas_horas_validas
            CHECK (
                horas_requeridas > 0
                AND horas_completadas BETWEEN 0 AND horas_requeridas
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'VINCULACION'::regclass
          AND conname = 'chk_vinculacion_horas_validas'
    ) THEN
        ALTER TABLE VINCULACION
            ADD CONSTRAINT chk_vinculacion_horas_validas
            CHECK (
                horas_requeridas > 0
                AND horas_completadas BETWEEN 0 AND horas_requeridas
            );
    END IF;
END $$;

CREATE OR REPLACE FUNCTION fn_horas_aprobadas_practica(
    expediente_id INT,
    horas_requeridas_expediente INT
)
RETURNS INT AS $$
    SELECT LEAST(
        horas_requeridas_expediente,
        COALESCE(SUM(b.horas), 0)::INT
    )
    FROM BITACORAS b
    WHERE b.practica_id = expediente_id
      AND b.estado = 'aprobada';
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION fn_horas_aprobadas_vinculacion(
    expediente_id INT,
    horas_requeridas_expediente INT
)
RETURNS INT AS $$
    SELECT LEAST(
        horas_requeridas_expediente,
        COALESCE(SUM(b.horas), 0)::INT
    )
    FROM BITACORAS b
    WHERE b.vinculacion_id = expediente_id
      AND b.estado = 'aprobada';
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION fn_derivar_horas_practica()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.horas_requeridas IS NULL OR NEW.horas_requeridas <= 0 THEN
        RAISE EXCEPTION 'Las horas requeridas de la practica deben ser mayores a cero.';
    END IF;

    NEW.horas_completadas := CASE
        WHEN NEW.id IS NULL THEN 0
        ELSE fn_horas_aprobadas_practica(NEW.id, NEW.horas_requeridas)
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_derivar_horas_vinculacion()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.horas_requeridas IS NULL OR NEW.horas_requeridas <= 0 THEN
        RAISE EXCEPTION 'Las horas requeridas de la vinculacion deben ser mayores a cero.';
    END IF;

    NEW.horas_completadas := CASE
        WHEN NEW.id IS NULL THEN 0
        ELSE fn_horas_aprobadas_vinculacion(NEW.id, NEW.horas_requeridas)
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_derivar_horas_practica ON PRACTICAS;
CREATE TRIGGER trg_derivar_horas_practica
    BEFORE INSERT OR UPDATE OF horas_requeridas, horas_completadas
    ON PRACTICAS
    FOR EACH ROW
    EXECUTE FUNCTION fn_derivar_horas_practica();

DROP TRIGGER IF EXISTS trg_derivar_horas_vinculacion ON VINCULACION;
CREATE TRIGGER trg_derivar_horas_vinculacion
    BEFORE INSERT OR UPDATE OF horas_requeridas, horas_completadas
    ON VINCULACION
    FOR EACH ROW
    EXECUTE FUNCTION fn_derivar_horas_vinculacion();

CREATE OR REPLACE FUNCTION fn_sincronizar_horas_desde_bitacora()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        IF OLD.practica_id IS NOT NULL THEN
            UPDATE PRACTICAS
            SET horas_completadas = fn_horas_aprobadas_practica(id, horas_requeridas)
            WHERE id = OLD.practica_id;
        END IF;
        IF OLD.vinculacion_id IS NOT NULL THEN
            UPDATE VINCULACION
            SET horas_completadas = fn_horas_aprobadas_vinculacion(id, horas_requeridas)
            WHERE id = OLD.vinculacion_id;
        END IF;
    END IF;

    IF TG_OP <> 'DELETE' THEN
        IF NEW.practica_id IS NOT NULL THEN
            UPDATE PRACTICAS
            SET horas_completadas = fn_horas_aprobadas_practica(id, horas_requeridas)
            WHERE id = NEW.practica_id;
        END IF;
        IF NEW.vinculacion_id IS NOT NULL THEN
            UPDATE VINCULACION
            SET horas_completadas = fn_horas_aprobadas_vinculacion(id, horas_requeridas)
            WHERE id = NEW.vinculacion_id;
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sincronizar_horas_bitacora ON BITACORAS;
CREATE TRIGGER trg_sincronizar_horas_bitacora
    AFTER INSERT OR UPDATE OF horas, estado, practica_id, vinculacion_id OR DELETE
    ON BITACORAS
    FOR EACH ROW
    EXECUTE FUNCTION fn_sincronizar_horas_desde_bitacora();

ALTER TABLE PRACTICAS VALIDATE CONSTRAINT chk_practicas_horas_validas;
ALTER TABLE VINCULACION VALIDATE CONSTRAINT chk_vinculacion_horas_validas;

COMMIT;

-- Resultado esperado: dos constraints con validada = true.
SELECT conrelid::regclass AS tabla, conname AS nombre_constraint, convalidated AS validada
FROM pg_constraint
WHERE conname IN ('chk_practicas_horas_validas', 'chk_vinculacion_horas_validas')
ORDER BY 1, 2;

-- Resultado esperado: una fila con invalidas = 0.
SELECT COUNT(*) AS invalidas
FROM (
    SELECT p.id
    FROM PRACTICAS p
    WHERE p.horas_requeridas <= 0
       OR p.horas_completadas < 0
       OR p.horas_completadas > p.horas_requeridas
       OR p.horas_completadas <> fn_horas_aprobadas_practica(p.id, p.horas_requeridas)
    UNION ALL
    SELECT v.id
    FROM VINCULACION v
    WHERE v.horas_requeridas <= 0
       OR v.horas_completadas < 0
       OR v.horas_completadas > v.horas_requeridas
       OR v.horas_completadas <> fn_horas_aprobadas_vinculacion(v.id, v.horas_requeridas)
) expedientes_invalidos;
