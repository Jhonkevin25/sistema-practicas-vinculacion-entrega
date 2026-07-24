-- Fase 19: convenios institucionales con empresas y fundaciones.
-- Script idempotente para ejecutar en el SQL Editor de Supabase.

BEGIN;

CREATE TABLE IF NOT EXISTS CONVENIOS (
    id              SERIAL PRIMARY KEY,
    codigo          VARCHAR(80) NOT NULL UNIQUE,
    empresa_id      INT REFERENCES EMPRESAS(id) ON DELETE RESTRICT,
    fundacion_id    INT REFERENCES FUNDACIONES(id) ON DELETE RESTRICT,
    fecha_inicio    DATE NOT NULL,
    fecha_fin       DATE NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'VIGENTE',
    url_documento   TEXT,
    cupos_pactados  INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_convenio_entidad_exclusiva CHECK (
        (empresa_id IS NOT NULL AND fundacion_id IS NULL)
        OR (empresa_id IS NULL AND fundacion_id IS NOT NULL)
    ),
    CONSTRAINT chk_convenio_fechas CHECK (fecha_inicio <= fecha_fin),
    CONSTRAINT chk_convenio_estado CHECK (estado IN ('VIGENTE', 'SUSPENDIDO', 'FINALIZADO')),
    CONSTRAINT chk_convenio_cupos_no_negativos CHECK (cupos_pactados >= 0)
);

CREATE TABLE IF NOT EXISTS CONVENIOS_CARRERAS (
    convenio_id INT NOT NULL REFERENCES CONVENIOS(id) ON DELETE CASCADE,
    carrera     VARCHAR(200) NOT NULL CONSTRAINT chk_convenio_carrera_no_vacia CHECK (BTRIM(carrera) <> ''),
    PRIMARY KEY (convenio_id, carrera)
);

CREATE INDEX IF NOT EXISTS idx_convenios_empresa
    ON CONVENIOS(empresa_id, estado, fecha_inicio, fecha_fin);

CREATE INDEX IF NOT EXISTS idx_convenios_fundacion
    ON CONVENIOS(fundacion_id, estado, fecha_inicio, fecha_fin);

CREATE INDEX IF NOT EXISTS idx_convenios_carreras_carrera
    ON CONVENIOS_CARRERAS(carrera);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc
        WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_convenios
            AFTER INSERT OR UPDATE OR DELETE ON CONVENIOS
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: ambas consultas deben devolver 0 filas.
SELECT id, codigo
FROM CONVENIOS
WHERE fecha_inicio > fecha_fin
   OR cupos_pactados < 0
   OR estado NOT IN ('VIGENTE', 'SUSPENDIDO', 'FINALIZADO')
   OR ((empresa_id IS NULL) = (fundacion_id IS NULL));

SELECT convenio_id, carrera
FROM CONVENIOS_CARRERAS
WHERE BTRIM(carrera) = '';
