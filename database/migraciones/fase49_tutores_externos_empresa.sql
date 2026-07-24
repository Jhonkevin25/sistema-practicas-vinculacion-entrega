-- ============================================================
-- FASE 49 - TUTORES EXTERNOS POR EMPRESA Y CARRERA
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
-- ============================================================
-- Un tutor externo puede vincularse con una o varias empresas y,
-- dentro de cada empresa, cubrir una o varias carreras. Las filas
-- historicas de TUTORES_EMPRESA que solo tenian nombre/cargo se
-- conservan, pero no se consideran asignables hasta enlazarlas con
-- una cuenta TUTOR mediante usuario_id.

ALTER TABLE TUTORES_EMPRESA
    ADD COLUMN IF NOT EXISTS usuario_id INT,
    ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_tutores_empresa_usuario'
          AND conrelid = 'tutores_empresa'::regclass
    ) THEN
        ALTER TABLE TUTORES_EMPRESA
            ADD CONSTRAINT fk_tutores_empresa_usuario
            FOREIGN KEY (usuario_id) REFERENCES USUARIOS(id) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_tutores_empresa_usuario_empresa
    ON TUTORES_EMPRESA(usuario_id, empresa_id)
    WHERE usuario_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tutores_empresa_empresa_activo
    ON TUTORES_EMPRESA(empresa_id, activo);

CREATE INDEX IF NOT EXISTS idx_tutores_empresa_usuario_activo
    ON TUTORES_EMPRESA(usuario_id, activo)
    WHERE usuario_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS TUTORES_EMPRESA_CARRERAS (
    tutor_empresa_id INT NOT NULL
        REFERENCES TUTORES_EMPRESA(id) ON DELETE CASCADE,
    carrera_id INT NOT NULL
        REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    PRIMARY KEY (tutor_empresa_id, carrera_id)
);

CREATE INDEX IF NOT EXISTS idx_tutores_empresa_carreras_carrera
    ON TUTORES_EMPRESA_CARRERAS(carrera_id, tutor_empresa_id);

-- Validacion informativa. Ambas consultas deben devolver cero para
-- los vinculos administrados por la aplicacion.
SELECT COUNT(*) AS tutores_vinculados_sin_carrera
FROM TUTORES_EMPRESA te
WHERE te.usuario_id IS NOT NULL
  AND te.activo = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM TUTORES_EMPRESA_CARRERAS tec
      WHERE tec.tutor_empresa_id = te.id
  );

SELECT COUNT(*) AS vinculos_duplicados
FROM (
    SELECT usuario_id, empresa_id
    FROM TUTORES_EMPRESA
    WHERE usuario_id IS NOT NULL
    GROUP BY usuario_id, empresa_id
    HAVING COUNT(*) > 1
) duplicados;
