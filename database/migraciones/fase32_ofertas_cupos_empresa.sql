-- ============================================================
-- FASE 32 - OFERTAS DE CUPOS DE EMPRESA POR PERIODO
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
-- Requiere que la Fase 27 (tabla CARRERAS) ya este aplicada.
-- ============================================================
-- La oferta es la capacidad operativa informada por la empresa.
-- Los cupos reservados se derivan de VACANTES_PRACTICAS.cupos y
-- los ocupados se derivan de PRACTICAS. El convenio queda como
-- requisito documental y no actua como contador de disponibilidad.

CREATE TABLE IF NOT EXISTS OFERTAS_CUPOS_EMPRESA (
    id                  SERIAL PRIMARY KEY,
    empresa_id          INT NOT NULL REFERENCES EMPRESAS(id) ON DELETE RESTRICT,
    periodo_academico   VARCHAR(20) NOT NULL,
    distribucion        VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    cupos_totales       INT NOT NULL DEFAULT 0,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    observacion         TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oferta_cupos_empresa_periodo UNIQUE (empresa_id, periodo_academico),
    CONSTRAINT chk_oferta_cupos_distribucion
        CHECK (distribucion IN ('GENERAL', 'POR_CARRERA')),
    CONSTRAINT chk_oferta_cupos_totales_no_negativos
        CHECK (cupos_totales >= 0),
    CONSTRAINT chk_oferta_cupos_periodo_no_vacio
        CHECK (BTRIM(periodo_academico) <> '')
);

CREATE TABLE IF NOT EXISTS OFERTAS_CUPOS_EMPRESA_CARRERAS (
    id              SERIAL PRIMARY KEY,
    oferta_id       INT NOT NULL REFERENCES OFERTAS_CUPOS_EMPRESA(id) ON DELETE CASCADE,
    carrera_id      INT NOT NULL REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    cupos           INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_oferta_cupos_empresa_carrera UNIQUE (oferta_id, carrera_id),
    CONSTRAINT chk_oferta_carrera_cupos_positivos CHECK (cupos > 0)
);

CREATE INDEX IF NOT EXISTS idx_ofertas_cupos_empresa_periodo
    ON OFERTAS_CUPOS_EMPRESA(periodo_academico, empresa_id, activo);

CREATE INDEX IF NOT EXISTS idx_ofertas_cupos_carreras_oferta
    ON OFERTAS_CUPOS_EMPRESA_CARRERAS(oferta_id, carrera_id);

ALTER TABLE VACANTES_PRACTICAS
    ADD COLUMN IF NOT EXISTS periodo_academico VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_vacantes_empresa_periodo_carrera
    ON VACANTES_PRACTICAS(empresa_id, periodo_academico, carrera);

-- Las vacantes heredadas se vinculan al ultimo periodo configurado
-- de Practicas. Si aun no existe calendario, permanecen sin periodo
-- y el backend exigira uno al crear las nuevas.
WITH periodo_base AS (
    SELECT periodo_academico
    FROM FECHAS_CONVOCATORIA
    WHERE tipo = 'PRACTICAS'
    ORDER BY convocatoria_inicio DESC, id DESC
    LIMIT 1
)
UPDATE VACANTES_PRACTICAS
SET periodo_academico = (SELECT periodo_academico FROM periodo_base)
WHERE periodo_academico IS NULL
  AND EXISTS (SELECT 1 FROM periodo_base);

-- Migra el numero global heredado de EMPRESAS al ultimo periodo de
-- Practicas. Para no invalidar datos existentes, la capacidad inicial
-- nunca queda por debajo de vacantes abiertas + practicas oficiales.
WITH periodo_base AS (
    SELECT periodo_academico
    FROM FECHAS_CONVOCATORIA
    WHERE tipo = 'PRACTICAS'
    ORDER BY convocatoria_inicio DESC, id DESC
    LIMIT 1
),
reservas AS (
    SELECT empresa_id, periodo_academico, COALESCE(SUM(cupos), 0)::INT AS total
    FROM VACANTES_PRACTICAS
    WHERE periodo_academico IS NOT NULL
    GROUP BY empresa_id, periodo_academico
),
ocupados AS (
    SELECT empresa_id, periodo_academico, COUNT(*)::INT AS total
    FROM PRACTICAS
    WHERE periodo_academico IS NOT NULL
    GROUP BY empresa_id, periodo_academico
)
INSERT INTO OFERTAS_CUPOS_EMPRESA
    (empresa_id, periodo_academico, distribucion, cupos_totales, activo, observacion)
SELECT
    e.id,
    pb.periodo_academico,
    'GENERAL',
    GREATEST(
        COALESCE(e.cupos_disponibles, 0),
        COALESCE(r.total, 0) + COALESCE(o.total, 0)
    ),
    TRUE,
    'Migrada automaticamente desde los cupos heredados de la empresa.'
FROM EMPRESAS e
CROSS JOIN periodo_base pb
LEFT JOIN reservas r
       ON r.empresa_id = e.id
      AND r.periodo_academico = pb.periodo_academico
LEFT JOIN ocupados o
       ON o.empresa_id = e.id
      AND o.periodo_academico = pb.periodo_academico
WHERE GREATEST(
        COALESCE(e.cupos_disponibles, 0),
        COALESCE(r.total, 0) + COALESCE(o.total, 0)
      ) > 0
ON CONFLICT (empresa_id, periodo_academico) DO NOTHING;

COMMENT ON COLUMN EMPRESAS.cupos_disponibles IS
    'Campo heredado. La capacidad operativa se administra en OFERTAS_CUPOS_EMPRESA.';

COMMENT ON COLUMN CONVENIOS.cupos_pactados IS
    'Dato documental heredado. No controla disponibilidad ni descuentos de vacantes.';

-- ============================================================
-- VERIFICACION
-- Debe devolver cero filas en ambas consultas de inconsistencias.
-- ============================================================
SELECT o.id, o.empresa_id, o.periodo_academico, o.distribucion, o.cupos_totales
FROM OFERTAS_CUPOS_EMPRESA o
WHERE o.cupos_totales < 0
   OR BTRIM(o.periodo_academico) = '';

SELECT oc.id, oc.oferta_id, oc.carrera_id, oc.cupos
FROM OFERTAS_CUPOS_EMPRESA_CARRERAS oc
WHERE oc.cupos <= 0;

SELECT
    o.id,
    e.nombre AS empresa,
    o.periodo_academico,
    o.distribucion,
    o.cupos_totales,
    o.activo
FROM OFERTAS_CUPOS_EMPRESA o
JOIN EMPRESAS e ON e.id = o.empresa_id
ORDER BY o.periodo_academico DESC, e.nombre;
