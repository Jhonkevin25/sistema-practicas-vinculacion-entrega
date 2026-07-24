-- Fase 38: capacidad pactada de fundaciones y cupos totales de proyectos.
-- Ejecutar manualmente en el SQL Editor de Supabase antes de reiniciar el backend.
-- Script idempotente: conserva datos existentes y puede ejecutarse mas de una vez.

ALTER TABLE PROYECTOS
    ADD COLUMN IF NOT EXISTS cupos_totales INT;

UPDATE PROYECTOS
SET cupos_disponibles = 0
WHERE cupos_disponibles IS NULL;

-- Los proyectos historicos solo almacenaban cupos restantes. Para reconstruir
-- su capacidad original se suman las vinculaciones oficiales ya generadas.
UPDATE PROYECTOS p
SET cupos_totales = GREATEST(
        COALESCE(p.cupos_disponibles, 0)
        + (
            SELECT COUNT(*)::INT
            FROM VINCULACION v
            WHERE v.proyecto_id = p.id
        ),
        0
    )
WHERE p.cupos_totales IS NULL;

ALTER TABLE PROYECTOS
    ALTER COLUMN cupos_totales SET DEFAULT 0,
    ALTER COLUMN cupos_totales SET NOT NULL,
    ALTER COLUMN cupos_disponibles SET DEFAULT 0,
    ALTER COLUMN cupos_disponibles SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM PROYECTOS
        WHERE cupos_totales < 0
           OR cupos_disponibles < 0
           OR cupos_disponibles > cupos_totales
    ) THEN
        RAISE EXCEPTION 'Existen proyectos con cupos inconsistentes. Debe cumplirse 0 <= disponibles <= totales.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_proyectos_cupos_totales_no_negativos'
    ) THEN
        ALTER TABLE PROYECTOS
            ADD CONSTRAINT chk_proyectos_cupos_totales_no_negativos
            CHECK (cupos_totales >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_proyectos_cupos_consistentes'
    ) THEN
        ALTER TABLE PROYECTOS
            ADD CONSTRAINT chk_proyectos_cupos_consistentes
            CHECK (cupos_disponibles <= cupos_totales);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_proyectos_fundacion_periodo
    ON PROYECTOS(fundacion_id, periodo);

COMMENT ON COLUMN PROYECTOS.cupos_totales IS
    'Cupos reservados para el proyecto dentro de la capacidad pactada de la fundacion y el periodo.';

COMMENT ON COLUMN PROYECTOS.cupos_disponibles IS
    'Cupos restantes del proyecto; disminuyen al consolidar una vinculacion oficial.';

-- Validacion: debe devolver cero.
SELECT COUNT(*) AS proyectos_cupos_invalidos
FROM PROYECTOS
WHERE cupos_totales < 0
   OR cupos_disponibles < 0
   OR cupos_disponibles > cupos_totales;

