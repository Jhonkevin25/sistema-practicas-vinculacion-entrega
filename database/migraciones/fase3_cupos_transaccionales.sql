-- Fase 3: Cupos transaccionales
-- Ejecutar en el SQL Editor de Supabase antes de validar cupos en produccion.
-- Refuerza que los cupos no puedan quedar negativos a nivel de base de datos.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM VACANTES_PRACTICAS
        WHERE cupos < 0
    ) THEN
        RAISE EXCEPTION 'Existen VACANTES_PRACTICAS con cupos negativos. Corrige esos datos antes de aplicar la constraint.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_vacantes_practicas_cupos_no_negativos'
    ) THEN
        ALTER TABLE VACANTES_PRACTICAS
        ADD CONSTRAINT chk_vacantes_practicas_cupos_no_negativos
        CHECK (cupos >= 0);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM PROYECTOS
        WHERE cupos_disponibles < 0
    ) THEN
        RAISE EXCEPTION 'Existen PROYECTOS con cupos_disponibles negativos. Corrige esos datos antes de aplicar la constraint.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_proyectos_cupos_no_negativos'
    ) THEN
        ALTER TABLE PROYECTOS
        ADD CONSTRAINT chk_proyectos_cupos_no_negativos
        CHECK (cupos_disponibles >= 0);
    END IF;
END $$;

-- Validacion de control: ambas consultas deben devolver 0.
SELECT id, nombre, cupos
FROM VACANTES_PRACTICAS
WHERE cupos < 0;

SELECT id, nombre, cupos_disponibles
FROM PROYECTOS
WHERE cupos_disponibles < 0;
