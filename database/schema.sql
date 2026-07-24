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
    usuario_id          INT UNIQUE NOT NULL REFERENCES USUARIOS(id) ON DELETE CASCADE,
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
    actividad           TEXT NOT NULL,
    horas               INT NOT NULL CHECK (horas BETWEEN 1 AND 24),
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
    SELECT LEAST(horas_requeridas_expediente, COALESCE(SUM(b.horas), 0)::INT)
    FROM BITACORAS b
    WHERE b.practica_id = expediente_id AND b.estado = 'aprobada';
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION fn_horas_aprobadas_vinculacion(
    expediente_id INT,
    horas_requeridas_expediente INT
)
RETURNS INT AS $$
    SELECT LEAST(horas_requeridas_expediente, COALESCE(SUM(b.horas), 0)::INT)
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
    AFTER INSERT OR UPDATE OF horas, estado, practica_id, vinculacion_id OR DELETE
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
