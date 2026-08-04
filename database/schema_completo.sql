-- ============================================================================
-- SCHEMA COMPLETO — Sistema de Prácticas y Vinculación (UNIBE)
-- Consolidado generado el 2026-07-17 concatenando, en orden de aplicación,
-- schema.sql y las migraciones de database/migraciones/.
-- Todos los scripts son idempotentes: puede ejecutarse completo sobre una
-- base vacía (o re-ejecutarse) en el SQL Editor de Supabase/PostgreSQL.
-- ============================================================================


-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: schema.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- SCHEMA.SQL — Sistema de Prácticas y Vinculación UNIBE
-- PostgreSQL 15 — Supabase
-- ============================================================

-- ============================================================
-- BLOQUE 1: SEGURIDAD BASE
-- ============================================================

CREATE TABLE IF NOT EXISTS USUARIOS (
    id              SERIAL PRIMARY KEY,
    cedula          VARCHAR(20) UNIQUE NOT NULL,
    nombre          VARCHAR(100) NOT NULL,
    apellido        VARCHAR(100) NOT NULL,
    email           VARCHAR(150) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    activo          BOOLEAN DEFAULT TRUE,
    primer_login    BOOLEAN DEFAULT TRUE,
    intentos_login_fallidos INTEGER NOT NULL DEFAULT 0,
    bloqueado_hasta TIMESTAMP,
    fuente          VARCHAR(30) DEFAULT 'MANUAL'
                    CHECK (fuente IN ('MANUAL', 'CSV_UNIVERSIDAD', 'API_UNIVERSIDAD')),
    external_id     VARCHAR(120),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_usuarios_intentos_login_no_negativos
        CHECK (intentos_login_fallidos >= 0)
);

