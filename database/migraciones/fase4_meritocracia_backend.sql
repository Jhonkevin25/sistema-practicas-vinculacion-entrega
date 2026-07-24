-- Fase 4: Meritocracia calculada en backend
-- Ejecutar en el SQL Editor de Supabase.
-- Agrega notas academicas verificadas para MVP e integracion futura con la U.
-- Refuerza preferencias minimas y no repetidas a nivel de base de datos.

CREATE TABLE IF NOT EXISTS NOTAS_ACADEMICAS (
    id                      SERIAL PRIMARY KEY,
    estudiante_id           INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    carrera                 VARCHAR(200) NOT NULL,
    semestre                INT NOT NULL CHECK (semestre >= 1),
    periodo_academico       VARCHAR(20) NOT NULL,
    promedio                DECIMAL(4,2) NOT NULL CHECK (promedio >= 0 AND promedio <= 10),
    documento_url           VARCHAR(300),
    fuente                  VARCHAR(30) NOT NULL DEFAULT 'MANUAL'
                            CHECK (fuente IN ('MANUAL', 'CSV_UNIVERSIDAD', 'API_UNIVERSIDAD')),
    external_id             VARCHAR(120),
    email_institucional     VARCHAR(150),
    estado                  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
                            CHECK (estado IN ('PENDIENTE', 'VERIFICADO', 'RECHAZADO')),
    observaciones           TEXT,
    verificado_por          INT REFERENCES USUARIOS(id),
    fecha_verificacion      TIMESTAMP,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(estudiante_id, periodo_academico, semestre)
);

CREATE INDEX IF NOT EXISTS idx_notas_academicas_estudiante
ON NOTAS_ACADEMICAS(estudiante_id);

CREATE INDEX IF NOT EXISTS idx_notas_academicas_estado
ON NOTAS_ACADEMICAS(estado);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc
        WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_notas_academicas
            AFTER INSERT OR UPDATE OR DELETE ON NOTAS_ACADEMICAS
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM POSTULACIONES_MERITOCRATICAS
        WHERE pref1_id IS NULL
           OR pref2_id IS NULL
           OR pref1_id = pref2_id
           OR pref3_id = pref1_id
           OR pref3_id = pref2_id
    ) THEN
        RAISE EXCEPTION 'Existen postulaciones meritocraticas con preferencias incompletas o repetidas. Corrige esos datos antes de aplicar las constraints.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_postulaciones_preferencias_distintas'
    ) THEN
        ALTER TABLE POSTULACIONES_MERITOCRATICAS
        ADD CONSTRAINT chk_postulaciones_preferencias_distintas
        CHECK (
            pref1_id IS NOT NULL
            AND pref2_id IS NOT NULL
            AND pref1_id <> pref2_id
            AND (pref3_id IS NULL OR (pref3_id <> pref1_id AND pref3_id <> pref2_id))
        );
    END IF;
END $$;

-- Validacion de control: debe devolver 0 filas.
SELECT id, estudiante_id, pref1_id, pref2_id, pref3_id
FROM POSTULACIONES_MERITOCRATICAS
WHERE pref1_id IS NULL
   OR pref2_id IS NULL
   OR pref1_id = pref2_id
   OR pref3_id = pref1_id
   OR pref3_id = pref2_id;
