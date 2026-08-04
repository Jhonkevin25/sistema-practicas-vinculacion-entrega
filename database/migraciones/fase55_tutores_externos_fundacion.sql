-- ============================================================
-- FASE 55 - TUTORES EXTERNOS POR FUNDACION Y CARRERA
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
-- ============================================================
-- Espejo de fase49_tutores_externos_empresa.sql pero para el proceso de
-- Vinculacion: un tutor externo puede vincularse con una o varias
-- fundaciones y, dentro de cada fundacion, cubrir una o varias carreras.

CREATE TABLE IF NOT EXISTS TUTORES_FUNDACION (
    id SERIAL PRIMARY KEY,
    fundacion_id INT NOT NULL
        REFERENCES FUNDACIONES(id) ON DELETE RESTRICT,
    usuario_id INT
        REFERENCES USUARIOS(id) ON DELETE RESTRICT,
    nombre VARCHAR(200) NOT NULL,
    cargo VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tutores_fundacion_usuario_fundacion
    ON TUTORES_FUNDACION(usuario_id, fundacion_id)
    WHERE usuario_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tutores_fundacion_fundacion_activo
    ON TUTORES_FUNDACION(fundacion_id, activo);

CREATE INDEX IF NOT EXISTS idx_tutores_fundacion_usuario_activo
    ON TUTORES_FUNDACION(usuario_id, activo)
    WHERE usuario_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS TUTORES_FUNDACION_CARRERAS (
    tutor_fundacion_id INT NOT NULL
        REFERENCES TUTORES_FUNDACION(id) ON DELETE CASCADE,
    carrera_id INT NOT NULL
        REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    PRIMARY KEY (tutor_fundacion_id, carrera_id)
);

CREATE INDEX IF NOT EXISTS idx_tutores_fundacion_carreras_carrera
    ON TUTORES_FUNDACION_CARRERAS(carrera_id, tutor_fundacion_id);

-- Validacion informativa. Ambas consultas deben devolver cero para
-- los vinculos administrados por la aplicacion.
SELECT COUNT(*) AS tutores_vinculados_sin_carrera
FROM TUTORES_FUNDACION tf
WHERE tf.usuario_id IS NOT NULL
  AND tf.activo = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM TUTORES_FUNDACION_CARRERAS tfc
      WHERE tfc.tutor_fundacion_id = tf.id
  );

SELECT COUNT(*) AS vinculos_duplicados
FROM (
    SELECT usuario_id, fundacion_id
    FROM TUTORES_FUNDACION
    WHERE usuario_id IS NOT NULL
    GROUP BY usuario_id, fundacion_id
    HAVING COUNT(*) > 1
) duplicados;
