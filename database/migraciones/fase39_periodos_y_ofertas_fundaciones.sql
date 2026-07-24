-- ============================================================
-- FASE 39 - PERIODOS Y OFERTAS DE CUPOS DE FUNDACIONES
-- Ejecutar manualmente en el SQL Editor de Supabase.
-- Script idempotente: conserva datos existentes y admite reejecucion.
-- Requiere las fases 27, 32 y 38.
-- ============================================================

-- 1. Catalogo institucional de periodos academicos.
CREATE TABLE IF NOT EXISTS PERIODOS_ACADEMICOS (
    id              SERIAL PRIMARY KEY,
    codigo          VARCHAR(20) NOT NULL UNIQUE,
    fecha_inicio    DATE NOT NULL,
    fecha_fin       DATE NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'PLANIFICADO',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_periodos_codigo
        CHECK (codigo ~ '^[0-9]{4}-[12]$'),
    CONSTRAINT chk_periodos_fechas
        CHECK (fecha_inicio <= fecha_fin),
    CONSTRAINT chk_periodos_estado
        CHECK (estado IN ('PLANIFICADO', 'ACTIVO', 'CERRADO'))
);

-- Se detiene ante codigos historicos no normalizados. No se corrigen
-- automaticamente porque un valor ambiguo no debe asociarse al periodo equivocado.
DO $$
DECLARE
    periodos_invalidos INT;
BEGIN
    SELECT COUNT(*)
    INTO periodos_invalidos
    FROM (
        SELECT periodo_academico AS codigo FROM FECHAS_CONVOCATORIA
        UNION ALL SELECT periodo_academico FROM FECHAS_LIMITE_CALIFICACION
        UNION ALL SELECT periodo_academico FROM OFERTAS_CUPOS_EMPRESA
        UNION ALL SELECT periodo_academico FROM VACANTES_PRACTICAS
        UNION ALL SELECT periodo_academico FROM PRACTICAS
        UNION ALL SELECT periodo_academico FROM VINCULACION
        UNION ALL SELECT periodo_academico FROM POSTULACIONES_VINCULACION
        UNION ALL SELECT periodo_academico FROM NOTAS_ACADEMICAS
        UNION ALL SELECT periodo_academico FROM ESTUDIANTES
        UNION ALL SELECT periodo FROM PROYECTOS
    ) historico
    WHERE codigo IS NOT NULL
      AND (BTRIM(codigo) = '' OR BTRIM(codigo) !~ '^[0-9]{4}-[12]$');

    IF periodos_invalidos > 0 THEN
        RAISE EXCEPTION
            'Existen % valores de periodo con formato distinto de AAAA-1 o AAAA-2. Corrige esos datos antes de aplicar Fase 39.',
            periodos_invalidos;
    END IF;
END $$;

UPDATE FECHAS_CONVOCATORIA
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE FECHAS_LIMITE_CALIFICACION
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE OFERTAS_CUPOS_EMPRESA
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE VACANTES_PRACTICAS
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE PRACTICAS
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE VINCULACION
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE POSTULACIONES_VINCULACION
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE NOTAS_ACADEMICAS
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE ESTUDIANTES
SET periodo_academico = BTRIM(periodo_academico)
WHERE periodo_academico IS NOT NULL
  AND periodo_academico <> BTRIM(periodo_academico);

UPDATE PROYECTOS
SET periodo = BTRIM(periodo)
WHERE periodo IS NOT NULL
  AND periodo <> BTRIM(periodo);