CREATE TABLE IF NOT EXISTS ROLES (
    id      SERIAL PRIMARY KEY,
    codigo  VARCHAR(20) UNIQUE NOT NULL,
    nombre  VARCHAR(100) NOT NULL,
    activo  BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS MODULOS (
    id          SERIAL PRIMARY KEY,
    codigo      VARCHAR(20) UNIQUE NOT NULL,
    nombre      VARCHAR(100) NOT NULL,
    ruta_base   VARCHAR(200),
    icono       VARCHAR(50),
    orden       INT
);

CREATE TABLE IF NOT EXISTS PERMISOS (
    id      SERIAL PRIMARY KEY,
    codigo  VARCHAR(20) NOT NULL,
    nombre  VARCHAR(50) NOT NULL
);

-- ============================================================
-- BLOQUE 2: TABLAS INTERMEDIAS DE SEGURIDAD
-- ============================================================

CREATE TABLE IF NOT EXISTS USUARIOS_ROLES (
    id              SERIAL PRIMARY KEY,
    usuario_id      INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    rol_id          INT NOT NULL REFERENCES ROLES(id) ON DELETE CASCADE,
    asignado_por    INT REFERENCES USUARIOS(id),
    fecha_asignacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ROLES_MODULOS_PERMISOS (
    id          SERIAL PRIMARY KEY,
    rol_id      INT NOT NULL REFERENCES ROLES(id) ON DELETE CASCADE,
    modulo_id   INT NOT NULL REFERENCES MODULOS(id) ON DELETE CASCADE,
    permiso_id  INT NOT NULL REFERENCES PERMISOS(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS SESIONES (
    id          SERIAL PRIMARY KEY,
    usuario_id  INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    token       VARCHAR(500) NOT NULL,
    ip          VARCHAR(45),
    expires_at  TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS TOKENS_RECUPERACION (
    id                  SERIAL PRIMARY KEY,
    usuario_id          INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    token_hash          VARCHAR(128) NOT NULL UNIQUE,
    fecha_expiracion    TIMESTAMP NOT NULL,
    usado               BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usado_en            TIMESTAMP,
    CHECK (fecha_expiracion > fecha_creacion)
);

CREATE TABLE IF NOT EXISTS AUDITORIA (
    id              BIGSERIAL PRIMARY KEY,
    tabla_afectada  VARCHAR(100) NOT NULL,
    accion          VARCHAR(50) NOT NULL,
    datos_antes     JSONB,
    datos_despues   JSONB,
    usuario_id      INT REFERENCES USUARIOS(id),
    fecha           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- BLOQUE 3: PRÁCTICAS Y VINCULACIÓN
-- ============================================================

CREATE TABLE IF NOT EXISTS ESTUDIANTES (
    id                  SERIAL PRIMARY KEY,
    usuario_id          INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    matricula           VARCHAR(20) UNIQUE NOT NULL,
    carrera             VARCHAR(200) NOT NULL,
    semestre            INT,
    periodo_academico   VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS EMPRESAS (
    id                  SERIAL PRIMARY KEY,
    ruc                 VARCHAR(13) UNIQUE NOT NULL,
    nombre              VARCHAR(200) NOT NULL,
    direccion           TEXT,
    cupos_disponibles   INT DEFAULT 0,
    activo              BOOLEAN DEFAULT TRUE,
    tiene_convenio      BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS TUTORES_EMPRESA (
    id          SERIAL PRIMARY KEY,
    empresa_id  INT NOT NULL REFERENCES EMPRESAS(id) ON DELETE CASCADE,
    nombre      VARCHAR(200) NOT NULL,
    cargo       VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS PRACTICAS (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id),
    empresa_id          INT NOT NULL REFERENCES EMPRESAS(id),
    tutor_id            INT REFERENCES USUARIOS(id),
    encargado_id        INT REFERENCES USUARIOS(id),
    estado              VARCHAR(30) DEFAULT 'pendiente',
    horas_requeridas    INT NOT NULL,
    horas_completadas   INT NOT NULL DEFAULT 0,
    fecha_inicio        DATE,
    fecha_fin           DATE,
    periodo_academico   VARCHAR(20),
    cerrado_por         INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    cerrado_en          TIMESTAMP,
    cierre_snapshot     JSONB,
    motivo_finalizacion TEXT,
    CONSTRAINT chk_practicas_estado
        CHECK (estado IN ('pendiente', 'en_curso', 'completado', 'reprobado', 'retirado')),
    CONSTRAINT chk_practicas_finalizacion_excepcional
        CHECK (
            estado NOT IN ('reprobado', 'retirado')
            OR (
                motivo_finalizacion IS NOT NULL
                AND CHAR_LENGTH(BTRIM(motivo_finalizacion)) >= 10
                AND cerrado_en IS NOT NULL
                AND cerrado_por IS NOT NULL
                AND cierre_snapshot IS NOT NULL
            )
        ),
    CONSTRAINT chk_practicas_horas_validas
        CHECK (
            horas_requeridas > 0
            AND horas_completadas BETWEEN 0 AND horas_requeridas
        )
);

CREATE TABLE IF NOT EXISTS EVALUACIONES_PRACTICAS (
    id              SERIAL PRIMARY KEY,
    practica_id     INT NOT NULL REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    tutor_id        INT REFERENCES USUARIOS(id),
    nota            DECIMAL(5,2),
    observaciones   TEXT,
    fecha_evaluacion DATE
);

CREATE TABLE IF NOT EXISTS DOCUMENTOS_PRACTICAS (
    id              SERIAL PRIMARY KEY,
    practica_id     INT NOT NULL REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    tipo_documento  VARCHAR(100) NOT NULL,
    url_archivo     VARCHAR(300),
    fecha_subida    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS DOCUMENTOS_REQUERIDOS (
    id                  SERIAL PRIMARY KEY,
    proceso             VARCHAR(30) NOT NULL CHECK (proceso IN ('GENERAL', 'PRACTICAS', 'VINCULACION')),
    carrera             VARCHAR(150),
    etapa               VARCHAR(80),
    tipo_documento      VARCHAR(80) NOT NULL,
    nombre              VARCHAR(180) NOT NULL,
    descripcion         TEXT,
    obligatorio         BOOLEAN NOT NULL DEFAULT TRUE,
    momento             VARCHAR(40) NOT NULL DEFAULT 'PRE_POSTULACION'
                        CHECK (momento IN ('PRE_POSTULACION', 'POST_ACEPTACION', 'CIERRE')),
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    solicitado_por      INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS DOCS_ESTUDIANTE (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    requerido_id        INT REFERENCES DOCUMENTOS_REQUERIDOS(id) ON DELETE SET NULL,
    tipo_documento      VARCHAR(100) NOT NULL,
    url_archivo         VARCHAR(300),
    proceso             VARCHAR(30) DEFAULT 'GENERAL' CHECK (proceso IN ('GENERAL', 'PRACTICAS', 'VINCULACION')),
    carrera             VARCHAR(150),
    etapa               VARCHAR(80),
    estado              VARCHAR(30) DEFAULT 'cargado'
                        CHECK (estado IN ('pendiente', 'cargado', 'en_revision', 'aprobado', 'rechazado', 'requiere_correccion')),
    observacion         TEXT,
    revisado_por        INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    fecha_revision      TIMESTAMP,
    fecha_subida        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS IMPORTACIONES (
    id              BIGSERIAL PRIMARY KEY,
    archivo_nombre  VARCHAR(255) NOT NULL,
    tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('ESTUDIANTES', 'NOTAS')),
    filas_total     INT NOT NULL DEFAULT 0 CHECK (filas_total >= 0),
    filas_ok        INT NOT NULL DEFAULT 0 CHECK (filas_ok >= 0),
    filas_error     INT NOT NULL DEFAULT 0 CHECK (filas_error >= 0),
    creados         INT NOT NULL DEFAULT 0 CHECK (creados >= 0),
    actualizados    INT NOT NULL DEFAULT 0 CHECK (actualizados >= 0),
    enlazados       INT NOT NULL DEFAULT 0 CHECK (enlazados >= 0),
    estado          VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA'
                    CHECK (estado IN ('COMPLETADA', 'CON_ERRORES', 'FALLIDA')),
    detalle_errores JSONB NOT NULL DEFAULT '[]'::jsonb,
    usuario_id      INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    fecha           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (filas_ok + filas_error = filas_total)
);

CREATE TABLE IF NOT EXISTS FUNDACIONES (
    id                      SERIAL PRIMARY KEY,
    ruc                     VARCHAR(13) UNIQUE NOT NULL,
    nombre                  VARCHAR(200) NOT NULL,
    mision                  TEXT,
    area_intervencion       VARCHAR(200),
    activa                  BOOLEAN DEFAULT TRUE,
    tiene_convenio          BOOLEAN DEFAULT FALSE
);

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

CREATE TABLE IF NOT EXISTS CARRERAS (
    id          SERIAL PRIMARY KEY,
    nombre      VARCHAR(200) NOT NULL UNIQUE,
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS PERIODOS_ACADEMICOS (
    id              SERIAL PRIMARY KEY,
    codigo          VARCHAR(20) NOT NULL UNIQUE,
    fecha_inicio    DATE NOT NULL,
    fecha_fin       DATE NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'PLANIFICADO',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_periodos_codigo CHECK (codigo ~ '^[0-9]{4}-[12]$'),
    CONSTRAINT chk_periodos_fechas CHECK (fecha_inicio <= fecha_fin),
    CONSTRAINT chk_periodos_estado CHECK (estado IN ('PLANIFICADO', 'ACTIVO', 'CERRADO'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_periodos_unico_activo
    ON PERIODOS_ACADEMICOS ((estado)) WHERE estado = 'ACTIVO';
CREATE INDEX IF NOT EXISTS idx_periodos_estado_fechas
    ON PERIODOS_ACADEMICOS(estado, fecha_inicio, fecha_fin);

CREATE TABLE IF NOT EXISTS OFERTAS_CUPOS_EMPRESA (
    id                  SERIAL PRIMARY KEY,
    empresa_id          INT NOT NULL REFERENCES EMPRESAS(id) ON DELETE RESTRICT,
    periodo_academico   VARCHAR(20) NOT NULL REFERENCES PERIODOS_ACADEMICOS(codigo) ON DELETE RESTRICT,
    distribucion        VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    cupos_totales       INT NOT NULL DEFAULT 0,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    observacion         TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oferta_cupos_empresa_periodo UNIQUE (empresa_id, periodo_academico),
    CONSTRAINT chk_oferta_cupos_distribucion CHECK (distribucion IN ('GENERAL', 'POR_CARRERA')),
    CONSTRAINT chk_oferta_cupos_totales_no_negativos CHECK (cupos_totales >= 0),
    CONSTRAINT chk_oferta_cupos_periodo_no_vacio CHECK (BTRIM(periodo_academico) <> '')
);

CREATE TABLE IF NOT EXISTS OFERTAS_CUPOS_EMPRESA_CARRERAS (
    id          SERIAL PRIMARY KEY,
    oferta_id   INT NOT NULL REFERENCES OFERTAS_CUPOS_EMPRESA(id) ON DELETE CASCADE,
    carrera_id  INT NOT NULL REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    cupos       INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_oferta_cupos_empresa_carrera UNIQUE (oferta_id, carrera_id),
    CONSTRAINT chk_oferta_carrera_cupos_positivos CHECK (cupos > 0)
);

CREATE INDEX IF NOT EXISTS idx_ofertas_cupos_empresa_periodo
    ON OFERTAS_CUPOS_EMPRESA(periodo_academico, empresa_id, activo);
CREATE INDEX IF NOT EXISTS idx_ofertas_cupos_carreras_oferta
    ON OFERTAS_CUPOS_EMPRESA_CARRERAS(oferta_id, carrera_id);
CREATE INDEX IF NOT EXISTS idx_convenios_carreras_carrera
    ON CONVENIOS_CARRERAS(carrera);

CREATE TABLE IF NOT EXISTS OFERTAS_CUPOS_FUNDACION (
    id                  SERIAL PRIMARY KEY,
    fundacion_id        INT NOT NULL REFERENCES FUNDACIONES(id) ON DELETE RESTRICT,
    periodo_academico   VARCHAR(20) NOT NULL REFERENCES PERIODOS_ACADEMICOS(codigo) ON DELETE RESTRICT,
    distribucion        VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    cupos_totales       INT NOT NULL DEFAULT 0,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    observacion         TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oferta_cupos_fundacion_periodo UNIQUE (fundacion_id, periodo_academico),
    CONSTRAINT chk_oferta_fundacion_distribucion CHECK (distribucion IN ('GENERAL', 'POR_CARRERA')),
    CONSTRAINT chk_oferta_fundacion_cupos_no_negativos CHECK (cupos_totales >= 0),
    CONSTRAINT chk_oferta_fundacion_periodo_no_vacio CHECK (BTRIM(periodo_academico) <> '')
);

CREATE TABLE IF NOT EXISTS OFERTAS_CUPOS_FUNDACION_CARRERAS (
    id          SERIAL PRIMARY KEY,
    oferta_id   INT NOT NULL REFERENCES OFERTAS_CUPOS_FUNDACION(id) ON DELETE CASCADE,
    carrera_id  INT NOT NULL REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    cupos       INT NOT NULL,
    CONSTRAINT uq_oferta_cupos_fundacion_carrera UNIQUE (oferta_id, carrera_id),
    CONSTRAINT chk_oferta_fundacion_carrera_cupos_positivos CHECK (cupos > 0)
);

CREATE INDEX IF NOT EXISTS idx_ofertas_fundacion_periodo
    ON OFERTAS_CUPOS_FUNDACION(periodo_academico, fundacion_id, activo);
CREATE INDEX IF NOT EXISTS idx_ofertas_fundacion_carreras_oferta
    ON OFERTAS_CUPOS_FUNDACION_CARRERAS(oferta_id, carrera_id);

CREATE TABLE IF NOT EXISTS PROYECTOS (
    id                  SERIAL PRIMARY KEY,
    fundacion_id        INT NOT NULL REFERENCES FUNDACIONES(id),
    nombre              VARCHAR(200) NOT NULL,
    descripcion         TEXT,
    cupos_totales       INT NOT NULL DEFAULT 0,
    cupos_disponibles   INT NOT NULL DEFAULT 0,
    horas_requeridas    INT NOT NULL DEFAULT 160,
    ciudad              VARCHAR(150) NOT NULL DEFAULT 'Por definir',
    modalidad           VARCHAR(20) NOT NULL DEFAULT 'NO_ESPECIFICADA',
    estado              BOOLEAN DEFAULT TRUE,
    periodo             VARCHAR(20) NOT NULL REFERENCES PERIODOS_ACADEMICOS(codigo) ON DELETE RESTRICT,
    CONSTRAINT chk_proyectos_cupos_no_negativos
        CHECK (cupos_disponibles >= 0),
    CONSTRAINT chk_proyectos_cupos_totales_no_negativos
        CHECK (cupos_totales >= 0),
    CONSTRAINT chk_proyectos_cupos_consistentes
        CHECK (cupos_disponibles <= cupos_totales),
    CONSTRAINT chk_proyectos_horas_requeridas_positivas
        CHECK (horas_requeridas > 0),
    CONSTRAINT chk_proyectos_ciudad_no_vacia
        CHECK (BTRIM(ciudad) <> ''),
    CONSTRAINT chk_proyectos_modalidad
        CHECK (modalidad IN ('PRESENCIAL', 'VIRTUAL', 'HIBRIDA', 'NO_ESPECIFICADA'))
);

CREATE INDEX IF NOT EXISTS idx_proyectos_fundacion_periodo
    ON PROYECTOS(fundacion_id, periodo);

CREATE TABLE IF NOT EXISTS PROYECTOS_CARRERAS (
    id                  SERIAL PRIMARY KEY,
    proyecto_id         INT NOT NULL REFERENCES PROYECTOS(id) ON DELETE CASCADE,
    carrera_id          INT NOT NULL REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    cupos_totales       INT,
    cupos_disponibles   INT,
    CONSTRAINT uq_proyecto_carrera UNIQUE (proyecto_id, carrera_id),
    CONSTRAINT chk_proyecto_carrera_cupos CHECK (
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

CREATE TABLE IF NOT EXISTS VINCULACION (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id),
    fundacion_id        INT NOT NULL REFERENCES FUNDACIONES(id),
    proyecto_id         INT NOT NULL REFERENCES PROYECTOS(id),
    tutor_id            INT REFERENCES USUARIOS(id),
    encargado_id        INT REFERENCES USUARIOS(id),
    estado              VARCHAR(30) DEFAULT 'pendiente',
    horas_requeridas    INT NOT NULL,
    horas_completadas   INT NOT NULL DEFAULT 0,
    fecha_inicio        DATE,
    fecha_fin           DATE,
    periodo_academico   VARCHAR(20),
    cerrado_por         INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    cerrado_en          TIMESTAMP,
    cierre_snapshot     JSONB,
    motivo_finalizacion TEXT,
    cupo_liberado           BOOLEAN NOT NULL DEFAULT FALSE,
    cupo_liberado_por       INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    cupo_liberado_en        TIMESTAMP,
    motivo_liberacion_cupo  TEXT,
    CONSTRAINT chk_vinculacion_estado
        CHECK (estado IN ('pendiente', 'en_curso', 'completado', 'reprobado', 'retirado')),
    CONSTRAINT chk_vinculacion_finalizacion_excepcional
        CHECK (
            estado NOT IN ('reprobado', 'retirado')
            OR (
                motivo_finalizacion IS NOT NULL
                AND CHAR_LENGTH(BTRIM(motivo_finalizacion)) >= 10
                AND cerrado_en IS NOT NULL
                AND cerrado_por IS NOT NULL
                AND cierre_snapshot IS NOT NULL
            )
        ),
    CONSTRAINT chk_vinculacion_horas_validas
        CHECK (
            horas_requeridas > 0
            AND horas_completadas BETWEEN 0 AND horas_requeridas
        ),
    -- Fase 42: liberación única del cupo de una vinculación retirada
    CONSTRAINT chk_vinculacion_liberacion_cupo
        CHECK (
            cupo_liberado = FALSE
            OR (
                estado = 'retirado'
                AND motivo_liberacion_cupo IS NOT NULL
                AND CHAR_LENGTH(BTRIM(motivo_liberacion_cupo)) >= 10
                AND cupo_liberado_por IS NOT NULL
                AND cupo_liberado_en IS NOT NULL
            )
        )
);

CREATE TABLE IF NOT EXISTS POSTULACIONES_VINCULACION (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    proyecto_id         INT NOT NULL REFERENCES PROYECTOS(id) ON DELETE RESTRICT,
    estado              VARCHAR(30) NOT NULL DEFAULT 'Pendiente'
                        CONSTRAINT chk_postulacion_vinculacion_estado
                        CHECK (estado IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo', 'Expirada')),
    periodo_academico   VARCHAR(20),
    vinculacion_id      INT REFERENCES VINCULACION(id) ON DELETE SET NULL,
    observacion         TEXT,
    aprobado_por        INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    aprobado_en         TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS EVAL_VINCULACION (
    id                  SERIAL PRIMARY KEY,
    vinculacion_id      INT NOT NULL REFERENCES VINCULACION(id) ON DELETE CASCADE,
    nota                DECIMAL(5,2),
    observaciones       TEXT,
    fecha_evaluacion    DATE
);

CREATE TABLE IF NOT EXISTS DOCS_VINCULACION (
    id              SERIAL PRIMARY KEY,
    vinculacion_id  INT NOT NULL REFERENCES VINCULACION(id) ON DELETE CASCADE,
    tipo_documento  VARCHAR(100) NOT NULL,
    url_archivo     VARCHAR(300),
    fecha_subida    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- BLOQUE 5: NUEVAS TABLAS DE MERITOCRACIA Y OPERACIONES UAT
-- ============================================================

CREATE TABLE IF NOT EXISTS VACANTES_PRACTICAS (
    id                  SERIAL PRIMARY KEY,
    nombre              VARCHAR(200) NOT NULL,
    empresa_id          INT NOT NULL REFERENCES EMPRESAS(id) ON DELETE CASCADE,
    cupos               INT DEFAULT 0 CHECK (cupos >= 0),
    horas               INT DEFAULT 240,
    descripcion         TEXT,
    carrera             VARCHAR(200) NOT NULL,
    modalidad_academica VARCHAR(50) NOT NULL CHECK (modalidad_academica IN ('Vinculación', 'Práctica I', 'Práctica II')),
    area                VARCHAR(100) NOT NULL,
    ciudad              VARCHAR(100) NOT NULL,
    tipo_empresa        VARCHAR(50) NOT NULL CHECK (tipo_empresa IN ('Pública', 'Privada')),
    modalidad_trabajo   VARCHAR(50) NOT NULL CHECK (modalidad_trabajo IN ('Presencial', 'Híbrida', 'Virtual')),
    fecha_limite        DATE,
    alta_demanda        BOOLEAN DEFAULT FALSE,
    requisitos          TEXT,
    periodo_academico   VARCHAR(20),
    -- Fase 42: pausa/reactivación sin devolver cupos a la empresa
    activa              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS POSTULACIONES_MERITOCRATICAS (
    id                      SERIAL PRIMARY KEY,
    estudiante_id           INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    promedio                DECIMAL(4,2) NOT NULL,
    pref1_id                INT NOT NULL REFERENCES VACANTES_PRACTICAS(id) ON DELETE CASCADE,
    pref2_id                INT NOT NULL REFERENCES VACANTES_PRACTICAS(id) ON DELETE CASCADE,
    pref3_id                INT REFERENCES VACANTES_PRACTICAS(id) ON DELETE SET NULL,
    score                   DECIMAL(4,2) NOT NULL,
    estado                  VARCHAR(30) DEFAULT 'Pendiente'
                            CONSTRAINT chk_postulacion_merito_estado
                            CHECK (estado IN ('Pendiente','Procesado','Aprobado','Rechazado','Expirada')),
    asignado_empresa_id     INT REFERENCES EMPRESAS(id),
    asignado_vacante_id     INT REFERENCES VACANTES_PRACTICAS(id) ON DELETE SET NULL,
    ajustado_manualmente    BOOLEAN DEFAULT FALSE,
    justificacion_ajuste    TEXT,
    CHECK (
        pref1_id IS NOT NULL
        AND pref2_id IS NOT NULL
        AND pref1_id <> pref2_id
        AND (pref3_id IS NULL OR (pref3_id <> pref1_id AND pref3_id <> pref2_id))
    )
);

CREATE TABLE IF NOT EXISTS BITACORAS (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    practica_id         INT REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    vinculacion_id      INT REFERENCES VINCULACION(id) ON DELETE CASCADE,
    parcial             INT NOT NULL CHECK (parcial IN (1, 2, 3)),
    fecha               DATE NOT NULL,
    fecha_registro      DATE DEFAULT CURRENT_DATE,
    actividad           TEXT NOT NULL,
    horas               INT NOT NULL CHECK (horas BETWEEN 1 AND 24),
    horas_extra         INT NOT NULL DEFAULT 0 CHECK (horas_extra >= 0),
    observaciones       TEXT,
    estado              VARCHAR(30) DEFAULT 'pendiente'
                        CHECK (estado IN ('pendiente', 'aprobada', 'rechazada', 'requiere_correccion')),
    observaciones_tutor TEXT,
    revisado_por        INT REFERENCES USUARIOS(id),
    fecha_revision      TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (practica_id IS NOT NULL AND vinculacion_id IS NULL)
        OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
    )
);
-- Nota: fecha_registro y horas_extra ya incluyen la migracion fase53
-- (database/migraciones/fase53_bitacora_horas_extra_fecha_registro.sql).
-- En una base ya existente, fecha_registro debe recibir backfill
-- (fecha_registro = fecha) para las filas creadas antes de fase53: ver el
-- script de la migracion, que hace ese UPDATE explicito.

CREATE TABLE IF NOT EXISTS ASISTENCIAS (
    id              SERIAL PRIMARY KEY,
    estudiante_id   INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    practica_id     INT REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    vinculacion_id  INT REFERENCES VINCULACION(id) ON DELETE CASCADE,
    fecha           DATE NOT NULL,
    hora_ingreso    VARCHAR(8) DEFAULT '08:00',
    hora_salida     VARCHAR(8) DEFAULT '12:00',
    estado          VARCHAR(20) CHECK (estado IN ('Presente', 'Atraso', 'Falta')),
    observaciones   TEXT,
    CONSTRAINT chk_asistencias_expediente_unico CHECK (
        (practica_id IS NOT NULL AND vinculacion_id IS NULL)
        OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS EVALUACIONES_PRACTICAS_DETALLE (
    id              SERIAL PRIMARY KEY,
    practica_id     INT REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    vinculacion_id  INT REFERENCES VINCULACION(id) ON DELETE CASCADE,
    parcial         INT CHECK (parcial IN (1, 2, 3)),
    nota_tutor      DECIMAL(4,2) DEFAULT 0.00 CHECK (nota_tutor BETWEEN 0 AND 10),
    nota_coord      DECIMAL(4,2) DEFAULT 0.00 CHECK (nota_coord BETWEEN 0 AND 10),
    nota_final      DECIMAL(4,2) DEFAULT 0.00 CHECK (nota_final BETWEEN 0 AND 10),
    encuesta_completada BOOLEAN DEFAULT FALSE,
    CHECK (
        (practica_id IS NOT NULL AND vinculacion_id IS NULL)
        OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS ENCUESTAS_SATISFACCION (
    id                              SERIAL PRIMARY KEY,
    estudiante_id                   INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    practica_id                     INT REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    vinculacion_id                  INT REFERENCES VINCULACION(id) ON DELETE CASCADE,
    parcial                         INT NOT NULL CHECK (parcial IN (1, 2, 3)),
    satisfaccion_tutor              INT NOT NULL CHECK (satisfaccion_tutor BETWEEN 1 AND 5),
    satisfaccion_empresa_proyecto   INT NOT NULL CHECK (satisfaccion_empresa_proyecto BETWEEN 1 AND 5),
    relacion_carrera                INT NOT NULL CHECK (relacion_carrera BETWEEN 1 AND 5),
    claridad_instrucciones          INT NOT NULL CHECK (claridad_instrucciones BETWEEN 1 AND 5),
    comentario                      TEXT,
    fecha_envio                     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (practica_id IS NOT NULL AND vinculacion_id IS NULL)
        OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS FECHAS_LIMITE_CALIFICACION (
    id                  SERIAL PRIMARY KEY,
    periodo_academico   VARCHAR(20) NOT NULL,
    parcial             INT CHECK (parcial IN (1, 2, 3)),
    fecha_limite        DATE NOT NULL,
    UNIQUE(periodo_academico, parcial)
);

CREATE TABLE IF NOT EXISTS FAVORITOS_VACANTES (
    id              SERIAL PRIMARY KEY,
    estudiante_id   INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    vacante_id      INT NOT NULL REFERENCES VACANTES_PRACTICAS(id) ON DELETE CASCADE,
    UNIQUE(estudiante_id, vacante_id)
);

-- ============================================================
-- TRIGGERS DE AUDITORÍA
-- ============================================================

CREATE OR REPLACE FUNCTION fn_auditoria()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO AUDITORIA(tabla_afectada, accion, datos_antes, datos_despues)
        VALUES (TG_TABLE_NAME, 'INSERT', NULL, row_to_json(NEW)::jsonb);
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO AUDITORIA(tabla_afectada, accion, datos_antes, datos_despues)
        VALUES (TG_TABLE_NAME, 'UPDATE', row_to_json(OLD)::jsonb, row_to_json(NEW)::jsonb);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO AUDITORIA(tabla_afectada, accion, datos_antes, datos_despues)
        VALUES (TG_TABLE_NAME, 'DELETE', row_to_json(OLD)::jsonb, NULL);
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_auditoria_usuarios
    AFTER INSERT OR UPDATE OR DELETE ON USUARIOS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_practicas
    AFTER INSERT OR UPDATE OR DELETE ON PRACTICAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_vinculacion
    AFTER INSERT OR UPDATE OR DELETE ON VINCULACION
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_postulaciones_meritocraticas
    AFTER INSERT OR UPDATE OR DELETE ON POSTULACIONES_MERITOCRATICAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_convenios
    AFTER INSERT OR UPDATE OR DELETE ON CONVENIOS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

-- ============================================================
-- BLOQUE 6: ACTUALIZACIÓN v2 — Correcciones Auditoría ISO 25010
-- ============================================================

-- D5: Agregar columna perfil_requerido a VACANTES_PRACTICAS
ALTER TABLE VACANTES_PRACTICAS
ADD COLUMN IF NOT EXISTS perfil_requerido TEXT;

-- D6: Nueva tabla FECHAS_CONVOCATORIA
CREATE TABLE IF NOT EXISTS FECHAS_CONVOCATORIA (
    id                          SERIAL PRIMARY KEY,
    periodo_academico           VARCHAR(20) NOT NULL REFERENCES PERIODOS_ACADEMICOS(codigo) ON DELETE RESTRICT,
    tipo                        VARCHAR(30) NOT NULL CHECK (tipo IN ('PRACTICAS', 'VINCULACION')),
    convocatoria_inicio         DATE NOT NULL,
    convocatoria_fin            DATE NOT NULL,
    fecha_limite_documentos     DATE NOT NULL,
    fecha_inicio_postulacion    DATE NOT NULL,
    creado_por                  INT REFERENCES USUARIOS(id),
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        convocatoria_inicio <= fecha_limite_documentos
        AND fecha_limite_documentos <= fecha_inicio_postulacion
        AND fecha_inicio_postulacion <= convocatoria_fin
    ),
    UNIQUE(periodo_academico, tipo)
);

-- D7: Índices de rendimiento
CREATE INDEX IF NOT EXISTS idx_estudiantes_carrera ON ESTUDIANTES(carrera);
CREATE INDEX IF NOT EXISTS idx_practicas_estado ON PRACTICAS(estado);
CREATE INDEX IF NOT EXISTS idx_practicas_estudiante ON PRACTICAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_vinculacion_estado ON VINCULACION(estado);
CREATE INDEX IF NOT EXISTS idx_vinculacion_estudiante ON VINCULACION(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_practicas_cerrado_en ON PRACTICAS(cerrado_en) WHERE cerrado_en IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_vinculacion_cerrado_en ON VINCULACION(cerrado_en) WHERE cerrado_en IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_practicas_estudiante_activa
    ON PRACTICAS(estudiante_id) WHERE estado IN ('pendiente', 'en_curso');
CREATE UNIQUE INDEX IF NOT EXISTS ux_vinculacion_estudiante_activa
    ON VINCULACION(estudiante_id) WHERE estado IN ('pendiente', 'en_curso');
CREATE INDEX IF NOT EXISTS idx_practicas_estudiante_periodo ON PRACTICAS(estudiante_id, periodo_academico);
CREATE INDEX IF NOT EXISTS idx_vinculacion_estudiante_periodo ON VINCULACION(estudiante_id, periodo_academico);
CREATE INDEX IF NOT EXISTS idx_bitacoras_estudiante ON BITACORAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_bitacoras_practica ON BITACORAS(practica_id);
CREATE INDEX IF NOT EXISTS idx_bitacoras_vinculacion ON BITACORAS(vinculacion_id);
CREATE INDEX IF NOT EXISTS idx_bitacoras_estado ON BITACORAS(estado);
CREATE INDEX IF NOT EXISTS idx_bitacoras_parcial ON BITACORAS(parcial);
CREATE INDEX IF NOT EXISTS idx_asistencias_estudiante ON ASISTENCIAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_asistencias_practica ON ASISTENCIAS(practica_id);
CREATE INDEX IF NOT EXISTS idx_asistencias_vinculacion ON ASISTENCIAS(vinculacion_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencias_practica_fecha
    ON ASISTENCIAS(practica_id, fecha) WHERE practica_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencias_vinculacion_fecha
    ON ASISTENCIAS(vinculacion_id, fecha) WHERE vinculacion_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_postulaciones_estudiante ON POSTULACIONES_MERITOCRATICAS(estudiante_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_postulacion_vinculacion_activa_estudiante ON POSTULACIONES_VINCULACION(estudiante_id) WHERE estado = 'Pendiente';
CREATE UNIQUE INDEX IF NOT EXISTS uq_postulacion_vinculacion_vinculacion ON POSTULACIONES_VINCULACION(vinculacion_id) WHERE vinculacion_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_postulaciones_vinculacion_proyecto ON POSTULACIONES_VINCULACION(proyecto_id);
CREATE INDEX IF NOT EXISTS idx_postulaciones_vinculacion_estado ON POSTULACIONES_VINCULACION(estado);
CREATE UNIQUE INDEX IF NOT EXISTS uq_documentos_requeridos_definicion ON DOCUMENTOS_REQUERIDOS(proceso, COALESCE(carrera, ''), COALESCE(etapa, ''), tipo_documento, momento);
CREATE UNIQUE INDEX IF NOT EXISTS uq_docs_estudiante_tipo_proceso ON DOCS_ESTUDIANTE(estudiante_id, tipo_documento, proceso);
CREATE INDEX IF NOT EXISTS idx_docs_estudiante_estado ON DOCS_ESTUDIANTE(estado);
CREATE INDEX IF NOT EXISTS idx_documentos_requeridos_proceso ON DOCUMENTOS_REQUERIDOS(proceso, activo);
CREATE INDEX IF NOT EXISTS idx_vacantes_carrera ON VACANTES_PRACTICAS(carrera);
CREATE INDEX IF NOT EXISTS idx_fechas_convocatoria_periodo_tipo ON FECHAS_CONVOCATORIA(periodo_academico, tipo);
CREATE INDEX IF NOT EXISTS idx_fechas_limite_periodo ON FECHAS_LIMITE_CALIFICACION(periodo_academico);
CREATE UNIQUE INDEX IF NOT EXISTS uq_eval_detalle_practica_parcial ON EVALUACIONES_PRACTICAS_DETALLE(practica_id, parcial) WHERE practica_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_eval_detalle_vinculacion_parcial ON EVALUACIONES_PRACTICAS_DETALLE(vinculacion_id, parcial) WHERE vinculacion_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_eval_detalle_vinculacion ON EVALUACIONES_PRACTICAS_DETALLE(vinculacion_id);
-- Nota: idx_eval_detalle_practica ya incluye la migracion fase54
-- (database/migraciones/fase54_indice_evaluaciones_practica_id.sql): agrega
-- el indice equivalente sobre practica_id, que faltaba desde el schema
-- original y agravaba el N+1 de findByPracticaId en el listado de
-- seguimiento de practicas.
CREATE INDEX IF NOT EXISTS idx_eval_detalle_practica ON EVALUACIONES_PRACTICAS_DETALLE(practica_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_encuestas_practica_parcial ON ENCUESTAS_SATISFACCION(practica_id, parcial) WHERE practica_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_encuestas_vinculacion_parcial ON ENCUESTAS_SATISFACCION(vinculacion_id, parcial) WHERE vinculacion_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_encuestas_estudiante ON ENCUESTAS_SATISFACCION(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_notas_academicas_estudiante ON NOTAS_ACADEMICAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_notas_academicas_estado ON NOTAS_ACADEMICAS(estado);
CREATE INDEX IF NOT EXISTS idx_tokens_recuperacion_usuario ON TOKENS_RECUPERACION(usuario_id);
CREATE INDEX IF NOT EXISTS idx_tokens_recuperacion_vigente ON TOKENS_RECUPERACION(token_hash, usado, fecha_expiracion);
CREATE UNIQUE INDEX IF NOT EXISTS uq_usuarios_external_id ON USUARIOS(LOWER(external_id))
    WHERE external_id IS NOT NULL AND BTRIM(external_id) <> '';
CREATE INDEX IF NOT EXISTS idx_importaciones_fecha ON IMPORTACIONES(fecha DESC);
CREATE INDEX IF NOT EXISTS idx_importaciones_tipo ON IMPORTACIONES(tipo, fecha DESC);
CREATE INDEX IF NOT EXISTS idx_postulaciones_asignado_vacante ON POSTULACIONES_MERITOCRATICAS(asignado_vacante_id);

CREATE OR REPLACE FUNCTION fn_validar_asistencia_expediente()
RETURNS TRIGGER AS $$
DECLARE
    estudiante_expediente INT;
BEGIN
    IF (NEW.practica_id IS NULL) = (NEW.vinculacion_id IS NULL) THEN
        RAISE EXCEPTION 'La asistencia debe pertenecer exactamente a una practica o vinculacion.';
    END IF;

    IF NEW.practica_id IS NOT NULL THEN
        SELECT estudiante_id INTO estudiante_expediente
        FROM PRACTICAS WHERE id = NEW.practica_id;
    ELSE
        SELECT estudiante_id INTO estudiante_expediente
        FROM VINCULACION WHERE id = NEW.vinculacion_id;
    END IF;

    IF estudiante_expediente IS NULL
       OR NEW.estudiante_id IS DISTINCT FROM estudiante_expediente THEN
        RAISE EXCEPTION 'El estudiante de la asistencia no coincide con el expediente asociado.';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_validar_asistencia_expediente
    BEFORE INSERT OR UPDATE OF estudiante_id, practica_id, vinculacion_id
    ON ASISTENCIAS
    FOR EACH ROW EXECUTE FUNCTION fn_validar_asistencia_expediente();

CREATE OR REPLACE FUNCTION fn_horas_aprobadas_practica(
    expediente_id INT,
    horas_requeridas_expediente INT
)
RETURNS INT AS $$
    SELECT LEAST(horas_requeridas_expediente, COALESCE(SUM(b.horas + b.horas_extra), 0)::INT)
    FROM BITACORAS b
    WHERE b.practica_id = expediente_id AND b.estado = 'aprobada';
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION fn_horas_aprobadas_vinculacion(
    expediente_id INT,
    horas_requeridas_expediente INT
)
RETURNS INT AS $$
    SELECT LEAST(horas_requeridas_expediente, COALESCE(SUM(b.horas + b.horas_extra), 0)::INT)
    FROM BITACORAS b
    WHERE b.vinculacion_id = expediente_id AND b.estado = 'aprobada';
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

CREATE OR REPLACE TRIGGER trg_derivar_horas_practica
    BEFORE INSERT OR UPDATE OF horas_requeridas, horas_completadas
    ON PRACTICAS
    FOR EACH ROW EXECUTE FUNCTION fn_derivar_horas_practica();

CREATE OR REPLACE TRIGGER trg_derivar_horas_vinculacion
    BEFORE INSERT OR UPDATE OF horas_requeridas, horas_completadas
    ON VINCULACION
    FOR EACH ROW EXECUTE FUNCTION fn_derivar_horas_vinculacion();

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
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_sincronizar_horas_bitacora
    AFTER INSERT OR UPDATE OF horas, horas_extra, estado, practica_id, vinculacion_id OR DELETE
    ON BITACORAS
    FOR EACH ROW EXECUTE FUNCTION fn_sincronizar_horas_desde_bitacora();

-- D8: Triggers de auditoría faltantes
CREATE OR REPLACE TRIGGER trg_auditoria_bitacoras
    AFTER INSERT OR UPDATE OR DELETE ON BITACORAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_asistencias
    AFTER INSERT OR UPDATE OR DELETE ON ASISTENCIAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_evaluaciones_detalle
    AFTER INSERT OR UPDATE OR DELETE ON EVALUACIONES_PRACTICAS_DETALLE
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_encuestas_satisfaccion
    AFTER INSERT OR UPDATE OR DELETE ON ENCUESTAS_SATISFACCION
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_fechas_convocatoria
    AFTER INSERT OR UPDATE OR DELETE ON FECHAS_CONVOCATORIA
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_notas_academicas
    AFTER INSERT OR UPDATE OR DELETE ON NOTAS_ACADEMICAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_importaciones
    AFTER INSERT OR UPDATE OR DELETE ON IMPORTACIONES
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: migration_v2_iso25010.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- MIGRACIÓN v2 — Correcciones Auditoría ISO/IEC 25010
-- Sistema de Prácticas y Vinculación UNIBE
-- PostgreSQL 15 — Supabase SQL Editor
-- 
-- INSTRUCCIONES:
-- 1. Abrir Supabase Dashboard → SQL Editor
-- 2. Copiar y pegar este script completo
-- 3. Ejecutar (Run)
-- 4. Verificar que no haya errores
-- ============================================================

-- ============================================================
-- D5: Agregar columna perfil_requerido a VACANTES_PRACTICAS
-- Descripción del perfil profesional que necesita el estudiante
-- ============================================================

ALTER TABLE VACANTES_PRACTICAS
ADD COLUMN IF NOT EXISTS perfil_requerido TEXT;

COMMENT ON COLUMN VACANTES_PRACTICAS.perfil_requerido 
IS 'Describe las habilidades, conocimientos y competencias requeridas del estudiante para esta vacante';

-- ============================================================
-- D6: Nueva tabla FECHAS_CONVOCATORIA
-- Gestiona los períodos de carga documental y postulación
-- ============================================================

CREATE TABLE IF NOT EXISTS FECHAS_CONVOCATORIA (
    id                          SERIAL PRIMARY KEY,
    periodo_academico           VARCHAR(20) NOT NULL,
    tipo                        VARCHAR(30) NOT NULL CHECK (tipo IN ('PRACTICAS', 'VINCULACION')),
    convocatoria_inicio         DATE NOT NULL,
    convocatoria_fin            DATE NOT NULL,
    fecha_limite_documentos     DATE NOT NULL,
    fecha_inicio_postulacion    DATE NOT NULL,
    creado_por                  INT REFERENCES USUARIOS(id),
    created_at                  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(periodo_academico, tipo)
);

COMMENT ON TABLE FECHAS_CONVOCATORIA 
IS 'Fechas de convocatoria por período académico y tipo (prácticas/vinculación)';

-- Insertar datos iniciales de convocatoria
INSERT INTO PERIODOS_ACADEMICOS (codigo, fecha_inicio, fecha_fin, estado)
VALUES ('2025-1', '2025-01-01', '2025-06-30', 'CERRADO')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO FECHAS_CONVOCATORIA (periodo_academico, tipo, convocatoria_inicio, convocatoria_fin, fecha_limite_documentos, fecha_inicio_postulacion)
VALUES 
    ('2025-1', 'PRACTICAS', '2025-06-01', '2025-07-31', '2025-06-20', '2025-06-25'),
    ('2025-1', 'VINCULACION', '2025-06-01', '2025-07-31', '2025-06-20', '2025-06-25')
ON CONFLICT (periodo_academico, tipo) DO NOTHING;

-- ============================================================
-- D7: Índices de rendimiento (ISO 25010 — 1.2 Eficiencia)
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_estudiantes_carrera ON ESTUDIANTES(carrera);
CREATE INDEX IF NOT EXISTS idx_practicas_estado ON PRACTICAS(estado);
CREATE INDEX IF NOT EXISTS idx_practicas_estudiante ON PRACTICAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_vinculacion_estado ON VINCULACION(estado);
CREATE INDEX IF NOT EXISTS idx_vinculacion_estudiante ON VINCULACION(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_bitacoras_estudiante ON BITACORAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_asistencias_estudiante ON ASISTENCIAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_postulaciones_estudiante ON POSTULACIONES_MERITOCRATICAS(estudiante_id);
CREATE INDEX IF NOT EXISTS idx_vacantes_carrera ON VACANTES_PRACTICAS(carrera);

-- ============================================================
-- D8: Triggers de auditoría faltantes (ISO 25010 — 1.6 Trazabilidad)
-- ============================================================

CREATE OR REPLACE TRIGGER trg_auditoria_bitacoras
    AFTER INSERT OR UPDATE OR DELETE ON BITACORAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_asistencias
    AFTER INSERT OR UPDATE OR DELETE ON ASISTENCIAS
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_evaluaciones_detalle
    AFTER INSERT OR UPDATE OR DELETE ON EVALUACIONES_PRACTICAS_DETALLE
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

CREATE OR REPLACE TRIGGER trg_auditoria_fechas_convocatoria
    AFTER INSERT OR UPDATE OR DELETE ON FECHAS_CONVOCATORIA
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria();

-- ============================================================
-- VERIFICACIÓN
-- ============================================================

-- Verificar que la columna fue creada
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'vacantes_practicas' AND column_name = 'perfil_requerido';

-- Verificar que la tabla fue creada
SELECT table_name 
FROM information_schema.tables 
WHERE table_name = 'fechas_convocatoria';

-- Verificar índices creados
SELECT indexname 
FROM pg_indexes 
WHERE tablename IN ('estudiantes', 'practicas', 'vinculacion', 'bitacoras', 'asistencias', 'postulaciones_meritocraticas', 'vacantes_practicas')
ORDER BY indexname;

-- Verificar triggers
SELECT trigger_name, event_object_table 
FROM information_schema.triggers 
WHERE trigger_name LIKE 'trg_auditoria_%'
ORDER BY trigger_name;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase2_calendario_academico.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 2: calendario academico y validaciones de orden logico
-- Ejecutar manualmente en el SQL Editor de Supabase.
-- Script idempotente: puede ejecutarse mas de una vez.

-- Fechas de convocatoria por periodo/tipo: el orden minimo debe ser
-- convocatoria -> documentos -> postulacion -> cierre.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fechas_convocatoria_orden'
    ) THEN
        ALTER TABLE FECHAS_CONVOCATORIA
        ADD CONSTRAINT chk_fechas_convocatoria_orden
        CHECK (
            convocatoria_inicio <= fecha_limite_documentos
            AND fecha_limite_documentos <= fecha_inicio_postulacion
            AND fecha_inicio_postulacion <= convocatoria_fin
        ) NOT VALID;
    END IF;
END $$;

-- Refuerzo idempotente del tipo de proceso esperado por el calendario.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fechas_convocatoria_tipo'
    ) THEN
        ALTER TABLE FECHAS_CONVOCATORIA
        ADD CONSTRAINT chk_fechas_convocatoria_tipo
        CHECK (tipo IN ('PRACTICAS', 'VINCULACION')) NOT VALID;
    END IF;
END $$;

-- Cada periodo academico debe tener una sola fecha limite por parcial.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = current_schema()
          AND indexname = 'uq_fechas_limite_periodo_parcial'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM FECHAS_LIMITE_CALIFICACION
        GROUP BY periodo_academico, parcial
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX uq_fechas_limite_periodo_parcial
            ON FECHAS_LIMITE_CALIFICACION(periodo_academico, parcial);
    END IF;
END $$;

-- Indices de consulta por calendario vigente.
CREATE INDEX IF NOT EXISTS idx_fechas_convocatoria_periodo_tipo
    ON FECHAS_CONVOCATORIA(periodo_academico, tipo);

CREATE INDEX IF NOT EXISTS idx_fechas_limite_periodo
    ON FECHAS_LIMITE_CALIFICACION(periodo_academico);

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase3_cupos_transaccionales.sql
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase4_meritocracia_backend.sql
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase5_docs_alcance.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 5 (prioridad 2) — Tablas pendientes para eliminar los
-- últimos datos simulados en localStorage:
--   1. DOCS_ESTUDIANTE      → documentos del expediente (cv, carta, cedula)
--                             hoy en signals que se pierden con F5
--   2. COORDINADOR_CARRERAS → alcance del coordinador (carreras y tipo)
--                             hoy en localStorage 'pravi_coord_carreras'
--
-- Ejecutar en Supabase SQL Editor. Es idempotente (IF NOT EXISTS).
-- Después de aplicarlo, avisar para conectar el backend (las entidades
-- JPA se agregan recién cuando estas tablas existan, por ddl-auto=validate).
-- ============================================================

-- 1. Documentos del expediente del estudiante (previos a la postulación,
--    por eso referencian ESTUDIANTES y no PRACTICAS/VINCULACION)
CREATE TABLE IF NOT EXISTS DOCS_ESTUDIANTE (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    requerido_id        INT,
    tipo_documento      VARCHAR(100) NOT NULL,
    url_archivo         VARCHAR(300),
    proceso             VARCHAR(30) DEFAULT 'GENERAL'
                        CHECK (proceso IN ('GENERAL', 'PRACTICAS', 'VINCULACION')),
    carrera             VARCHAR(150),
    etapa               VARCHAR(80),
    estado              VARCHAR(30) DEFAULT 'cargado'
                        CHECK (estado IN ('pendiente', 'cargado', 'en_revision', 'aprobado', 'rechazado', 'requiere_correccion')),
    observacion         TEXT,
    revisado_por        INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    fecha_revision      TIMESTAMP,
    fecha_subida        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(estudiante_id, tipo_documento, proceso)
);

-- 2. Alcance del coordinador: carreras asignadas y tipo de coordinación.
--    El tipo se repite por fila para no alterar USUARIOS; se lee de la
--    primera fila del usuario.
CREATE TABLE IF NOT EXISTS COORDINADOR_CARRERAS (
    id                  SERIAL PRIMARY KEY,
    usuario_id          INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    carrera             VARCHAR(200) NOT NULL,
    coordinacion_tipo   VARCHAR(20) NOT NULL DEFAULT 'AMBOS'
                        CHECK (coordinacion_tipo IN ('PRACTICAS', 'VINCULACION', 'AMBOS')),
    UNIQUE(usuario_id, carrera)
);

-- ============================================================
-- VERIFICACIÓN (debe devolver las 2 tablas con 0 filas)
-- ============================================================
SELECT 'DOCS_ESTUDIANTE' AS tabla, COUNT(*) AS filas FROM DOCS_ESTUDIANTE
UNION ALL
SELECT 'COORDINADOR_CARRERAS', COUNT(*) FROM COORDINADOR_CARRERAS;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase6_bitacoras_reales.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 6: bitacoras reales asociadas a practica o vinculacion
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - cada bitacora pertenece a una practica o vinculacion concreta
-- - cada bitacora tiene parcial academico
-- - el tutor revisa con estados controlados
-- - las horas aprobadas se calculan desde bitacoras aprobadas en backend

BEGIN;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS practica_id INT REFERENCES PRACTICAS(id) ON DELETE CASCADE;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS vinculacion_id INT REFERENCES VINCULACION(id) ON DELETE CASCADE;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS parcial INT;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS observaciones_tutor TEXT;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS revisado_por INT REFERENCES USUARIOS(id);

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS fecha_revision TIMESTAMP;

ALTER TABLE BITACORAS
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE BITACORAS
DROP CONSTRAINT IF EXISTS bitacoras_estado_check;

ALTER TABLE BITACORAS
DROP CONSTRAINT IF EXISTS chk_bitacoras_estado;

-- Backfill para datos existentes: asociar bitacoras antiguas al proceso activo
-- o mas reciente del estudiante. Si hay practica y vinculacion simultaneas,
-- se prioriza practica porque el modulo actual de UI es practicas.
UPDATE BITACORAS b
SET practica_id = p.id
FROM PRACTICAS p
WHERE b.practica_id IS NULL
  AND b.vinculacion_id IS NULL
  AND p.estudiante_id = b.estudiante_id
  AND p.id = (
      SELECT p2.id
      FROM PRACTICAS p2
      WHERE p2.estudiante_id = b.estudiante_id
      ORDER BY
          CASE WHEN p2.estado IN ('en_curso', 'pendiente') THEN 0 ELSE 1 END,
          p2.id DESC
      LIMIT 1
  );

UPDATE BITACORAS b
SET vinculacion_id = v.id
FROM VINCULACION v
WHERE b.practica_id IS NULL
  AND b.vinculacion_id IS NULL
  AND v.estudiante_id = b.estudiante_id
  AND v.id = (
      SELECT v2.id
      FROM VINCULACION v2
      WHERE v2.estudiante_id = b.estudiante_id
      ORDER BY
          CASE WHEN v2.estado IN ('en_curso', 'pendiente') THEN 0 ELSE 1 END,
          v2.id DESC
      LIMIT 1
  );

UPDATE BITACORAS
SET parcial = 1
WHERE parcial IS NULL;

ALTER TABLE BITACORAS
ALTER COLUMN parcial SET NOT NULL;

UPDATE BITACORAS
SET estado = 'aprobada'
WHERE estado = 'aprobado';

UPDATE BITACORAS
SET estado = 'rechazada'
WHERE estado = 'rechazado';

UPDATE BITACORAS
SET estado = 'requiere_correccion'
WHERE estado = 'correccion';

CREATE INDEX IF NOT EXISTS idx_bitacoras_practica
ON BITACORAS(practica_id);

CREATE INDEX IF NOT EXISTS idx_bitacoras_vinculacion
ON BITACORAS(vinculacion_id);

CREATE INDEX IF NOT EXISTS idx_bitacoras_estado
ON BITACORAS(estado);

CREATE INDEX IF NOT EXISTS idx_bitacoras_parcial
ON BITACORAS(parcial);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM BITACORAS
        WHERE (practica_id IS NULL AND vinculacion_id IS NULL)
           OR (practica_id IS NOT NULL AND vinculacion_id IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'Existen bitacoras sin expediente o con doble expediente. Corrige esos datos antes de aplicar constraints.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_expediente_unico'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_expediente_unico
        CHECK (
            (practica_id IS NOT NULL AND vinculacion_id IS NULL)
            OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_parcial'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_parcial
        CHECK (parcial IN (1, 2, 3));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_horas'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_horas
        CHECK (horas BETWEEN 1 AND 24);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_bitacoras_estado'
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_estado
        CHECK (estado IN ('pendiente', 'aprobada', 'rechazada', 'requiere_correccion'));
    END IF;
END $$;

COMMIT;


-- Validacion final: debe devolver 0 filas.
SELECT id, estudiante_id, practica_id, vinculacion_id, parcial, estado
FROM BITACORAS
WHERE (practica_id IS NULL AND vinculacion_id IS NULL)
   OR (practica_id IS NOT NULL AND vinculacion_id IS NOT NULL)
   OR parcial NOT IN (1, 2, 3)
   OR horas NOT BETWEEN 1 AND 24
   OR estado NOT IN ('pendiente', 'aprobada', 'rechazada', 'requiere_correccion');

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase7_parciales_notas_encuestas.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 7: parciales, notas por rol y encuesta persistente
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - una evaluacion por practica/parcial
-- - notas 0-10 reforzadas en base de datos
-- - encuesta persistente por practica o vinculacion/parcial
-- - evitar doble encuesta del mismo expediente/parcial

BEGIN;

CREATE TABLE IF NOT EXISTS EVALUACIONES_PRACTICAS_DETALLE_DUP_FASE7 AS
SELECT
    e.*,
    CURRENT_TIMESTAMP::TIMESTAMP AS respaldado_en,
    'duplicado practica/parcial removido antes de UNIQUE'::TEXT AS motivo_limpieza
FROM EVALUACIONES_PRACTICAS_DETALLE e
WITH NO DATA;

WITH duplicadas AS (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY practica_id, parcial
                ORDER BY id DESC
            ) AS rn
        FROM EVALUACIONES_PRACTICAS_DETALLE
        WHERE practica_id IS NOT NULL
          AND parcial IS NOT NULL
    ) ranked
    WHERE rn > 1
),
respaldo AS (
    INSERT INTO EVALUACIONES_PRACTICAS_DETALLE_DUP_FASE7
    SELECT
        e.*,
        CURRENT_TIMESTAMP,
        'duplicado practica/parcial removido antes de UNIQUE'
    FROM EVALUACIONES_PRACTICAS_DETALLE e
    JOIN duplicadas d ON d.id = e.id
    WHERE NOT EXISTS (
        SELECT 1
        FROM EVALUACIONES_PRACTICAS_DETALLE_DUP_FASE7 b
        WHERE b.id = e.id
    )
    RETURNING id
)
DELETE FROM EVALUACIONES_PRACTICAS_DETALLE e
USING duplicadas d
WHERE e.id = d.id;

UPDATE EVALUACIONES_PRACTICAS_DETALLE
SET nota_tutor = 0
WHERE nota_tutor IS NULL;

UPDATE EVALUACIONES_PRACTICAS_DETALLE
SET nota_coord = 0
WHERE nota_coord IS NULL;

UPDATE EVALUACIONES_PRACTICAS_DETALLE
SET nota_final = ROUND(((nota_tutor * 0.5) + (nota_coord * 0.5))::numeric, 2);

UPDATE EVALUACIONES_PRACTICAS_DETALLE
SET encuesta_completada = FALSE
WHERE encuesta_completada IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_evaluaciones_practica_parcial'
    ) THEN
        ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
        ADD CONSTRAINT uq_evaluaciones_practica_parcial
        UNIQUE (practica_id, parcial);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_evaluaciones_notas_0_10'
    ) THEN
        ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
        ADD CONSTRAINT chk_evaluaciones_notas_0_10
        CHECK (
            nota_tutor BETWEEN 0 AND 10
            AND nota_coord BETWEEN 0 AND 10
            AND nota_final BETWEEN 0 AND 10
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS ENCUESTAS_SATISFACCION (
    id                              SERIAL PRIMARY KEY,
    estudiante_id                   INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    practica_id                     INT REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    vinculacion_id                  INT REFERENCES VINCULACION(id) ON DELETE CASCADE,
    parcial                         INT NOT NULL CHECK (parcial IN (1, 2, 3)),
    satisfaccion_tutor              INT NOT NULL CHECK (satisfaccion_tutor BETWEEN 1 AND 5),
    satisfaccion_empresa_proyecto   INT NOT NULL CHECK (satisfaccion_empresa_proyecto BETWEEN 1 AND 5),
    relacion_carrera                INT NOT NULL CHECK (relacion_carrera BETWEEN 1 AND 5),
    claridad_instrucciones          INT NOT NULL CHECK (claridad_instrucciones BETWEEN 1 AND 5),
    comentario                      TEXT,
    fecha_envio                     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (practica_id IS NOT NULL AND vinculacion_id IS NULL)
        OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_encuestas_practica_parcial
ON ENCUESTAS_SATISFACCION(practica_id, parcial)
WHERE practica_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_encuestas_vinculacion_parcial
ON ENCUESTAS_SATISFACCION(vinculacion_id, parcial)
WHERE vinculacion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_encuestas_estudiante
ON ENCUESTAS_SATISFACCION(estudiante_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc
        WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_encuestas_satisfaccion
            AFTER INSERT OR UPDATE OR DELETE ON ENCUESTAS_SATISFACCION
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, practica_id, parcial, nota_tutor, nota_coord, nota_final
FROM EVALUACIONES_PRACTICAS_DETALLE
WHERE nota_tutor NOT BETWEEN 0 AND 10
   OR nota_coord NOT BETWEEN 0 AND 10
   OR nota_final NOT BETWEEN 0 AND 10
   OR parcial NOT IN (1, 2, 3);

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase9_vinculacion_real.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 9: vinculacion real con postulacion persistente
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - guardar postulaciones de estudiantes a proyectos de vinculacion
-- - evitar postulaciones activas duplicadas por estudiante
-- - permitir que coordinacion apruebe y cree el expediente oficial
-- - enlazar la postulacion aprobada con la vinculacion creada

BEGIN;

ALTER TABLE VINCULACION
ADD COLUMN IF NOT EXISTS periodo_academico VARCHAR(20);

CREATE TABLE IF NOT EXISTS POSTULACIONES_VINCULACION (
    id                  SERIAL PRIMARY KEY,
    estudiante_id       INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    proyecto_id         INT NOT NULL REFERENCES PROYECTOS(id) ON DELETE RESTRICT,
    estado              VARCHAR(30) NOT NULL DEFAULT 'Pendiente',
    periodo_academico   VARCHAR(20),
    vinculacion_id      INT REFERENCES VINCULACION(id) ON DELETE SET NULL,
    observacion         TEXT,
    aprobado_por        INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    aprobado_en         TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_postulaciones_vinculacion_estado'
    ) THEN
        ALTER TABLE POSTULACIONES_VINCULACION
        ADD CONSTRAINT chk_postulaciones_vinculacion_estado
        CHECK (estado IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_postulacion_vinculacion_activa_estudiante
ON POSTULACIONES_VINCULACION(estudiante_id)
WHERE estado = 'Pendiente';

CREATE UNIQUE INDEX IF NOT EXISTS uq_postulacion_vinculacion_vinculacion
ON POSTULACIONES_VINCULACION(vinculacion_id)
WHERE vinculacion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_postulaciones_vinculacion_proyecto
ON POSTULACIONES_VINCULACION(proyecto_id);

CREATE INDEX IF NOT EXISTS idx_postulaciones_vinculacion_estado
ON POSTULACIONES_VINCULACION(estado);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc
        WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_postulaciones_vinculacion
            AFTER INSERT OR UPDATE OR DELETE ON POSTULACIONES_VINCULACION
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, estudiante_id, proyecto_id, estado
FROM POSTULACIONES_VINCULACION
WHERE estado NOT IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo');

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase11_integracion_documentos_institucionales.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 11: integracion institucional y documentos configurables
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - preparar usuarios para sincronizacion futura con la base institucional
-- - ampliar documentos del estudiante con proceso, estado y revision
-- - permitir requisitos documentales estandar por proceso/carrera
-- - incluir carta de aceptacion como requisito opcional/configurable

BEGIN;

ALTER TABLE USUARIOS
ADD COLUMN IF NOT EXISTS fuente VARCHAR(30) DEFAULT 'MANUAL',
ADD COLUMN IF NOT EXISTS external_id VARCHAR(120);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_usuarios_fuente'
    ) THEN
        ALTER TABLE USUARIOS
        ADD CONSTRAINT chk_usuarios_fuente
        CHECK (fuente IN ('MANUAL', 'CSV_UNIVERSIDAD', 'API_UNIVERSIDAD'));
    END IF;
END $$;

ALTER TABLE DOCS_ESTUDIANTE
ADD COLUMN IF NOT EXISTS proceso VARCHAR(30) DEFAULT 'GENERAL',
ADD COLUMN IF NOT EXISTS carrera VARCHAR(150),
ADD COLUMN IF NOT EXISTS etapa VARCHAR(80),
ADD COLUMN IF NOT EXISTS estado VARCHAR(30) DEFAULT 'cargado',
ADD COLUMN IF NOT EXISTS observacion TEXT,
ADD COLUMN IF NOT EXISTS revisado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS fecha_revision TIMESTAMP,
ADD COLUMN IF NOT EXISTS requerido_id INT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_docs_estudiante_estado'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        ADD CONSTRAINT chk_docs_estudiante_estado
        CHECK (estado IN ('pendiente', 'cargado', 'en_revision', 'aprobado', 'rechazado', 'requiere_correccion'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_docs_estudiante_proceso'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        ADD CONSTRAINT chk_docs_estudiante_proceso
        CHECK (proceso IN ('GENERAL', 'PRACTICAS', 'VINCULACION'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS DOCUMENTOS_REQUERIDOS (
    id                  SERIAL PRIMARY KEY,
    proceso             VARCHAR(30) NOT NULL CHECK (proceso IN ('GENERAL', 'PRACTICAS', 'VINCULACION')),
    carrera             VARCHAR(150),
    etapa               VARCHAR(80),
    tipo_documento      VARCHAR(80) NOT NULL,
    nombre              VARCHAR(180) NOT NULL,
    descripcion         TEXT,
    obligatorio         BOOLEAN NOT NULL DEFAULT TRUE,
    momento             VARCHAR(40) NOT NULL DEFAULT 'PRE_POSTULACION'
                        CHECK (momento IN ('PRE_POSTULACION', 'POST_ACEPTACION', 'CIERRE')),
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    solicitado_por      INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_docs_estudiante_requerido'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        ADD CONSTRAINT fk_docs_estudiante_requerido
        FOREIGN KEY (requerido_id) REFERENCES DOCUMENTOS_REQUERIDOS(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_documentos_requeridos_definicion
ON DOCUMENTOS_REQUERIDOS(
    proceso,
    COALESCE(carrera, ''),
    COALESCE(etapa, ''),
    tipo_documento,
    momento
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_docs_estudiante_tipo_proceso
ON DOCS_ESTUDIANTE(estudiante_id, tipo_documento, proceso);

CREATE INDEX IF NOT EXISTS idx_docs_estudiante_estado
ON DOCS_ESTUDIANTE(estado);

CREATE INDEX IF NOT EXISTS idx_documentos_requeridos_proceso
ON DOCUMENTOS_REQUERIDOS(proceso, activo);

INSERT INTO DOCUMENTOS_REQUERIDOS
    (proceso, carrera, etapa, tipo_documento, nombre, descripcion, obligatorio, momento)
VALUES
    ('GENERAL', NULL, NULL, 'cv', 'Hoja de vida', 'Documento base del expediente estudiantil.', TRUE, 'PRE_POSTULACION'),
    ('GENERAL', NULL, NULL, 'cedula', 'Copia de cédula', 'Identificación oficial del estudiante.', TRUE, 'PRE_POSTULACION'),
    ('PRACTICAS', NULL, NULL, 'carta', 'Carta de solicitud de prácticas', 'Solicitud inicial para participar en convocatorias de prácticas.', TRUE, 'PRE_POSTULACION'),
    ('VINCULACION', NULL, NULL, 'carta', 'Carta de solicitud de vinculación', 'Solicitud inicial para participar en proyectos de vinculación.', TRUE, 'PRE_POSTULACION'),
    ('PRACTICAS', NULL, NULL, 'carta_aceptacion', 'Carta de aceptación del cupo', 'Documento posterior a la aceptación del cupo; puede marcarse obligatorio por carrera si aplica.', FALSE, 'POST_ACEPTACION'),
    ('VINCULACION', NULL, NULL, 'carta_aceptacion', 'Carta de aceptación del proyecto', 'Documento posterior a la aceptación del proyecto de vinculación; puede marcarse obligatorio por carrera si aplica.', FALSE, 'POST_ACEPTACION')
ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc
        WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_documentos_requeridos
            AFTER INSERT OR UPDATE OR DELETE ON DOCUMENTOS_REQUERIDOS
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver los documentos estandar activos.
SELECT proceso, carrera, tipo_documento, nombre, obligatorio, momento
FROM DOCUMENTOS_REQUERIDOS
WHERE activo = TRUE
ORDER BY proceso, momento, tipo_documento;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase12_notificaciones.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 12: notificaciones internas (2026-07-08)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - crear la tabla NOTIFICACIONES como base de la bandeja interna por usuario
-- - registrar eventos del sistema (documentos, postulaciones, asignaciones,
--   bitacoras, notas, encuestas y cierre de expediente) dirigidos a un usuario
-- - permitir marcar como leida y contar no leidas de forma eficiente
-- - guardar una referencia opcional (tipo + id) y un link interno del frontend
--
-- Script idempotente: puede ejecutarse dos veces sin error.

BEGIN;

CREATE TABLE IF NOT EXISTS NOTIFICACIONES (
    id                  SERIAL PRIMARY KEY,
    usuario_destino_id  INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    tipo                VARCHAR(40) NOT NULL,
    titulo              VARCHAR(200) NOT NULL,
    mensaje             TEXT NOT NULL,
    leida               BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    referencia_tipo     VARCHAR(40),
    referencia_id       BIGINT,
    link                VARCHAR(300)
);

-- Vocabulario de tipos en snake_case minusculas, igual que los estados de
-- BITACORAS y DOCS_ESTUDIANTE ('pendiente', 'aprobada', 'documento_aprobado'...).
-- Reservados sin emisor todavia: documento_aprobado/documento_rechazado (la
-- revision documental llega en la fase 16) y expediente_por_cerrar (fase 18).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notificaciones_tipo'
    ) THEN
        ALTER TABLE NOTIFICACIONES
        ADD CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN (
            'documento_aprobado',
            'documento_rechazado',
            'postulacion_enviada',
            'postulacion_resuelta',
            'asignacion_practica',
            'asignacion_vinculacion',
            'bitacora_rechazada',
            'nota_registrada',
            'encuesta_habilitada',
            'expediente_por_cerrar',
            'expediente_cerrado'
        ));
    END IF;
END $$;

-- Indice principal de la bandeja: listar/contar por destinatario y estado de lectura.
CREATE INDEX IF NOT EXISTS idx_notificaciones_destino_leida
ON NOTIFICACIONES(usuario_destino_id, leida);

-- Nota: NO se agrega trigger de auditoria (fn_auditoria) sobre NOTIFICACIONES.
-- Es una tabla derivada de alto volumen; auditarla duplicaria cada evento en
-- AUDITORIA sin aportar trazabilidad de negocio (los eventos de origen ya se auditan).

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, usuario_destino_id, tipo
FROM NOTIFICACIONES
WHERE tipo NOT IN (
    'documento_aprobado',
    'documento_rechazado',
    'postulacion_enviada',
    'postulacion_resuelta',
    'asignacion_practica',
    'asignacion_vinculacion',
    'bitacora_rechazada',
    'nota_registrada',
    'encuesta_habilitada',
    'expediente_por_cerrar',
    'expediente_cerrado'
);

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase14_recuperacion_password.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 14 - Recuperacion de contraseña
-- Sistema de Practicas y Vinculacion UNIBE
-- ============================================================
-- Objetivo:
-- - guardar tokens de recuperacion de contraseña con hash, expiracion corta
--   y marca de un solo uso
-- - soportar POST /api/auth/recuperar y POST /api/auth/restablecer
-- - no guardar el token plano en base de datos
--
-- Script idempotente: puede ejecutarse mas de una vez sin error.

CREATE TABLE IF NOT EXISTS TOKENS_RECUPERACION (
    id                  SERIAL PRIMARY KEY,
    usuario_id          INT NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
    token_hash          VARCHAR(128) NOT NULL UNIQUE,
    fecha_expiracion    TIMESTAMP NOT NULL,
    usado               BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usado_en            TIMESTAMP,
    CHECK (fecha_expiracion > fecha_creacion)
);

CREATE INDEX IF NOT EXISTS idx_tokens_recuperacion_usuario
ON TOKENS_RECUPERACION(usuario_id);

CREATE INDEX IF NOT EXISTS idx_tokens_recuperacion_vigente
ON TOKENS_RECUPERACION(token_hash, usado, fecha_expiracion);

-- Validacion final: debe devolver 0 filas.
SELECT id, usuario_id, usado, fecha_creacion, fecha_expiracion
FROM TOKENS_RECUPERACION
WHERE fecha_expiracion <= fecha_creacion;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase15_storage_documentos_constraints.sql
-- ────────────────────────────────────────────────────────────────────────────
-- FASE 15 - Compatibilidad de DOCS_ESTUDIANTE con Storage y procesos
-- Objetivo:
-- - eliminar restricciones antiguas de fase 5 que bloquean documentos por
--   proceso (PRACTICAS/VINCULACION/GENERAL) y tipos posteriores como
--   carta_aceptacion, informe_final o certificado.
-- - conservar la unicidad correcta: estudiante + tipo_documento + proceso.
--
-- Idempotente. Ejecutar en Supabase SQL Editor antes de probar carga real.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'docs_estudiante_estudiante_id_tipo_documento_key'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        DROP CONSTRAINT docs_estudiante_estudiante_id_tipo_documento_key;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'docs_estudiante_estudiante_id_tipo_documento_key'
    ) THEN
        DROP INDEX docs_estudiante_estudiante_id_tipo_documento_key;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'docs_estudiante_tipo_documento_check'
    ) THEN
        ALTER TABLE DOCS_ESTUDIANTE
        DROP CONSTRAINT docs_estudiante_tipo_documento_check;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_docs_estudiante_tipo_proceso
ON DOCS_ESTUDIANTE(estudiante_id, tipo_documento, proceso);

CREATE INDEX IF NOT EXISTS idx_docs_estudiante_estado
ON DOCS_ESTUDIANTE(estado);

-- Verificacion esperada:
-- 1) No debe existir la restriccion antigua por estudiante/tipo.
-- 2) Debe existir el indice unico estudiante/tipo/proceso.
SELECT
    'docs_estudiante_estudiante_id_tipo_documento_key' AS objeto,
    EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'docs_estudiante_estudiante_id_tipo_documento_key'
    ) AS existe;

SELECT
    'uq_docs_estudiante_tipo_proceso' AS objeto,
    EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uq_docs_estudiante_tipo_proceso'
    ) AS existe;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase17_paridad_vinculacion.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 17: paridad de vinculación con prácticas en parciales/notas.
-- Idempotente: permite que EVALUACIONES_PRACTICAS_DETALLE se asocie a
-- práctica o vinculación, exactamente a uno de los dos expedientes.

ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
ADD COLUMN IF NOT EXISTS vinculacion_id INT;

ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
ALTER COLUMN practica_id DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_eval_detalle_vinculacion'
    ) THEN
        ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
        ADD CONSTRAINT fk_eval_detalle_vinculacion
        FOREIGN KEY (vinculacion_id) REFERENCES VINCULACION(id) ON DELETE CASCADE;
    END IF;
END $$;

ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
DROP CONSTRAINT IF EXISTS evaluaciones_practicas_detalle_practica_id_parcial_key;

ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
DROP CONSTRAINT IF EXISTS uq_evaluacion_practica_parcial;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_eval_detalle_un_solo_expediente'
    ) THEN
        ALTER TABLE EVALUACIONES_PRACTICAS_DETALLE
        ADD CONSTRAINT chk_eval_detalle_un_solo_expediente
        CHECK (
            (practica_id IS NOT NULL AND vinculacion_id IS NULL)
            OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
        );
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_eval_detalle_practica_parcial
ON EVALUACIONES_PRACTICAS_DETALLE(practica_id, parcial)
WHERE practica_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_eval_detalle_vinculacion_parcial
ON EVALUACIONES_PRACTICAS_DETALLE(vinculacion_id, parcial)
WHERE vinculacion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_eval_detalle_vinculacion
ON EVALUACIONES_PRACTICAS_DETALLE(vinculacion_id);

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase18_cierre_robusto.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 18: cierre formal robusto.
-- Idempotente: agrega metadatos persistentes del cierre para prácticas
-- y vinculación. El snapshot también se registra en AUDITORIA desde backend.

ALTER TABLE PRACTICAS
ADD COLUMN IF NOT EXISTS cerrado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL;

ALTER TABLE PRACTICAS
ADD COLUMN IF NOT EXISTS cerrado_en TIMESTAMP;

ALTER TABLE PRACTICAS
ADD COLUMN IF NOT EXISTS cierre_snapshot JSONB;

ALTER TABLE VINCULACION
ADD COLUMN IF NOT EXISTS cerrado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL;

ALTER TABLE VINCULACION
ADD COLUMN IF NOT EXISTS cerrado_en TIMESTAMP;

ALTER TABLE VINCULACION
ADD COLUMN IF NOT EXISTS cierre_snapshot JSONB;

CREATE INDEX IF NOT EXISTS idx_practicas_cerrado_en
ON PRACTICAS(cerrado_en)
WHERE cerrado_en IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_vinculacion_cerrado_en
ON VINCULACION(cerrado_en)
WHERE cerrado_en IS NOT NULL;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase19_convenios.sql
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase20_importaciones.sql
-- ────────────────────────────────────────────────────────────────────────────
-- Fase 20: importacion institucional CSV preparada para futura API.
-- Script idempotente para ejecutar en el SQL Editor de Supabase.

BEGIN;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'notas_academicas'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%fuente%'
    LOOP
        EXECUTE format('ALTER TABLE NOTAS_ACADEMICAS DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

-- Unificar el vocabulario historico después de retirar el CHECK antiguo.
UPDATE NOTAS_ACADEMICAS
SET fuente = 'CSV_UNIVERSIDAD'
WHERE fuente = 'CSV';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_notas_academicas_fuente'
    ) THEN
        ALTER TABLE NOTAS_ACADEMICAS
        ADD CONSTRAINT chk_notas_academicas_fuente
        CHECK (fuente IN ('MANUAL', 'CSV_UNIVERSIDAD', 'API_UNIVERSIDAD'));
    END IF;
END $$;

ALTER TABLE POSTULACIONES_MERITOCRATICAS
ADD COLUMN IF NOT EXISTS asignado_vacante_id INT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_postulacion_asignado_vacante'
    ) THEN
        ALTER TABLE POSTULACIONES_MERITOCRATICAS
        ADD CONSTRAINT fk_postulacion_asignado_vacante
        FOREIGN KEY (asignado_vacante_id)
        REFERENCES VACANTES_PRACTICAS(id)
        ON DELETE SET NULL;
    END IF;
END $$;

-- Backfill seguro: solo cuando una única preferencia pertenece a la empresa asignada.
WITH candidatas AS (
    SELECT p.id AS postulacion_id,
           MIN(v.id) AS vacante_id,
           COUNT(*) AS cantidad
    FROM POSTULACIONES_MERITOCRATICAS p
    JOIN VACANTES_PRACTICAS v
      ON v.id IN (p.pref1_id, p.pref2_id, p.pref3_id)
     AND v.empresa_id = p.asignado_empresa_id
    WHERE p.asignado_vacante_id IS NULL
      AND p.asignado_empresa_id IS NOT NULL
    GROUP BY p.id
)
UPDATE POSTULACIONES_MERITOCRATICAS p
SET asignado_vacante_id = c.vacante_id
FROM candidatas c
WHERE p.id = c.postulacion_id
  AND c.cantidad = 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT LOWER(external_id)
        FROM USUARIOS
        WHERE external_id IS NOT NULL AND BTRIM(external_id) <> ''
        GROUP BY LOWER(external_id)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen usuarios con external_id repetido. Corrige esos datos antes de aplicar la restriccion.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuarios_external_id
    ON USUARIOS(LOWER(external_id))
    WHERE external_id IS NOT NULL AND BTRIM(external_id) <> '';

CREATE TABLE IF NOT EXISTS IMPORTACIONES (
    id              BIGSERIAL PRIMARY KEY,
    archivo_nombre  VARCHAR(255) NOT NULL,
    tipo            VARCHAR(20) NOT NULL
                    CONSTRAINT chk_importaciones_tipo CHECK (tipo IN ('ESTUDIANTES', 'NOTAS')),
    filas_total     INT NOT NULL DEFAULT 0 CHECK (filas_total >= 0),
    filas_ok        INT NOT NULL DEFAULT 0 CHECK (filas_ok >= 0),
    filas_error     INT NOT NULL DEFAULT 0 CHECK (filas_error >= 0),
    creados         INT NOT NULL DEFAULT 0 CHECK (creados >= 0),
    actualizados    INT NOT NULL DEFAULT 0 CHECK (actualizados >= 0),
    enlazados       INT NOT NULL DEFAULT 0 CHECK (enlazados >= 0),
    estado          VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA'
                    CONSTRAINT chk_importaciones_estado CHECK (estado IN ('COMPLETADA', 'CON_ERRORES', 'FALLIDA')),
    detalle_errores JSONB NOT NULL DEFAULT '[]'::jsonb,
    usuario_id      INT REFERENCES USUARIOS(id) ON DELETE SET NULL,
    fecha           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (filas_ok + filas_error = filas_total)
);

CREATE INDEX IF NOT EXISTS idx_importaciones_fecha
    ON IMPORTACIONES(fecha DESC);

CREATE INDEX IF NOT EXISTS idx_importaciones_tipo
    ON IMPORTACIONES(tipo, fecha DESC);

CREATE INDEX IF NOT EXISTS idx_postulaciones_asignado_vacante
    ON POSTULACIONES_MERITOCRATICAS(asignado_vacante_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_proc WHERE proname = 'fn_auditoria'
    ) THEN
        CREATE OR REPLACE TRIGGER trg_auditoria_importaciones
            AFTER INSERT OR UPDATE OR DELETE ON IMPORTACIONES
            FOR EACH ROW EXECUTE FUNCTION fn_auditoria();
    END IF;
END $$;

COMMIT;

-- Validacion final: las dos primeras consultas deben devolver 0 filas.
SELECT id, fuente
FROM NOTAS_ACADEMICAS
WHERE fuente NOT IN ('MANUAL', 'CSV_UNIVERSIDAD', 'API_UNIVERSIDAD');

SELECT LOWER(external_id) AS external_id, COUNT(*)
FROM USUARIOS
WHERE external_id IS NOT NULL AND BTRIM(external_id) <> ''
GROUP BY LOWER(external_id)
HAVING COUNT(*) > 1;

-- Filas antiguas que requieren seleccionar manualmente la vacante exacta.
SELECT id, estudiante_id, asignado_empresa_id
FROM POSTULACIONES_MERITOCRATICAS
WHERE asignado_empresa_id IS NOT NULL
  AND asignado_vacante_id IS NULL;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase26_cierre_mvp.sql
-- ────────────────────────────────────────────────────────────────────────────
-- FASE 26: cierre de integridad de bitacoras para el MVP.
-- Idempotente y listo para ejecutar en Supabase SQL Editor.
--
-- El backfill a parcial 1 replica la decision historica de la fase 6 para
-- registros antiguos. Antes de imponer NOT NULL se rechaza cualquier valor
-- distinto de 1, 2 o 3.

BEGIN;

UPDATE BITACORAS
SET parcial = 1
WHERE parcial IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM BITACORAS
        WHERE parcial NOT IN (1, 2, 3)
    ) THEN
        RAISE EXCEPTION
            'Existen bitacoras con parcial fuera del rango 1..3. Corrige esos datos antes de aplicar NOT NULL.';
    END IF;
END $$;

ALTER TABLE BITACORAS
ALTER COLUMN parcial SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_bitacoras_parcial'
          AND conrelid = 'bitacoras'::regclass
    ) THEN
        ALTER TABLE BITACORAS
        ADD CONSTRAINT chk_bitacoras_parcial
        CHECK (parcial IN (1, 2, 3)) NOT VALID;
    END IF;
END $$;

ALTER TABLE BITACORAS
VALIDATE CONSTRAINT chk_bitacoras_parcial;

COMMIT;

-- Validacion 1: is_nullable debe ser NO.
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'bitacoras'
  AND column_name = 'parcial';

-- Validacion 2: debe devolver 0.
SELECT COUNT(*) AS bitacoras_invalidas
FROM BITACORAS
WHERE parcial IS NULL
   OR parcial NOT IN (1, 2, 3);

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase27_catalogo_carreras.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 27 — CATALOGO DE CARRERAS
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
-- ============================================================
-- Cambio:  tabla CARRERAS como catalogo unico de carreras; se siembra
--          con las carreras ya usadas en el sistema y las carreras base.
-- Motivo:  eliminar el texto libre (errores de tildes/escritura) en
--          estudiantes, convenios, vacantes y alcance del coordinador.
-- Impacto: tabla nueva CARRERAS. No modifica columnas existentes: las
--          demas tablas siguen guardando la carrera como texto y el
--          frontend pasa a elegirla del catalogo.

CREATE TABLE IF NOT EXISTS CARRERAS (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL UNIQUE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 1. Carreras institucionales base. El ADMIN puede agregar otras desde la UI.
INSERT INTO CARRERAS (nombre) VALUES
    ('Derecho'),
    ('Enfermería'),
    ('Estética Integral'),
    ('Fisioterapia'),
    ('Gastronomía'),
    ('Ingeniería en Software'),
    ('Medicina'),
    ('Multimedia y Producción Audiovisual'),
    ('Nutrición y Dietética'),
    ('Odontología'),
    ('Psicología'),
    ('Psicología Clínica')
ON CONFLICT (nombre) DO NOTHING;

-- 2. Carreras ya usadas en los datos existentes (no perder ninguna)
INSERT INTO CARRERAS (nombre)
SELECT DISTINCT TRIM(carrera) FROM ESTUDIANTES
WHERE carrera IS NOT NULL AND TRIM(carrera) <> ''
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO CARRERAS (nombre)
SELECT DISTINCT TRIM(carrera) FROM COORDINADOR_CARRERAS
WHERE carrera IS NOT NULL AND TRIM(carrera) <> ''
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO CARRERAS (nombre)
SELECT DISTINCT TRIM(carrera) FROM CONVENIOS_CARRERAS
WHERE carrera IS NOT NULL AND TRIM(carrera) <> ''
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO CARRERAS (nombre)
SELECT DISTINCT TRIM(carrera) FROM VACANTES_PRACTICAS
WHERE carrera IS NOT NULL AND TRIM(carrera) <> ''
ON CONFLICT (nombre) DO NOTHING;

-- ============================================================
-- Verificacion
-- ============================================================
SELECT id, nombre, activo FROM CARRERAS ORDER BY nombre;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase28_tutor_tipo.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 28 — TIPO REAL DEL TUTOR (PRACTICAS / VINCULACION / AMBOS)
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
-- ============================================================
-- Cambio:  columna usuarios.tutor_tipo con CHECK; reemplaza el toggle
--          demo del frontend (localStorage pravi_tutor_tipo).
-- Motivo:  el tipo de tutoria es un dato institucional que asigna el
--          ADMIN, no una simulacion del navegador.
-- Impacto: tabla USUARIOS. Los tutores existentes quedan en AMBOS
--          (equivalente al comportamiento previo del demo).

ALTER TABLE USUARIOS ADD COLUMN IF NOT EXISTS tutor_tipo VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'usuarios_tutor_tipo_check'
    ) THEN
        ALTER TABLE USUARIOS
            ADD CONSTRAINT usuarios_tutor_tipo_check
            CHECK (tutor_tipo IS NULL OR tutor_tipo IN ('PRACTICAS', 'VINCULACION', 'AMBOS'));
    END IF;
END $$;

-- Tutores existentes sin tipo: AMBOS
UPDATE USUARIOS u
SET tutor_tipo = 'AMBOS'
WHERE u.tutor_tipo IS NULL
  AND EXISTS (
      SELECT 1
      FROM USUARIOS_ROLES ur
      JOIN ROLES r ON r.id = ur.rol_id
      WHERE ur.usuario_id = u.id AND r.codigo = 'TUTOR'
  );

-- ============================================================
-- Verificacion
-- ============================================================
SELECT u.email, u.tutor_tipo
FROM USUARIOS u
WHERE u.tutor_tipo IS NOT NULL
ORDER BY u.email;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase32_ofertas_cupos_empresa.sql
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase33_historial_academico_casos_excepcionales.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 33 - HISTORIAL ACADEMICO Y CASOS EXCEPCIONALES
-- ============================================================
-- Ejecutar en el SQL Editor de Supabase. Es idempotente.
--
-- ADVERTENCIA: reemplaza exclusivamente los CHECK heredados de estado
-- para admitir REPROBADO/RETIRADO; no elimina filas ni historial.
--
-- La linea de tiempo se deriva de PRACTICAS/VINCULACION, sus periodos,
-- cierre_snapshot y AUDITORIA. No se crea una tabla de eventos adicional.

BEGIN;

ALTER TABLE PRACTICAS
    ADD COLUMN IF NOT EXISTS motivo_finalizacion TEXT;

ALTER TABLE VINCULACION
    ADD COLUMN IF NOT EXISTS motivo_finalizacion TEXT;

-- Los CHECK originales fueron declarados sin nombre explícito por el
-- schema base, por lo que PostgreSQL los nombra automaticamente así.
ALTER TABLE PRACTICAS
    DROP CONSTRAINT IF EXISTS practicas_estado_check,
    DROP CONSTRAINT IF EXISTS chk_practicas_estado;

ALTER TABLE VINCULACION
    DROP CONSTRAINT IF EXISTS vinculacion_estado_check,
    DROP CONSTRAINT IF EXISTS chk_vinculacion_estado;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'PRACTICAS'::regclass
          AND conname = 'chk_practicas_estado'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT chk_practicas_estado
            CHECK (estado IN ('pendiente', 'en_curso', 'completado', 'reprobado', 'retirado'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'VINCULACION'::regclass
          AND conname = 'chk_vinculacion_estado'
    ) THEN
        ALTER TABLE VINCULACION
            ADD CONSTRAINT chk_vinculacion_estado
            CHECK (estado IN ('pendiente', 'en_curso', 'completado', 'reprobado', 'retirado'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'PRACTICAS'::regclass
          AND conname = 'chk_practicas_finalizacion_excepcional'
    ) THEN
        ALTER TABLE PRACTICAS
            ADD CONSTRAINT chk_practicas_finalizacion_excepcional
            CHECK (
                estado NOT IN ('reprobado', 'retirado')
                OR (
                    motivo_finalizacion IS NOT NULL
                    AND CHAR_LENGTH(BTRIM(motivo_finalizacion)) >= 10
                    AND cerrado_en IS NOT NULL
                    AND cerrado_por IS NOT NULL
                    AND cierre_snapshot IS NOT NULL
                )
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'VINCULACION'::regclass
          AND conname = 'chk_vinculacion_finalizacion_excepcional'
    ) THEN
        ALTER TABLE VINCULACION
            ADD CONSTRAINT chk_vinculacion_finalizacion_excepcional
            CHECK (
                estado NOT IN ('reprobado', 'retirado')
                OR (
                    motivo_finalizacion IS NOT NULL
                    AND CHAR_LENGTH(BTRIM(motivo_finalizacion)) >= 10
                    AND cerrado_en IS NOT NULL
                    AND cerrado_por IS NOT NULL
                    AND cierre_snapshot IS NOT NULL
                )
            );
    END IF;
END $$;

-- Cierra la carrera entre dos solicitudes simultaneas del mismo proceso.
-- La exclusividad cruzada PRACTICAS/VINCULACION se valida en backend.
DO $$
BEGIN
    IF EXISTS (
        SELECT estudiante_id
        FROM PRACTICAS
        WHERE estado IN ('pendiente', 'en_curso')
        GROUP BY estudiante_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen estudiantes con mas de una practica activa. Corrige esos datos antes de aplicar la Fase 33.';
    END IF;

    IF EXISTS (
        SELECT estudiante_id
        FROM VINCULACION
        WHERE estado IN ('pendiente', 'en_curso')
        GROUP BY estudiante_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen estudiantes con mas de una vinculacion activa. Corrige esos datos antes de aplicar la Fase 33.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_practicas_estudiante_activa
    ON PRACTICAS(estudiante_id)
    WHERE estado IN ('pendiente', 'en_curso');

CREATE UNIQUE INDEX IF NOT EXISTS ux_vinculacion_estudiante_activa
    ON VINCULACION(estudiante_id)
    WHERE estado IN ('pendiente', 'en_curso');

CREATE INDEX IF NOT EXISTS idx_practicas_estudiante_periodo
    ON PRACTICAS(estudiante_id, periodo_academico);

CREATE INDEX IF NOT EXISTS idx_vinculacion_estudiante_periodo
    ON VINCULACION(estudiante_id, periodo_academico);

COMMENT ON COLUMN PRACTICAS.motivo_finalizacion IS
    'Motivo obligatorio cuando el expediente termina como reprobado o retirado.';

COMMENT ON COLUMN VINCULACION.motivo_finalizacion IS
    'Motivo obligatorio cuando el expediente termina como reprobado o retirado.';

COMMIT;

-- Resultado esperado: cuatro constraints con validada = true.
SELECT conrelid::regclass AS tabla,
       conname AS nombre_constraint,
       convalidated AS validada
FROM pg_constraint
WHERE conname IN (
    'chk_practicas_estado',
    'chk_practicas_finalizacion_excepcional',
    'chk_vinculacion_estado',
    'chk_vinculacion_finalizacion_excepcional'
)
ORDER BY 1, 2;

-- Resultado esperado: cero filas.
SELECT 'PRACTICAS' AS tabla, estudiante_id, COUNT(*) AS procesos_activos
FROM PRACTICAS
WHERE estado IN ('pendiente', 'en_curso')
GROUP BY estudiante_id
HAVING COUNT(*) > 1
UNION ALL
SELECT 'VINCULACION' AS tabla, estudiante_id, COUNT(*) AS procesos_activos
FROM VINCULACION
WHERE estado IN ('pendiente', 'en_curso')
GROUP BY estudiante_id
HAVING COUNT(*) > 1;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase34_asistencias_por_expediente.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 34 - ASISTENCIAS ASOCIADAS AL EXPEDIENTE EXACTO
-- ============================================================
-- Ejecutar completo en el SQL Editor de Supabase. Es idempotente.
-- No elimina asistencias y no asigna registros ambiguos.

BEGIN;

ALTER TABLE ASISTENCIAS
    ADD COLUMN IF NOT EXISTS practica_id INT,
    ADD COLUMN IF NOT EXISTS vinculacion_id INT;

-- Primera pasada: asocia por estudiante y rango de fechas solamente cuando
-- existe un unico expediente compatible entre Practicas y Vinculacion.
WITH candidatos AS (
    SELECT a.id AS asistencia_id, 'P'::TEXT AS tipo, p.id AS expediente_id
    FROM ASISTENCIAS a
    JOIN PRACTICAS p ON p.estudiante_id = a.estudiante_id
    WHERE a.practica_id IS NULL
      AND a.vinculacion_id IS NULL
      AND (p.fecha_inicio IS NULL OR a.fecha >= p.fecha_inicio)
      AND (p.fecha_fin IS NULL OR a.fecha <= p.fecha_fin)
    UNION ALL
    SELECT a.id, 'V'::TEXT, v.id
    FROM ASISTENCIAS a
    JOIN VINCULACION v ON v.estudiante_id = a.estudiante_id
    WHERE a.practica_id IS NULL
      AND a.vinculacion_id IS NULL
      AND (v.fecha_inicio IS NULL OR a.fecha >= v.fecha_inicio)
      AND (v.fecha_fin IS NULL OR a.fecha <= v.fecha_fin)
), unicos AS (
    SELECT asistencia_id,
           MAX(tipo) AS tipo,
           MAX(expediente_id) AS expediente_id
    FROM candidatos
    GROUP BY asistencia_id
    HAVING COUNT(*) = 1
)
UPDATE ASISTENCIAS a
SET practica_id = CASE WHEN u.tipo = 'P' THEN u.expediente_id END,
    vinculacion_id = CASE WHEN u.tipo = 'V' THEN u.expediente_id END
FROM unicos u
WHERE a.id = u.asistencia_id;

-- Segunda pasada: si el estudiante solo ha tenido un expediente en todo el
-- sistema, la asociacion tambien es inequivoca aunque falten fechas historicas.
WITH candidatos AS (
    SELECT a.id AS asistencia_id, 'P'::TEXT AS tipo, p.id AS expediente_id
    FROM ASISTENCIAS a
    JOIN PRACTICAS p ON p.estudiante_id = a.estudiante_id
    WHERE a.practica_id IS NULL AND a.vinculacion_id IS NULL
    UNION ALL
    SELECT a.id, 'V'::TEXT, v.id
    FROM ASISTENCIAS a
    JOIN VINCULACION v ON v.estudiante_id = a.estudiante_id
    WHERE a.practica_id IS NULL AND a.vinculacion_id IS NULL
), unicos AS (
    SELECT asistencia_id,
           MAX(tipo) AS tipo,
           MAX(expediente_id) AS expediente_id
    FROM candidatos
    GROUP BY asistencia_id
    HAVING COUNT(*) = 1
)
UPDATE ASISTENCIAS a
SET practica_id = CASE WHEN u.tipo = 'P' THEN u.expediente_id END,
    vinculacion_id = CASE WHEN u.tipo = 'V' THEN u.expediente_id END
FROM unicos u
WHERE a.id = u.asistencia_id;

DO $$
DECLARE
    ids TEXT;
BEGIN
    SELECT STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO ids
    FROM (
        SELECT id
        FROM ASISTENCIAS
        WHERE (practica_id IS NULL AND vinculacion_id IS NULL)
           OR (practica_id IS NOT NULL AND vinculacion_id IS NOT NULL)
        ORDER BY id
        LIMIT 20
    ) pendientes;

    IF ids IS NOT NULL THEN
        RAISE EXCEPTION
            'Hay asistencias sin expediente unico o asociadas a ambos procesos. IDs: %. Corrige esos datos antes de aplicar la Fase 34.',
            ids;
    END IF;

    SELECT STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO ids
    FROM (
        SELECT a.id
        FROM ASISTENCIAS a
        LEFT JOIN PRACTICAS p ON p.id = a.practica_id
        LEFT JOIN VINCULACION v ON v.id = a.vinculacion_id
        WHERE (a.practica_id IS NOT NULL AND p.estudiante_id IS DISTINCT FROM a.estudiante_id)
           OR (a.vinculacion_id IS NOT NULL AND v.estudiante_id IS DISTINCT FROM a.estudiante_id)
        ORDER BY a.id
        LIMIT 20
    ) inconsistentes;

    IF ids IS NOT NULL THEN
        RAISE EXCEPTION
            'Hay asistencias cuyo estudiante no coincide con el expediente. IDs: %.', ids;
    END IF;

    SELECT STRING_AGG(id::TEXT, ', ' ORDER BY id)
    INTO ids
    FROM (
        SELECT MIN(id) AS id
        FROM ASISTENCIAS
        GROUP BY practica_id, fecha
        HAVING practica_id IS NOT NULL AND COUNT(*) > 1
        UNION ALL
        SELECT MIN(id) AS id
        FROM ASISTENCIAS
        GROUP BY vinculacion_id, fecha
        HAVING vinculacion_id IS NOT NULL AND COUNT(*) > 1
        ORDER BY id
        LIMIT 20
    ) duplicadas;

    IF ids IS NOT NULL THEN
        RAISE EXCEPTION
            'Hay asistencias duplicadas para el mismo expediente y fecha. Revisa los grupos que incluyen los IDs: %.',
            ids;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ASISTENCIAS'::regclass
          AND conname = 'fk_asistencias_practica'
    ) THEN
        ALTER TABLE ASISTENCIAS
            ADD CONSTRAINT fk_asistencias_practica
            FOREIGN KEY (practica_id) REFERENCES PRACTICAS(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ASISTENCIAS'::regclass
          AND conname = 'fk_asistencias_vinculacion'
    ) THEN
        ALTER TABLE ASISTENCIAS
            ADD CONSTRAINT fk_asistencias_vinculacion
            FOREIGN KEY (vinculacion_id) REFERENCES VINCULACION(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ASISTENCIAS'::regclass
          AND conname = 'chk_asistencias_expediente_unico'
    ) THEN
        ALTER TABLE ASISTENCIAS
            ADD CONSTRAINT chk_asistencias_expediente_unico
            CHECK (
                (practica_id IS NOT NULL AND vinculacion_id IS NULL)
                OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
            );
    END IF;
END $$;

ALTER TABLE ASISTENCIAS VALIDATE CONSTRAINT fk_asistencias_practica;
ALTER TABLE ASISTENCIAS VALIDATE CONSTRAINT fk_asistencias_vinculacion;
ALTER TABLE ASISTENCIAS VALIDATE CONSTRAINT chk_asistencias_expediente_unico;

CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencias_practica_fecha
    ON ASISTENCIAS(practica_id, fecha)
    WHERE practica_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_asistencias_vinculacion_fecha
    ON ASISTENCIAS(vinculacion_id, fecha)
    WHERE vinculacion_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_asistencias_practica
    ON ASISTENCIAS(practica_id);

CREATE INDEX IF NOT EXISTS idx_asistencias_vinculacion
    ON ASISTENCIAS(vinculacion_id);

CREATE OR REPLACE FUNCTION fn_validar_asistencia_expediente()
RETURNS TRIGGER AS $$
DECLARE
    estudiante_expediente INT;
BEGIN
    IF (NEW.practica_id IS NULL) = (NEW.vinculacion_id IS NULL) THEN
        RAISE EXCEPTION 'La asistencia debe pertenecer exactamente a una practica o vinculacion.';
    END IF;

    IF NEW.practica_id IS NOT NULL THEN
        SELECT estudiante_id INTO estudiante_expediente
        FROM PRACTICAS WHERE id = NEW.practica_id;
    ELSE
        SELECT estudiante_id INTO estudiante_expediente
        FROM VINCULACION WHERE id = NEW.vinculacion_id;
    END IF;

    IF estudiante_expediente IS NULL THEN
        RAISE EXCEPTION 'El expediente asociado a la asistencia no existe.';
    END IF;

    IF NEW.estudiante_id IS DISTINCT FROM estudiante_expediente THEN
        RAISE EXCEPTION 'El estudiante de la asistencia no coincide con el expediente asociado.';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_validar_asistencia_expediente ON ASISTENCIAS;
CREATE TRIGGER trg_validar_asistencia_expediente
    BEFORE INSERT OR UPDATE OF estudiante_id, practica_id, vinculacion_id
    ON ASISTENCIAS
    FOR EACH ROW
    EXECUTE FUNCTION fn_validar_asistencia_expediente();

COMMENT ON COLUMN ASISTENCIAS.practica_id IS
    'Practica exacta a la que pertenece la asistencia; excluyente con vinculacion_id.';
COMMENT ON COLUMN ASISTENCIAS.vinculacion_id IS
    'Vinculacion exacta a la que pertenece la asistencia; excluyente con practica_id.';

COMMIT;

-- Resultado esperado: tres constraints con validada = true.
SELECT conname AS nombre_constraint, convalidated AS validada
FROM pg_constraint
WHERE conrelid = 'ASISTENCIAS'::regclass
  AND conname IN (
      'fk_asistencias_practica',
      'fk_asistencias_vinculacion',
      'chk_asistencias_expediente_unico'
  )
ORDER BY 1;

-- Resultado esperado: una fila con invalidas = 0.
SELECT COUNT(*) AS invalidas
FROM ASISTENCIAS
WHERE (practica_id IS NULL AND vinculacion_id IS NULL)
   OR (practica_id IS NOT NULL AND vinculacion_id IS NOT NULL);

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase35_horas_oficiales_derivadas.sql
-- ────────────────────────────────────────────────────────────────────────────
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
        COALESCE(SUM(b.horas + b.horas_extra), 0)::INT
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
        COALESCE(SUM(b.horas + b.horas_extra), 0)::INT
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
    AFTER INSERT OR UPDATE OF horas, horas_extra, estado, practica_id, vinculacion_id OR DELETE
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

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase36_endurecimiento_login.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 36 - ENDURECIMIENTO DEL INICIO DE SESION
-- ============================================================
-- Ejecutar completo en el SQL Editor de Supabase. Es idempotente.
-- No desactiva usuarios ni invalida sesiones existentes.

BEGIN;

ALTER TABLE USUARIOS
    ADD COLUMN IF NOT EXISTS intentos_login_fallidos INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bloqueado_hasta TIMESTAMP;

UPDATE USUARIOS
SET intentos_login_fallidos = 0
WHERE intentos_login_fallidos IS NULL;

ALTER TABLE USUARIOS
    ALTER COLUMN intentos_login_fallidos SET DEFAULT 0,
    ALTER COLUMN intentos_login_fallidos SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'USUARIOS'::regclass
          AND conname = 'chk_usuarios_intentos_login_no_negativos'
    ) THEN
        ALTER TABLE USUARIOS
            ADD CONSTRAINT chk_usuarios_intentos_login_no_negativos
            CHECK (intentos_login_fallidos >= 0);
    END IF;
END $$;

ALTER TABLE USUARIOS
    VALIDATE CONSTRAINT chk_usuarios_intentos_login_no_negativos;

COMMIT;

-- Resultado esperado: dos columnas, contador NOT NULL y bloqueo nullable.
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'usuarios'
  AND column_name IN ('intentos_login_fallidos', 'bloqueado_hasta')
ORDER BY column_name;


-- Resultado esperado: una fila con validada = true.
SELECT conname AS nombre_constraint, convalidated AS validada
FROM pg_constraint
WHERE conrelid = 'USUARIOS'::regclass
  AND conname = 'chk_usuarios_intentos_login_no_negativos';

-- Resultado esperado: invalidos = 0.
SELECT COUNT(*) AS invalidos
FROM USUARIOS
WHERE intentos_login_fallidos < 0;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase38_cupos_fundaciones_proyectos.sql
-- ────────────────────────────────────────────────────────────────────────────
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


-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase39_periodos_y_ofertas_fundaciones.sql
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase42_ciclo_vida_cierre_periodos.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 42 — Ciclo de vida de vacantes/proyectos y cierre de periodos
-- Script idempotente: se puede ejecutar dos veces sin error.
-- Pegar completo en el SQL Editor de Supabase.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Liberación de cupos retirados en VINCULACION
--    Un retiro conserva el cupo por defecto; el coordinador puede
--    liberarlo UNA sola vez, con justificación, para un reemplazo.
-- ------------------------------------------------------------
ALTER TABLE VINCULACION ADD COLUMN IF NOT EXISTS cupo_liberado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE VINCULACION ADD COLUMN IF NOT EXISTS cupo_liberado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL;
ALTER TABLE VINCULACION ADD COLUMN IF NOT EXISTS cupo_liberado_en TIMESTAMP;
ALTER TABLE VINCULACION ADD COLUMN IF NOT EXISTS motivo_liberacion_cupo TEXT;

-- Solo una vinculación RETIRADA puede tener el cupo liberado, siempre
-- con motivo (>= 10 caracteres), actor y fecha registrados.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_vinculacion_liberacion_cupo'
          AND conrelid = 'vinculacion'::regclass
    ) THEN
        ALTER TABLE VINCULACION ADD CONSTRAINT chk_vinculacion_liberacion_cupo
            CHECK (
                cupo_liberado = FALSE
                OR (
                    estado = 'retirado'
                    AND motivo_liberacion_cupo IS NOT NULL
                    AND CHAR_LENGTH(BTRIM(motivo_liberacion_cupo)) >= 10
                    AND cupo_liberado_por IS NOT NULL
                    AND cupo_liberado_en IS NOT NULL
                )
            );
    END IF;
END $$;

-- ------------------------------------------------------------
-- 2. Pausa y reactivación de vacantes de prácticas
--    activa = FALSE oculta la vacante a estudiantes y bloquea
--    postulaciones/consolidaciones sin devolver los cupos a la empresa.
-- ------------------------------------------------------------
ALTER TABLE VACANTES_PRACTICAS ADD COLUMN IF NOT EXISTS activa BOOLEAN NOT NULL DEFAULT TRUE;

-- ------------------------------------------------------------
-- 3. Estado 'Expirada' en las postulaciones de ambos procesos
--    Al cerrar un periodo, las postulaciones pendientes expiran.
--    Se reemplaza el CHECK de estado por uno con nombre estable.
-- ------------------------------------------------------------
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'postulaciones_vinculacion'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%estado%'
    LOOP
        EXECUTE format('ALTER TABLE POSTULACIONES_VINCULACION DROP CONSTRAINT %I', c.conname);
    END LOOP;
    ALTER TABLE POSTULACIONES_VINCULACION ADD CONSTRAINT chk_postulacion_vinculacion_estado
        CHECK (estado IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo', 'Expirada'));
END $$;

DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'postulaciones_meritocraticas'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%estado%'
    LOOP
        EXECUTE format('ALTER TABLE POSTULACIONES_MERITOCRATICAS DROP CONSTRAINT %I', c.conname);
    END LOOP;
    ALTER TABLE POSTULACIONES_MERITOCRATICAS ADD CONSTRAINT chk_postulacion_merito_estado
        CHECK (estado IN ('Pendiente', 'Procesado', 'Aprobado', 'Rechazado', 'Expirada'));
END $$;

-- ============================================================
-- CONSULTAS DE VALIDACIÓN (todas deben devolver 0 filas inválidas)
-- ============================================================

-- 4.1 Vinculaciones con liberación inconsistente (debe ser 0)
SELECT COUNT(*) AS vinculaciones_liberacion_invalida
FROM VINCULACION
WHERE cupo_liberado = TRUE
  AND (
      estado <> 'retirado'
      OR motivo_liberacion_cupo IS NULL
      OR CHAR_LENGTH(BTRIM(motivo_liberacion_cupo)) < 10
      OR cupo_liberado_por IS NULL
      OR cupo_liberado_en IS NULL
  );

-- 4.2 Vacantes sin bandera de activación (debe ser 0)
SELECT COUNT(*) AS vacantes_sin_bandera_activa
FROM VACANTES_PRACTICAS
WHERE activa IS NULL;

-- 4.3 Postulaciones con estados fuera del catálogo (debe ser 0)
SELECT COUNT(*) AS postulaciones_vinculacion_estado_invalido
FROM POSTULACIONES_VINCULACION
WHERE estado NOT IN ('Pendiente', 'Aprobado', 'Rechazado', 'Sin cupo', 'Expirada');

SELECT COUNT(*) AS postulaciones_merito_estado_invalido
FROM POSTULACIONES_MERITOCRATICAS
WHERE estado NOT IN ('Pendiente', 'Procesado', 'Aprobado', 'Rechazado', 'Expirada');

-- 4.4 Resumen informativo de periodos y su estado
SELECT codigo, estado, fecha_inicio, fecha_fin
FROM PERIODOS_ACADEMICOS
ORDER BY fecha_inicio DESC;

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase45_cola_correos.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ==============================================================================
-- FASE 45: Cola de Correos Persistente
-- ==============================================================================
-- Crea la tabla para gestionar el envío de correos de forma asíncrona y resiliente,
-- permitiendo reintentos y visualización de errores.
--
-- Ejecución: Idempotente. Pegar en el SQL Editor de Supabase.
-- ==============================================================================

-- 1. Crear tabla CORREOS_EN_COLA
CREATE TABLE IF NOT EXISTS public.correos_en_cola (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    destinatario VARCHAR(150) NOT NULL,
    asunto VARCHAR(255) NOT NULL,
    cuerpo_html TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    intentos INTEGER NOT NULL DEFAULT 0,
    ultimo_error TEXT,
    fecha_creacion TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 2. Añadir Constraint de Estado (Idempotente)
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'chk_correos_estado' 
        AND conrelid = 'public.correos_en_cola'::regclass
    ) THEN
        ALTER TABLE public.correos_en_cola
        ADD CONSTRAINT chk_correos_estado 
        CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO'));
    END IF;
END $$;

-- 3. Índices para búsqueda rápida del Worker
CREATE INDEX IF NOT EXISTS idx_correos_estado_pendientes 
ON public.correos_en_cola(estado) 
WHERE estado IN ('PENDIENTE', 'FALLIDO');

CREATE INDEX IF NOT EXISTS idx_correos_fecha_creacion
ON public.correos_en_cola(fecha_creacion DESC);

-- ────────────────────────────────────────────────────────────────────────────
-- ORIGEN: fase47_liberar_cupo_practica.sql
-- ────────────────────────────────────────────────────────────────────────────
-- ============================================================
-- FASE 47 — Liberación del cupo de empresa de una práctica retirada
-- Espejo de la fase 42 (liberación de cupos en VINCULACION).
-- Script idempotente: se puede ejecutar dos veces sin error.
-- Pegar completo en el SQL Editor de Supabase.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Liberación de cupos retirados en PRACTICAS
--    Un retiro conserva el cupo de la empresa por defecto (los cupos
--    ocupados se derivan contando las prácticas del periodo); gestión
--    académica puede liberarlo UNA sola vez, con justificación, para
--    asignar un reemplazo dentro del mismo periodo. Una práctica con
--    cupo_liberado = TRUE deja de contar como cupo ocupado.
-- ------------------------------------------------------------
ALTER TABLE PRACTICAS ADD COLUMN IF NOT EXISTS cupo_liberado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE PRACTICAS ADD COLUMN IF NOT EXISTS cupo_liberado_por INT REFERENCES USUARIOS(id) ON DELETE SET NULL;
ALTER TABLE PRACTICAS ADD COLUMN IF NOT EXISTS cupo_liberado_en TIMESTAMP;
ALTER TABLE PRACTICAS ADD COLUMN IF NOT EXISTS motivo_liberacion_cupo TEXT;

-- Solo una práctica RETIRADA puede tener el cupo liberado, siempre
-- con motivo (>= 10 caracteres), actor y fecha registrados.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_practica_liberacion_cupo'
          AND conrelid = 'practicas'::regclass
    ) THEN
        ALTER TABLE PRACTICAS ADD CONSTRAINT chk_practica_liberacion_cupo
            CHECK (
                cupo_liberado = FALSE
                OR (
                    estado = 'retirado'
                    AND motivo_liberacion_cupo IS NOT NULL
                    AND CHAR_LENGTH(BTRIM(motivo_liberacion_cupo)) >= 10
                    AND cupo_liberado_por IS NOT NULL
                    AND cupo_liberado_en IS NOT NULL
                )
            );
    END IF;
END $$;

-- ============================================================
-- CONSULTAS DE VALIDACIÓN
-- ============================================================

-- 2.1 Prácticas con liberación inconsistente (debe ser 0)
SELECT COUNT(*) AS practicas_liberacion_invalida
FROM PRACTICAS
WHERE cupo_liberado = TRUE
  AND (
      estado <> 'retirado'
      OR motivo_liberacion_cupo IS NULL
      OR CHAR_LENGTH(BTRIM(motivo_liberacion_cupo)) < 10
      OR cupo_liberado_por IS NULL
      OR cupo_liberado_en IS NULL
  );

-- 2.2 Columnas nuevas presentes (debe devolver 4 filas)
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'practicas'
  AND column_name IN ('cupo_liberado', 'cupo_liberado_por', 'cupo_liberado_en', 'motivo_liberacion_cupo')
ORDER BY column_name;

-- --------------------------------------------------------------------------
-- ORIGEN: fase48_expediente_estudiante_unico.sql
-- --------------------------------------------------------------------------
-- ============================================================
-- FASE 48 - Un expediente de estudiante por cuenta de usuario
-- Script idempotente para ejecutar en el SQL Editor de Supabase.
-- No elimina ni modifica datos existentes.
-- ============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM ESTUDIANTES
        GROUP BY usuario_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Existen cuentas de usuario asociadas a mas de un expediente de estudiante. Corrige los duplicados antes de aplicar la constraint.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = 'estudiantes'::regclass
          AND c.contype = 'u'
          AND (
              SELECT ARRAY_AGG(a.attname::TEXT ORDER BY u.ordinality)
              FROM UNNEST(c.conkey) WITH ORDINALITY AS u(attnum, ordinality)
              JOIN pg_attribute a
                ON a.attrelid = c.conrelid
               AND a.attnum = u.attnum
          ) = ARRAY['usuario_id']::TEXT[]
    ) THEN
        ALTER TABLE ESTUDIANTES
            ADD CONSTRAINT estudiantes_usuario_id_uk UNIQUE (usuario_id);
    END IF;
END $$;

-- Debe devolver cero filas.
SELECT usuario_id, COUNT(*) AS expedientes
FROM ESTUDIANTES
GROUP BY usuario_id
HAVING COUNT(*) > 1;

-- Debe devolver una fila para usuario_id.
SELECT c.conname AS constraint_name,
       pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c
WHERE c.conrelid = 'estudiantes'::regclass
  AND c.contype = 'u'
  AND pg_get_constraintdef(c.oid) ILIKE '%(usuario_id)%';

-- --------------------------------------------------------------------------
-- ORIGEN: fase49_bitacora_requiere_correccion_notificacion.sql
-- --------------------------------------------------------------------------
-- Fase 49: notificacion de "bitacora requiere correccion" (2026-07-20)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - completar el flujo del estado 'requiere_correccion' de BITACORAS: hasta
--   ahora el estado existia en la entidad/validacion pero no generaba ninguna
--   notificacion al estudiante (a diferencia de 'rechazada', que si la tiene).
-- - agregar el tipo 'bitacora_requiere_correccion' al CHECK de NOTIFICACIONES.
--
-- Script idempotente: puede ejecutarse dos veces sin error. El constraint se
-- elimina y se vuelve a crear (no basta un "IF NOT EXISTS" simple, porque el
-- constraint ya existe con la lista vieja y quedaria sin el valor nuevo).

BEGIN;

ALTER TABLE NOTIFICACIONES
    DROP CONSTRAINT IF EXISTS chk_notificaciones_tipo;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notificaciones_tipo'
    ) THEN
        ALTER TABLE NOTIFICACIONES
        ADD CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN (
            'documento_aprobado',
            'documento_rechazado',
            'postulacion_enviada',
            'postulacion_resuelta',
            'asignacion_practica',
            'asignacion_vinculacion',
            'bitacora_rechazada',
            'bitacora_requiere_correccion',
            'nota_registrada',
            'encuesta_habilitada',
            'expediente_por_cerrar',
            'expediente_cerrado'
        ));
    END IF;
END $$;

COMMIT;

-- Validacion final: debe devolver 0 filas.
SELECT id, usuario_destino_id, tipo
FROM NOTIFICACIONES
WHERE tipo NOT IN (
    'documento_aprobado',
    'documento_rechazado',
    'postulacion_enviada',
    'postulacion_resuelta',
    'asignacion_practica',
    'asignacion_vinculacion',
    'bitacora_rechazada',
    'bitacora_requiere_correccion',
    'nota_registrada',
    'encuesta_habilitada',
    'expediente_por_cerrar',
    'expediente_cerrado'
);

-- --------------------------------------------------------------------------
-- ORIGEN: fase50_notas_coordinacion.sql
-- --------------------------------------------------------------------------
-- Fase 50: notas de coordinacion (constancia libre del coordinador visible al estudiante) (2026-07-20)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - permitir que el coordinador (o admin) deje una nota de texto libre por
--   expediente (practica O vinculacion, XOR), append-only, sin editar/eliminar.
-- - el estudiante la ve en su expediente academico y recibe notificacion+correo.
--
-- Script idempotente: puede ejecutarse dos veces sin error.

CREATE TABLE IF NOT EXISTS NOTAS_COORDINACION (
    id               SERIAL PRIMARY KEY,
    estudiante_id    INT NOT NULL REFERENCES ESTUDIANTES(id) ON DELETE CASCADE,
    practica_id      INT REFERENCES PRACTICAS(id) ON DELETE CASCADE,
    vinculacion_id   INT REFERENCES VINCULACION(id) ON DELETE CASCADE,
    autor_id         INT NOT NULL REFERENCES USUARIOS(id),
    mensaje          TEXT NOT NULL,
    fecha_creacion   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- XOR: exactamente una de practica_id/vinculacion_id, igual criterio que
-- BITACORAS/ASISTENCIAS.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notas_coordinacion_expediente'
    ) THEN
        ALTER TABLE NOTAS_COORDINACION
        ADD CONSTRAINT chk_notas_coordinacion_expediente
        CHECK (
            (practica_id IS NOT NULL AND vinculacion_id IS NULL)
            OR (practica_id IS NULL AND vinculacion_id IS NOT NULL)
        );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_notas_coordinacion_estudiante
ON NOTAS_COORDINACION(estudiante_id);

-- Agregar 'nota_coordinacion' al vocabulario de NOTIFICACIONES (mismo patron
-- DROP+ADD de fase49, porque el "IF NOT EXISTS" simple dejaria el constraint
-- viejo intacto).
ALTER TABLE NOTIFICACIONES
    DROP CONSTRAINT IF EXISTS chk_notificaciones_tipo;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notificaciones_tipo'
    ) THEN
        ALTER TABLE NOTIFICACIONES
        ADD CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN (
            'documento_aprobado',
            'documento_rechazado',
            'postulacion_enviada',
            'postulacion_resuelta',
            'asignacion_practica',
            'asignacion_vinculacion',
            'bitacora_rechazada',
            'bitacora_requiere_correccion',
            'nota_registrada',
            'nota_coordinacion',
            'encuesta_habilitada',
            'expediente_por_cerrar',
            'expediente_cerrado'
        ));
    END IF;
END $$;

-- Validacion final: debe devolver 0 filas.
SELECT id, estudiante_id, practica_id, vinculacion_id
FROM NOTAS_COORDINACION
WHERE (practica_id IS NOT NULL) = (vinculacion_id IS NOT NULL);

SELECT id, usuario_destino_id, tipo
FROM NOTIFICACIONES
WHERE tipo NOT IN (
    'documento_aprobado',
    'documento_rechazado',
    'postulacion_enviada',
    'postulacion_resuelta',
    'asignacion_practica',
    'asignacion_vinculacion',
    'bitacora_rechazada',
    'bitacora_requiere_correccion',
    'nota_registrada',
    'nota_coordinacion',
    'encuesta_habilitada',
    'expediente_por_cerrar',
    'expediente_cerrado'
);

-- --------------------------------------------------------------------------
-- ORIGEN: fase51_notificacion_expediente_atrasado.sql
-- --------------------------------------------------------------------------
-- Fase 51: notificacion automatica de atraso al estudiante (2026-07-20)
-- Ejecutar en el SQL Editor de Supabase.
--
-- Objetivo:
-- - agregar el tipo 'expediente_atrasado' al vocabulario de NOTIFICACIONES,
--   usado por el job diario (AlertaAtrasoScheduler) que avisa al estudiante
--   por correo+sistema cuando lleva 14+ dias sin actividad en bitacoras,
--   sin que el coordinador tenga que notificarlo manualmente.
--
-- Script idempotente: puede ejecutarse dos veces sin error.

ALTER TABLE NOTIFICACIONES
    DROP CONSTRAINT IF EXISTS chk_notificaciones_tipo;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notificaciones_tipo'
    ) THEN
        ALTER TABLE NOTIFICACIONES
        ADD CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN (
            'documento_aprobado',
            'documento_rechazado',
            'postulacion_enviada',
            'postulacion_resuelta',
            'asignacion_practica',
            'asignacion_vinculacion',
            'bitacora_rechazada',
            'bitacora_requiere_correccion',
            'nota_registrada',
            'nota_coordinacion',
            'expediente_atrasado',
            'encuesta_habilitada',
            'expediente_por_cerrar',
            'expediente_cerrado'
        ));
    END IF;
END $$;

-- Validacion final: debe devolver 0 filas.
SELECT id, usuario_destino_id, tipo
FROM NOTIFICACIONES
WHERE tipo NOT IN (
    'documento_aprobado',
    'documento_rechazado',
    'postulacion_enviada',
    'postulacion_resuelta',
    'asignacion_practica',
    'asignacion_vinculacion',
    'bitacora_rechazada',
    'bitacora_requiere_correccion',
    'nota_registrada',
    'nota_coordinacion',
    'expediente_atrasado',
    'encuesta_habilitada',
    'expediente_por_cerrar',
    'expediente_cerrado'
);
-- ============================================================================
-- FASE 48: SEGUIMIENTO BIDIRECCIONAL Y CONTINUIDAD ACADEMICA
-- Fuente: database/migraciones/fase48_seguimiento_continuidad.sql
-- ============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS COMENTARIOS_SEGUIMIENTO (
    id              SERIAL PRIMARY KEY,
    practica_id     INT,
    vinculacion_id  INT,
    autor_id        INT NOT NULL,
    audiencia       VARCHAR(20) NOT NULL,
    mensaje         TEXT NOT NULL,
    fecha_creacion  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comentario_seguimiento_practica FOREIGN KEY (practica_id) REFERENCES PRACTICAS(id),
    CONSTRAINT fk_comentario_seguimiento_vinculacion FOREIGN KEY (vinculacion_id) REFERENCES VINCULACION(id),
    CONSTRAINT fk_comentario_seguimiento_autor FOREIGN KEY (autor_id) REFERENCES USUARIOS(id),
    CONSTRAINT chk_comentario_seguimiento_expediente
        CHECK ((practica_id IS NOT NULL)::INT + (vinculacion_id IS NOT NULL)::INT = 1),
    CONSTRAINT chk_comentario_seguimiento_audiencia
        CHECK (audiencia IN ('ESTUDIANTE', 'TUTOR', 'COORDINACION', 'TODOS')),
    CONSTRAINT chk_comentario_seguimiento_mensaje
        CHECK (LENGTH(BTRIM(mensaje)) BETWEEN 5 AND 2000)
);

CREATE INDEX IF NOT EXISTS idx_comentarios_seguimiento_practica_fecha
    ON COMENTARIOS_SEGUIMIENTO(practica_id, fecha_creacion DESC) WHERE practica_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_comentarios_seguimiento_vinculacion_fecha
    ON COMENTARIOS_SEGUIMIENTO(vinculacion_id, fecha_creacion DESC) WHERE vinculacion_id IS NOT NULL;

ALTER TABLE PRACTICAS
    ADD COLUMN IF NOT EXISTS vacante_id INT,
    ADD COLUMN IF NOT EXISTS tipo_asignacion VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS practica_origen_id INT,
    ADD COLUMN IF NOT EXISTS asignacion_snapshot JSONB;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_practicas_vacante') THEN
        ALTER TABLE PRACTICAS ADD CONSTRAINT fk_practicas_vacante
            FOREIGN KEY (vacante_id) REFERENCES VACANTES_PRACTICAS(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_practicas_origen') THEN
        ALTER TABLE PRACTICAS ADD CONSTRAINT fk_practicas_origen
            FOREIGN KEY (practica_origen_id) REFERENCES PRACTICAS(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_practicas_tipo_asignacion') THEN
        ALTER TABLE PRACTICAS ADD CONSTRAINT chk_practicas_tipo_asignacion
            CHECK (tipo_asignacion IN ('LEGACY', 'MERITOCRACIA', 'DIRECTA', 'CONTINUIDAD'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_practicas_origen_distinto') THEN
        ALTER TABLE PRACTICAS ADD CONSTRAINT chk_practicas_origen_distinto
            CHECK (practica_origen_id IS NULL OR practica_origen_id <> id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_practicas_continuidad_origen') THEN
        ALTER TABLE PRACTICAS ADD CONSTRAINT chk_practicas_continuidad_origen
            CHECK (
                (tipo_asignacion = 'CONTINUIDAD' AND practica_origen_id IS NOT NULL)
                OR (tipo_asignacion <> 'CONTINUIDAD' AND practica_origen_id IS NULL)
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_practicas_vacante ON PRACTICAS(vacante_id) WHERE vacante_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_practicas_origen ON PRACTICAS(practica_origen_id) WHERE practica_origen_id IS NOT NULL;

COMMIT;

-- ============================================================================
-- FASE 49: TUTORES EXTERNOS POR EMPRESA Y CARRERA
-- Fuente: database/migraciones/fase49_tutores_externos_empresa.sql
-- ============================================================================

ALTER TABLE TUTORES_EMPRESA
    ADD COLUMN IF NOT EXISTS usuario_id INT,
    ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
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
    tutor_empresa_id INT NOT NULL REFERENCES TUTORES_EMPRESA(id) ON DELETE CASCADE,
    carrera_id INT NOT NULL REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    PRIMARY KEY (tutor_empresa_id, carrera_id)
);

CREATE INDEX IF NOT EXISTS idx_tutores_empresa_carreras_carrera
    ON TUTORES_EMPRESA_CARRERAS(carrera_id, tutor_empresa_id);

-- ============================================================================
-- FASE 52: NOTIFICACION AUTOMATICA DE ATRASO/FALTA DE ASISTENCIA
-- Fuente: database/migraciones/fase52_notificacion_asistencia_atraso_falta.sql
-- ============================================================================

ALTER TABLE NOTIFICACIONES
    DROP CONSTRAINT IF EXISTS chk_notificaciones_tipo;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_notificaciones_tipo'
    ) THEN
        ALTER TABLE NOTIFICACIONES
        ADD CONSTRAINT chk_notificaciones_tipo
        CHECK (tipo IN (
            'documento_aprobado',
            'documento_rechazado',
            'postulacion_enviada',
            'postulacion_resuelta',
            'asignacion_practica',
            'asignacion_vinculacion',
            'bitacora_rechazada',
            'bitacora_requiere_correccion',
            'nota_registrada',
            'nota_coordinacion',
            'expediente_atrasado',
            'encuesta_habilitada',
            'expediente_por_cerrar',
            'expediente_cerrado',
            'asistencia_atraso',
            'asistencia_falta'
        ));
    END IF;
END $$;

-- ============================================================================
-- FASE 55: TUTORES EXTERNOS POR FUNDACION Y CARRERA
-- Fuente: database/migraciones/fase55_tutores_externos_fundacion.sql
-- ============================================================================

CREATE TABLE IF NOT EXISTS TUTORES_FUNDACION (
    id SERIAL PRIMARY KEY,
    fundacion_id INT NOT NULL REFERENCES FUNDACIONES(id) ON DELETE RESTRICT,
    usuario_id INT REFERENCES USUARIOS(id) ON DELETE RESTRICT,
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
    tutor_fundacion_id INT NOT NULL REFERENCES TUTORES_FUNDACION(id) ON DELETE CASCADE,
    carrera_id INT NOT NULL REFERENCES CARRERAS(id) ON DELETE RESTRICT,
    PRIMARY KEY (tutor_fundacion_id, carrera_id)
);

CREATE INDEX IF NOT EXISTS idx_tutores_fundacion_carreras_carrera
    ON TUTORES_FUNDACION_CARRERAS(carrera_id, tutor_fundacion_id);
