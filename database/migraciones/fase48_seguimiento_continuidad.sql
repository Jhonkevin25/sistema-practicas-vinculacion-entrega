-- Fase 48: seguimiento bidireccional y trazabilidad de continuidad academica.
-- Idempotente y no destructivo. Ejecutar en Supabase SQL Editor antes de
-- desplegar el backend que incorpora estas columnas y la nueva entidad JPA.

BEGIN;

CREATE TABLE IF NOT EXISTS COMENTARIOS_SEGUIMIENTO (
    id              SERIAL PRIMARY KEY,
    practica_id     INT,
    vinculacion_id  INT,
    autor_id        INT NOT NULL,
    audiencia       VARCHAR(20) NOT NULL,
    mensaje         TEXT NOT NULL,
    fecha_creacion  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comentario_seguimiento_practica
        FOREIGN KEY (practica_id) REFERENCES PRACTICAS(id),
    CONSTRAINT fk_comentario_seguimiento_vinculacion
        FOREIGN KEY (vinculacion_id) REFERENCES VINCULACION(id),
    CONSTRAINT fk_comentario_seguimiento_autor
        FOREIGN KEY (autor_id) REFERENCES USUARIOS(id),
    CONSTRAINT chk_comentario_seguimiento_expediente
        CHECK ((practica_id IS NOT NULL)::INT + (vinculacion_id IS NOT NULL)::INT = 1),
    CONSTRAINT chk_comentario_seguimiento_audiencia
        CHECK (audiencia IN ('ESTUDIANTE', 'TUTOR', 'COORDINACION', 'TODOS')),
    CONSTRAINT chk_comentario_seguimiento_mensaje
        CHECK (LENGTH(BTRIM(mensaje)) BETWEEN 5 AND 2000)
);

CREATE INDEX IF NOT EXISTS idx_comentarios_seguimiento_practica_fecha
    ON COMENTARIOS_SEGUIMIENTO(practica_id, fecha_creacion DESC)
    WHERE practica_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_comentarios_seguimiento_vinculacion_fecha
    ON COMENTARIOS_SEGUIMIENTO(vinculacion_id, fecha_creacion DESC)
    WHERE vinculacion_id IS NOT NULL;

ALTER TABLE PRACTICAS
    ADD COLUMN IF NOT EXISTS vacante_id INT,
    ADD COLUMN IF NOT EXISTS tipo_asignacion VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS practica_origen_id INT,
    ADD COLUMN IF NOT EXISTS asignacion_snapshot JSONB;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_practicas_vacante'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT fk_practicas_vacante
            FOREIGN KEY (vacante_id) REFERENCES VACANTES_PRACTICAS(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_practicas_origen'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT fk_practicas_origen
            FOREIGN KEY (practica_origen_id) REFERENCES PRACTICAS(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_practicas_tipo_asignacion'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT chk_practicas_tipo_asignacion
            CHECK (tipo_asignacion IN ('LEGACY', 'MERITOCRACIA', 'DIRECTA', 'CONTINUIDAD'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_practicas_origen_distinto'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT chk_practicas_origen_distinto
            CHECK (practica_origen_id IS NULL OR practica_origen_id <> id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_practicas_continuidad_origen'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT chk_practicas_continuidad_origen
            CHECK (
                (tipo_asignacion = 'CONTINUIDAD' AND practica_origen_id IS NOT NULL)
                OR (tipo_asignacion <> 'CONTINUIDAD' AND practica_origen_id IS NULL)
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_practicas_vacante
    ON PRACTICAS(vacante_id)
    WHERE vacante_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_practicas_origen
    ON PRACTICAS(practica_origen_id)
    WHERE practica_origen_id IS NOT NULL;

COMMIT;

-- Validacion: ambas consultas deben devolver 0.
SELECT COUNT(*) AS comentarios_invalidos
FROM COMENTARIOS_SEGUIMIENTO
WHERE ((practica_id IS NOT NULL)::INT + (vinculacion_id IS NOT NULL)::INT) <> 1
   OR audiencia NOT IN ('ESTUDIANTE', 'TUTOR', 'COORDINACION', 'TODOS')
   OR LENGTH(BTRIM(mensaje)) NOT BETWEEN 5 AND 2000;

SELECT COUNT(*) AS asignaciones_invalidas
FROM PRACTICAS
WHERE tipo_asignacion NOT IN ('LEGACY', 'MERITOCRACIA', 'DIRECTA', 'CONTINUIDAD')
   OR practica_origen_id = id
   OR (tipo_asignacion = 'CONTINUIDAD') <> (practica_origen_id IS NOT NULL);