WITH codigos AS (
    SELECT BTRIM(periodo_academico) AS codigo FROM FECHAS_CONVOCATORIA
    UNION SELECT BTRIM(periodo_academico) FROM FECHAS_LIMITE_CALIFICACION
    UNION SELECT BTRIM(periodo_academico) FROM OFERTAS_CUPOS_EMPRESA
    UNION SELECT BTRIM(periodo_academico) FROM VACANTES_PRACTICAS
    UNION SELECT BTRIM(periodo_academico) FROM PRACTICAS
    UNION SELECT BTRIM(periodo_academico) FROM VINCULACION
    UNION SELECT BTRIM(periodo_academico) FROM POSTULACIONES_VINCULACION
    UNION SELECT BTRIM(periodo_academico) FROM NOTAS_ACADEMICAS
    UNION SELECT BTRIM(periodo_academico) FROM ESTUDIANTES
    UNION SELECT BTRIM(periodo) FROM PROYECTOS
), fechas AS (
    SELECT
        c.codigo,
        MAKE_DATE(SPLIT_PART(c.codigo, '-', 1)::INT,
                  CASE SPLIT_PART(c.codigo, '-', 2) WHEN '1' THEN 1 ELSE 7 END,
                  1) AS fecha_inicio,
        MAKE_DATE(SPLIT_PART(c.codigo, '-', 1)::INT,
                  CASE SPLIT_PART(c.codigo, '-', 2) WHEN '1' THEN 6 ELSE 12 END,
                  CASE SPLIT_PART(c.codigo, '-', 2) WHEN '1' THEN 30 ELSE 31 END) AS fecha_fin
    FROM codigos c
    WHERE c.codigo IS NOT NULL
      AND c.codigo ~ '^[0-9]{4}-[12]$'
    GROUP BY c.codigo
)
INSERT INTO PERIODOS_ACADEMICOS
    (codigo, fecha_inicio, fecha_fin, estado)
SELECT
    codigo,
    fecha_inicio,
    fecha_fin,
    CASE
        WHEN CURRENT_DATE < fecha_inicio THEN 'PLANIFICADO'
        WHEN CURRENT_DATE > fecha_fin THEN 'CERRADO'
        ELSE 'ACTIVO'
    END
FROM fechas
ON CONFLICT (codigo) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM PERIODOS_ACADEMICOS WHERE estado = 'ACTIVO') > 1 THEN
        RAISE EXCEPTION
            'Existe mas de un periodo ACTIVO. Conserva uno solo antes de continuar.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_periodos_unico_activo
    ON PERIODOS_ACADEMICOS ((estado))
    WHERE estado = 'ACTIVO';

CREATE INDEX IF NOT EXISTS idx_periodos_estado_fechas
    ON PERIODOS_ACADEMICOS(estado, fecha_inicio, fecha_fin);

-- 2. Los proyectos usan el catalogo y guardan los datos propios acordados.
ALTER TABLE PROYECTOS
    ADD COLUMN IF NOT EXISTS horas_requeridas INT,
    ADD COLUMN IF NOT EXISTS ciudad VARCHAR(150),
    ADD COLUMN IF NOT EXISTS modalidad VARCHAR(20);

UPDATE PROYECTOS p
SET periodo = (
    SELECT pa.codigo
    FROM PERIODOS_ACADEMICOS pa
    ORDER BY
        CASE pa.estado WHEN 'ACTIVO' THEN 0 WHEN 'PLANIFICADO' THEN 1 ELSE 2 END,
        pa.fecha_inicio DESC,
        pa.id DESC
    LIMIT 1
)
WHERE p.periodo IS NULL OR BTRIM(p.periodo) = '';

UPDATE VINCULACION v
SET periodo_academico = p.periodo
FROM PROYECTOS p
WHERE p.id = v.proyecto_id
  AND (v.periodo_academico IS NULL OR BTRIM(v.periodo_academico) = '');

UPDATE POSTULACIONES_VINCULACION pv
SET periodo_academico = p.periodo
FROM PROYECTOS p
WHERE p.id = pv.proyecto_id
  AND (pv.periodo_academico IS NULL OR BTRIM(pv.periodo_academico) = '');

UPDATE PROYECTOS p
SET horas_requeridas = COALESCE(
        (
            SELECT MAX(v.horas_requeridas)
            FROM VINCULACION v
            WHERE v.proyecto_id = p.id
              AND v.horas_requeridas > 0
        ),
        160
    )
WHERE p.horas_requeridas IS NULL OR p.horas_requeridas <= 0;

UPDATE PROYECTOS
SET ciudad = 'Por definir'
WHERE ciudad IS NULL OR BTRIM(ciudad) = '';

UPDATE PROYECTOS
SET modalidad = 'NO_ESPECIFICADA'
WHERE modalidad IS NULL OR BTRIM(modalidad) = '';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM PROYECTOS WHERE periodo IS NULL OR BTRIM(periodo) = '') THEN
        RAISE EXCEPTION
            'No fue posible asignar un periodo a todos los proyectos existentes.';
    END IF;
END $$;

