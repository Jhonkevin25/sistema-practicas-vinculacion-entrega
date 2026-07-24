-- ============================================================
-- DIAGNOSTICO PREVIO AL REINICIO INTEGRAL DEL MVP
-- SOLO LECTURA: no modifica ninguna fila.
-- Ejecutar en Supabase SQL Editor antes de
-- reinicio_mvp_solo_admin.sql.
-- ============================================================

-- 1. Cuentas con rol ADMIN. Debe identificarse exactamente la cuenta
-- que se conservara antes de ejecutar el reinicio.
SELECT
    u.id,
    u.cedula,
    u.nombre,
    u.apellido,
    u.email,
    u.activo,
    u.primer_login,
    STRING_AGG(DISTINCT r.codigo, ', ' ORDER BY r.codigo) AS roles
FROM USUARIOS u
JOIN USUARIOS_ROLES ur ON ur.usuario_id = u.id
JOIN ROLES r ON r.id = ur.rol_id
WHERE EXISTS (
    SELECT 1
    FROM USUARIOS_ROLES ur_admin
    JOIN ROLES r_admin ON r_admin.id = ur_admin.rol_id
    WHERE ur_admin.usuario_id = u.id
      AND r_admin.codigo = 'ADMIN'
)
GROUP BY u.id, u.cedula, u.nombre, u.apellido, u.email,
         u.activo, u.primer_login
ORDER BY u.id;

-- 2. Inventario de datos que eliminara el reinicio.
SELECT grupo, tabla, filas
FROM (
    SELECT 1 AS orden, 'Identidades' AS grupo, 'USUARIOS no ADMIN' AS tabla,
           COUNT(*)::BIGINT AS filas
    FROM USUARIOS u
    WHERE NOT EXISTS (
        SELECT 1 FROM USUARIOS_ROLES ur
        JOIN ROLES r ON r.id = ur.rol_id
        WHERE ur.usuario_id = u.id AND r.codigo = 'ADMIN'
    )
    UNION ALL SELECT 2, 'Identidades', 'ESTUDIANTES', COUNT(*) FROM ESTUDIANTES
    UNION ALL SELECT 3, 'Identidades', 'COORDINADOR_CARRERAS', COUNT(*) FROM COORDINADOR_CARRERAS
    UNION ALL SELECT 4, 'Identidades', 'SESIONES', COUNT(*) FROM SESIONES
    UNION ALL SELECT 5, 'Identidades', 'TOKENS_RECUPERACION', COUNT(*) FROM TOKENS_RECUPERACION

    UNION ALL SELECT 10, 'Expedientes', 'PRACTICAS', COUNT(*) FROM PRACTICAS
    UNION ALL SELECT 11, 'Expedientes', 'VINCULACION', COUNT(*) FROM VINCULACION
    UNION ALL SELECT 12, 'Expedientes', 'POSTULACIONES_MERITOCRATICAS', COUNT(*) FROM POSTULACIONES_MERITOCRATICAS
    UNION ALL SELECT 13, 'Expedientes', 'POSTULACIONES_VINCULACION', COUNT(*) FROM POSTULACIONES_VINCULACION
    UNION ALL SELECT 14, 'Expedientes', 'BITACORAS', COUNT(*) FROM BITACORAS
    UNION ALL SELECT 15, 'Expedientes', 'ASISTENCIAS', COUNT(*) FROM ASISTENCIAS
    UNION ALL SELECT 16, 'Expedientes', 'EVALUACIONES_PRACTICAS_DETALLE', COUNT(*) FROM EVALUACIONES_PRACTICAS_DETALLE
    UNION ALL SELECT 17, 'Expedientes', 'ENCUESTAS_SATISFACCION', COUNT(*) FROM ENCUESTAS_SATISFACCION
    UNION ALL SELECT 18, 'Expedientes', 'DOCS_ESTUDIANTE', COUNT(*) FROM DOCS_ESTUDIANTE
    UNION ALL SELECT 19, 'Expedientes', 'NOTAS_ACADEMICAS', COUNT(*) FROM NOTAS_ACADEMICAS

    UNION ALL SELECT 20, 'Entidades', 'EMPRESAS', COUNT(*) FROM EMPRESAS
    UNION ALL SELECT 21, 'Entidades', 'FUNDACIONES', COUNT(*) FROM FUNDACIONES
    UNION ALL SELECT 22, 'Entidades', 'CONVENIOS', COUNT(*) FROM CONVENIOS
    UNION ALL SELECT 23, 'Entidades', 'VACANTES_PRACTICAS', COUNT(*) FROM VACANTES_PRACTICAS
    UNION ALL SELECT 24, 'Entidades', 'PROYECTOS', COUNT(*) FROM PROYECTOS
    UNION ALL SELECT 25, 'Entidades', 'OFERTAS_CUPOS_EMPRESA', COUNT(*) FROM OFERTAS_CUPOS_EMPRESA
    UNION ALL SELECT 26, 'Entidades', 'OFERTAS_CUPOS_FUNDACION', COUNT(*) FROM OFERTAS_CUPOS_FUNDACION

    UNION ALL SELECT 30, 'Configuracion operativa', 'PERIODOS_ACADEMICOS', COUNT(*) FROM PERIODOS_ACADEMICOS
    UNION ALL SELECT 31, 'Configuracion operativa', 'FECHAS_CONVOCATORIA', COUNT(*) FROM FECHAS_CONVOCATORIA
    UNION ALL SELECT 32, 'Configuracion operativa', 'FECHAS_LIMITE_CALIFICACION', COUNT(*) FROM FECHAS_LIMITE_CALIFICACION

    UNION ALL SELECT 40, 'Operacion', 'IMPORTACIONES', COUNT(*) FROM IMPORTACIONES
    UNION ALL SELECT 41, 'Operacion', 'NOTIFICACIONES', COUNT(*) FROM NOTIFICACIONES
    UNION ALL SELECT 42, 'Operacion', 'CORREOS_EN_COLA', COUNT(*) FROM CORREOS_EN_COLA
    UNION ALL SELECT 43, 'Operacion', 'AUDITORIA', COUNT(*) FROM AUDITORIA
) inventario
ORDER BY orden;

-- 3. Estos catalogos se conservan deliberadamente.
SELECT 'CARRERAS' AS tabla_conservada, COUNT(*)::BIGINT AS filas FROM CARRERAS
UNION ALL
SELECT 'DOCUMENTOS_REQUERIDOS', COUNT(*) FROM DOCUMENTOS_REQUERIDOS
UNION ALL
SELECT 'ROLES', COUNT(*) FROM ROLES
UNION ALL
SELECT 'MODULOS', COUNT(*) FROM MODULOS
UNION ALL
SELECT 'PERMISOS', COUNT(*) FROM PERMISOS;

-- 4. Los objetos de Storage no se borran con las filas de DOCS_ESTUDIANTE.
-- Si esta consulta devuelve objetos, eliminarlos manualmente del bucket
-- privado "documentos" despues del reinicio de la base.
SELECT bucket_id, COUNT(*)::BIGINT AS objetos
FROM storage.objects
WHERE bucket_id = 'documentos'
GROUP BY bucket_id;