ALTER TABLE PROYECTOS
    ALTER COLUMN periodo SET NOT NULL,
    ALTER COLUMN horas_requeridas SET DEFAULT 160,
    ALTER COLUMN horas_requeridas SET NOT NULL,
    ALTER COLUMN ciudad SET DEFAULT 'Por definir',
    ALTER COLUMN ciudad SET NOT NULL,
    ALTER COLUMN modalidad SET DEFAULT 'NO_ESPECIFICADA',
    ALTER COLUMN modalidad SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_proyectos_horas_requeridas_positivas'
          AND conrelid = 'proyectos'::regclass
    ) THEN
        ALTER TABLE PROYECTOS
            ADD CONSTRAINT chk_proyectos_horas_requeridas_positivas
            CHECK (horas_requeridas > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_proyectos_ciudad_no_vacia'
          AND conrelid = 'proyectos'::regclass
    ) THEN
        ALTER TABLE PROYECTOS
            ADD CONSTRAINT chk_proyectos_ciudad_no_vacia
            CHECK (BTRIM(ciudad) <> '');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_proyectos_modalidad'
          AND conrelid = 'proyectos'::regclass
    ) THEN
        ALTER TABLE PROYECTOS
            ADD CONSTRAINT chk_proyectos_modalidad
            CHECK (modalidad IN ('PRESENCIAL', 'VIRTUAL', 'HIBRIDA', 'NO_ESPECIFICADA'));
    END IF;
END $$;

-- 3. Oferta operativa de la fundacion por periodo. El convenio conserva
-- su funcion documental; esta tabla es la fuente de capacidad.
CREATE TABLE IF NOT EXISTS OFERTAS_CUPOS_FUNDACION (
    id                  SERIAL PRIMARY KEY,
    fundacion_id        INT NOT NULL REFERENCES FUNDACIONES(id) ON DELETE RESTRICT,
    periodo_academico   VARCHAR(20) NOT NULL
                        REFERENCES PERIODOS_ACADEMICOS(codigo) ON DELETE RESTRICT,
    distribucion        VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    cupos_totales       INT NOT NULL DEFAULT 0,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    observacion         TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oferta_cupos_fundacion_periodo
        UNIQUE (fundacion_id, periodo_academico),
    CONSTRAINT chk_oferta_fundacion_distribucion
        CHECK (distribucion IN ('GENERAL', 'POR_CARRERA')),
    CONSTRAINT chk_oferta_fundacion_cupos_no_negativos
        CHECK (cupos_totales >= 0),
    CONSTRAINT chk_oferta_fundacion_periodo_no_vacio
        CHECK (BTRIM(periodo_academico) <> '')
);

CREATE TABLE IF NOT EXISTS OFERTAS_CUPOS_FUNDACION_CARRERAS (
    id          SERIAL PRIMARY KEY,
    oferta_id   INT NOT NULL REFERENCES OFERTAS_CUPOS_FUNDACION(id) ON DELETE CASCADE,
    carrera_id  INT NOT NULL REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    cupos       INT NOT NULL,
    CONSTRAINT uq_oferta_cupos_fundacion_carrera
        UNIQUE (oferta_id, carrera_id),
    CONSTRAINT chk_oferta_fundacion_carrera_cupos_positivos
        CHECK (cupos > 0)
);

CREATE INDEX IF NOT EXISTS idx_ofertas_fundacion_periodo
    ON OFERTAS_CUPOS_FUNDACION(periodo_academico, fundacion_id, activo);

CREATE INDEX IF NOT EXISTS idx_ofertas_fundacion_carreras_oferta
    ON OFERTAS_CUPOS_FUNDACION_CARRERAS(oferta_id, carrera_id);

-- La migracion usa el mayor cupo pactado que se solape con el periodo.
-- Si ya existen proyectos, nunca crea una oferta menor que lo comprometido.
WITH compromisos AS (
    SELECT
        p.fundacion_id,
        p.periodo AS periodo_academico,
        SUM(GREATEST(COALESCE(p.cupos_totales, 0), 0))::INT AS cupos
    FROM PROYECTOS p
    GROUP BY p.fundacion_id, p.periodo
), convenios_periodo AS (
    SELECT
        c.fundacion_id,
        pa.codigo AS periodo_academico,
        MAX(GREATEST(COALESCE(c.cupos_pactados, 0), 0))::INT AS cupos
    FROM CONVENIOS c
    JOIN PERIODOS_ACADEMICOS pa
      ON c.fecha_inicio <= pa.fecha_fin
     AND c.fecha_fin >= pa.fecha_inicio
    WHERE c.fundacion_id IS NOT NULL
      AND c.estado IN ('VIGENTE', 'FINALIZADO')
    GROUP BY c.fundacion_id, pa.codigo
), base AS (
    SELECT
        COALESCE(cp.fundacion_id, co.fundacion_id) AS fundacion_id,
        COALESCE(cp.periodo_academico, co.periodo_academico) AS periodo_academico,
        GREATEST(COALESCE(cp.cupos, 0), COALESCE(co.cupos, 0)) AS cupos_totales
    FROM convenios_periodo cp
    FULL OUTER JOIN compromisos co
      ON co.fundacion_id = cp.fundacion_id
     AND co.periodo_academico = cp.periodo_academico
)
INSERT INTO OFERTAS_CUPOS_FUNDACION
    (fundacion_id, periodo_academico, distribucion, cupos_totales, activo, observacion)
SELECT
    b.fundacion_id,
    b.periodo_academico,
    'GENERAL',
    b.cupos_totales,
    pa.estado <> 'CERRADO',
    'Migrada desde convenios y proyectos existentes durante Fase 39.'
FROM base b
JOIN PERIODOS_ACADEMICOS pa ON pa.codigo = b.periodo_academico
WHERE b.cupos_totales > 0
ON CONFLICT (fundacion_id, periodo_academico) DO NOTHING;

-- 4. Carreras habilitadas por proyecto. Los cupos por carrera son opcionales
-- cuando la oferta es GENERAL y obligatorios cuando sea POR_CARRERA.
CREATE TABLE IF NOT EXISTS PROYECTOS_CARRERAS (
    id                  SERIAL PRIMARY KEY,
    proyecto_id         INT NOT NULL REFERENCES PROYECTOS(id) ON DELETE CASCADE,
    carrera_id          INT NOT NULL REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    cupos_totales       INT,
    cupos_disponibles   INT,
    CONSTRAINT uq_proyecto_carrera UNIQUE (proyecto_id, carrera_id),
    CONSTRAINT chk_proyecto_carrera_cupos
        CHECK (
            (cupos_totales IS NULL AND cupos_disponibles IS NULL)
            OR (
                cupos_totales IS NOT NULL
                AND cupos_disponibles IS NOT NULL
                AND cupos_totales > 0
                AND cupos_disponibles >= 0
                AND cupos_disponibles <= cupos_totales
            )
        )
);

CREATE INDEX IF NOT EXISTS idx_proyectos_carreras_proyecto
    ON PROYECTOS_CARRERAS(proyecto_id, carrera_id);

CREATE INDEX IF NOT EXISTS idx_proyectos_carreras_carrera
    ON PROYECTOS_CARRERAS(carrera_id, proyecto_id);

-- Primero se recuperan carreras demostradas por vinculaciones historicas.
INSERT INTO PROYECTOS_CARRERAS (proyecto_id, carrera_id)
SELECT DISTINCT p.id, ca.id
FROM PROYECTOS p
JOIN VINCULACION v ON v.proyecto_id = p.id
JOIN ESTUDIANTES e ON e.id = v.estudiante_id
JOIN CARRERAS ca
  ON TRANSLATE(LOWER(BTRIM(ca.nombre)), 'áéíóúüñ', 'aeiouun')
   = TRANSLATE(LOWER(BTRIM(e.carrera)), 'áéíóúüñ', 'aeiouun')
ON CONFLICT (proyecto_id, carrera_id) DO NOTHING;

-- Luego se incorporan carreras explicitamente cubiertas por convenios que
-- se solapan con el periodo. Un convenio sin carreras no se interpreta como
-- que todos sus proyectos sean para todas las carreras.
INSERT INTO PROYECTOS_CARRERAS (proyecto_id, carrera_id)
SELECT DISTINCT p.id, ca.id
FROM PROYECTOS p
JOIN PERIODOS_ACADEMICOS pa ON pa.codigo = p.periodo
JOIN CONVENIOS c
  ON c.fundacion_id = p.fundacion_id
 AND c.fecha_inicio <= pa.fecha_fin
 AND c.fecha_fin >= pa.fecha_inicio
 AND c.estado IN ('VIGENTE', 'FINALIZADO')
JOIN CONVENIOS_CARRERAS cc ON cc.convenio_id = c.id
JOIN CARRERAS ca
  ON TRANSLATE(LOWER(BTRIM(ca.nombre)), 'áéíóúüñ', 'aeiouun')
   = TRANSLATE(LOWER(BTRIM(cc.carrera)), 'áéíóúüñ', 'aeiouun')
ON CONFLICT (proyecto_id, carrera_id) DO NOTHING;

-- 5. Relaciones del catalogo con las configuraciones que ya operan por periodo.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_fechas_convocatoria_periodo'
          AND conrelid = 'fechas_convocatoria'::regclass
    ) THEN
        ALTER TABLE FECHAS_CONVOCATORIA
            ADD CONSTRAINT fk_fechas_convocatoria_periodo
            FOREIGN KEY (periodo_academico)
            REFERENCES PERIODOS_ACADEMICOS(codigo) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_ofertas_empresa_periodo'
          AND conrelid = 'ofertas_cupos_empresa'::regclass
    ) THEN
        ALTER TABLE OFERTAS_CUPOS_EMPRESA
            ADD CONSTRAINT fk_ofertas_empresa_periodo
            FOREIGN KEY (periodo_academico)
            REFERENCES PERIODOS_ACADEMICOS(codigo) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_proyectos_periodo'
          AND conrelid = 'proyectos'::regclass
    ) THEN
        ALTER TABLE PROYECTOS
            ADD CONSTRAINT fk_proyectos_periodo
            FOREIGN KEY (periodo)
            REFERENCES PERIODOS_ACADEMICOS(codigo) ON DELETE RESTRICT;
    END IF;
END $$;

COMMENT ON TABLE PERIODOS_ACADEMICOS IS
    'Catalogo institucional de periodos; las fechas migradas usan limites semestrales y luego pueden ajustarse por ADMIN.';
COMMENT ON TABLE OFERTAS_CUPOS_FUNDACION IS
    'Capacidad operativa informada por una fundacion para un periodo academico.';
COMMENT ON TABLE PROYECTOS_CARRERAS IS
    'Carreras participantes del proyecto y, si aplica, su distribucion de cupos.';
COMMENT ON COLUMN CONVENIOS.cupos_pactados IS
    'Referencia documental del convenio; la capacidad operativa se administra en ofertas por periodo.';
COMMENT ON COLUMN PROYECTOS.modalidad IS
    'PRESENCIAL, VIRTUAL o HIBRIDA. NO_ESPECIFICADA se reserva para datos historicos migrados.';

-- ============================================================
-- VALIDACION
-- Los tres primeros valores deben ser cero.
-- proyectos_sin_carreras es informativo: no se asignan carreras por suposicion.
-- Esos proyectos se configuraran desde la interfaz de Fase 41.
-- ============================================================
SELECT COUNT(*) AS periodos_invalidos
FROM PERIODOS_ACADEMICOS
WHERE codigo !~ '^[0-9]{4}-[12]$'
   OR fecha_inicio > fecha_fin
   OR estado NOT IN ('PLANIFICADO', 'ACTIVO', 'CERRADO');

SELECT COUNT(*) AS ofertas_fundacion_invalidas
FROM OFERTAS_CUPOS_FUNDACION
WHERE cupos_totales < 0
   OR BTRIM(periodo_academico) = '';

SELECT COUNT(*) AS proyectos_datos_invalidos
FROM PROYECTOS
WHERE periodo IS NULL
   OR BTRIM(periodo) = ''
   OR horas_requeridas <= 0
   OR BTRIM(ciudad) = ''
   OR modalidad NOT IN ('PRESENCIAL', 'VIRTUAL', 'HIBRIDA', 'NO_ESPECIFICADA');

SELECT COUNT(*) AS proyectos_sin_carreras
FROM PROYECTOS p
WHERE NOT EXISTS (
    SELECT 1 FROM PROYECTOS_CARRERAS pc WHERE pc.proyecto_id = p.id
);

SELECT
    pa.codigo,
    pa.estado,
    f.nombre AS fundacion,
    o.distribucion,
    o.cupos_totales,
    o.activo
FROM OFERTAS_CUPOS_FUNDACION o
JOIN PERIODOS_ACADEMICOS pa ON pa.codigo = o.periodo_academico
JOIN FUNDACIONES f ON f.id = o.fundacion_id
ORDER BY pa.codigo DESC, f.nombre;
