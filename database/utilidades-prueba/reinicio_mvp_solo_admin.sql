-- ============================================================
-- REINICIO INTEGRAL DE DATOS DE PRUEBA DEL MVP
-- Conserva:
--   * una unica cuenta ADMIN, identificada por email;
--   * ROLES, MODULOS, PERMISOS y su matriz;
--   * CARRERAS;
--   * DOCUMENTOS_REQUERIDOS.
-- Elimina datos operativos, expedientes, entidades, periodos y usuarios
-- no administradores. NUNCA ejecutar en produccion.
-- ============================================================
-- PRECONDICIONES:
-- 1. Crear un respaldo de Supabase.
-- 2. Detener backend y frontend para evitar escrituras concurrentes.
-- 3. Ejecutar reinicio_mvp_solo_admin_preflight.sql y revisar conteos.
-- 4. Confirmar el email del ADMIN que se conservara.
-- 5. Reemplazar PENDIENTE_DE_CONFIRMACION por la frase indicada abajo.
-- ============================================================

BEGIN;

CREATE TEMP TABLE _reinicio_config (
    admin_email VARCHAR(150) NOT NULL,
    confirmacion VARCHAR(80) NOT NULL
) ON COMMIT DROP;

INSERT INTO _reinicio_config (admin_email, confirmacion)
VALUES (
    'admin@sistema.edu.ec',
    'PENDIENTE_DE_CONFIRMACION'
    -- Cambiar exactamente a: REINICIAR_MVP_SOLO_ADMIN
);

DO $$
DECLARE
    email_admin TEXT;
    confirmacion_actual TEXT;
    administradores_coincidentes INT;
BEGIN
    SELECT admin_email, confirmacion
    INTO email_admin, confirmacion_actual
    FROM _reinicio_config;

    IF confirmacion_actual <> 'REINICIAR_MVP_SOLO_ADMIN' THEN
        RAISE EXCEPTION
            'Reinicio bloqueado. Revisa el preflight y escribe la frase de confirmacion exacta.';
    END IF;

    SELECT COUNT(*)
    INTO administradores_coincidentes
    FROM USUARIOS u
    WHERE LOWER(u.email) = LOWER(email_admin)
      AND u.activo = TRUE
      AND EXISTS (
          SELECT 1
          FROM USUARIOS_ROLES ur
          JOIN ROLES r ON r.id = ur.rol_id
          WHERE ur.usuario_id = u.id
            AND r.codigo = 'ADMIN'
      );

    IF administradores_coincidentes <> 1 THEN
        RAISE EXCEPTION
            'Debe existir exactamente un ADMIN activo con el email %. Coincidencias: %.',
            email_admin, administradores_coincidentes;
    END IF;
END $$;

CREATE TEMP TABLE _admin_conservado ON COMMIT DROP AS
SELECT u.id
FROM USUARIOS u
JOIN _reinicio_config c ON LOWER(c.admin_email) = LOWER(u.email)
WHERE u.activo = TRUE
  AND EXISTS (
      SELECT 1
      FROM USUARIOS_ROLES ur
      JOIN ROLES r ON r.id = ur.rol_id
      WHERE ur.usuario_id = u.id
        AND r.codigo = 'ADMIN'
  );

-- 1. Mensajeria, sesion e importaciones.
DELETE FROM CORREOS_EN_COLA;
DELETE FROM NOTIFICACIONES;
DELETE FROM TOKENS_RECUPERACION;
DELETE FROM SESIONES;
DELETE FROM IMPORTACIONES;

-- 2. Evidencias y actividad academica dependiente.
DELETE FROM ENCUESTAS_SATISFACCION;
DELETE FROM EVALUACIONES_PRACTICAS_DETALLE;
DELETE FROM EVALUACIONES_PRACTICAS_DETALLE_DUP_FASE7;
DELETE FROM BITACORAS;
DELETE FROM ASISTENCIAS;
DELETE FROM FAVORITOS_VACANTES;
DELETE FROM POSTULACIONES_MERITOCRATICAS;
DELETE FROM POSTULACIONES_VINCULACION;
DELETE FROM DOCS_ESTUDIANTE;
DELETE FROM NOTAS_ACADEMICAS;
DELETE FROM EVALUACIONES_PRACTICAS;
DELETE FROM DOCUMENTOS_PRACTICAS;
DELETE FROM EVAL_VINCULACION;
DELETE FROM DOCS_VINCULACION;

-- 3. Expedientes y oferta academica.
DELETE FROM PRACTICAS;
DELETE FROM VINCULACION;
DELETE FROM VACANTES_PRACTICAS;
DELETE FROM PROYECTOS_CARRERAS;
DELETE FROM PROYECTOS;
DELETE FROM OFERTAS_CUPOS_EMPRESA_CARRERAS;
DELETE FROM OFERTAS_CUPOS_EMPRESA;
DELETE FROM OFERTAS_CUPOS_FUNDACION_CARRERAS;
DELETE FROM OFERTAS_CUPOS_FUNDACION;

-- 4. Entidades receptoras y convenios.
DELETE FROM CONVENIOS_CARRERAS;
DELETE FROM CONVENIOS;
DELETE FROM TUTORES_EMPRESA;
DELETE FROM EMPRESAS;
DELETE FROM FUNDACIONES;

-- 5. Configuracion que debe reconstruirse en la prueba desde cero.
DELETE FROM FECHAS_CONVOCATORIA;
DELETE FROM FECHAS_LIMITE_CALIFICACION;

-- 6. Identidades academicas y alcances.
DELETE FROM COORDINADOR_CARRERAS;
DELETE FROM ESTUDIANTES;

-- AUDITORIA no tiene ON DELETE CASCADE hacia USUARIOS. Se vacia antes de
-- eliminar identidades y otra vez al final por los triggers de auditoria.
DELETE FROM AUDITORIA;

-- Evita una FK sin ON DELETE si el rol del ADMIN fue asignado por una
-- cuenta que se eliminara.
UPDATE USUARIOS_ROLES
SET asignado_por = NULL
WHERE asignado_por NOT IN (SELECT id FROM _admin_conservado);

DELETE FROM USUARIOS
WHERE id NOT IN (SELECT id FROM _admin_conservado);

-- La cuenta conservada queda exclusivamente con rol ADMIN.
DELETE FROM USUARIOS_ROLES ur
USING ROLES r
WHERE ur.rol_id = r.id
  AND ur.usuario_id IN (SELECT id FROM _admin_conservado)
  AND r.codigo <> 'ADMIN';

-- Puede haber asignaciones historicas duplicadas del mismo rol. Se conserva
-- una sola fila ADMIN para que el reinicio deje una identidad consistente.
DELETE FROM USUARIOS_ROLES duplicado
USING USUARIOS_ROLES conservado
WHERE duplicado.usuario_id IN (SELECT id FROM _admin_conservado)
  AND duplicado.usuario_id = conservado.usuario_id
  AND duplicado.rol_id = conservado.rol_id
  AND duplicado.id > conservado.id;

INSERT INTO USUARIOS_ROLES (usuario_id, rol_id, asignado_por)
SELECT a.id, r.id, a.id
FROM _admin_conservado a
JOIN ROLES r ON r.codigo = 'ADMIN'
WHERE NOT EXISTS (
    SELECT 1
    FROM USUARIOS_ROLES ur
    WHERE ur.usuario_id = a.id
      AND ur.rol_id = r.id
);

UPDATE USUARIOS
SET activo = TRUE,
    intentos_login_fallidos = 0,
    bloqueado_hasta = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (SELECT id FROM _admin_conservado);

-- Los periodos ya no tienen referencias despues de los bloques anteriores.
DELETE FROM PERIODOS_ACADEMICOS;

-- Los triggers pueden auditar las eliminaciones; se limpia al final para que
-- la nueva prueba empiece con historial vacio.
DELETE FROM AUDITORIA;

COMMIT;

-- ============================================================
-- VERIFICACION POSTERIOR
-- Debe devolver una sola cuenta con rol ADMIN y todos los conteos en cero.
-- ============================================================
SELECT
    u.id,
    u.cedula,
    u.nombre,
    u.apellido,
    u.email,
    u.activo,
    STRING_AGG(r.codigo, ', ' ORDER BY r.codigo) AS roles
FROM USUARIOS u
JOIN USUARIOS_ROLES ur ON ur.usuario_id = u.id
JOIN ROLES r ON r.id = ur.rol_id
GROUP BY u.id, u.cedula, u.nombre, u.apellido, u.email, u.activo;

SELECT tabla, filas
FROM (
    SELECT 1 AS orden, 'USUARIOS_NO_ADMIN' AS tabla,
           COUNT(*)::BIGINT AS filas
    FROM USUARIOS u
    WHERE NOT EXISTS (
        SELECT 1 FROM USUARIOS_ROLES ur
        JOIN ROLES r ON r.id = ur.rol_id
        WHERE ur.usuario_id = u.id AND r.codigo = 'ADMIN'
    )
    UNION ALL SELECT 2, 'ESTUDIANTES', COUNT(*) FROM ESTUDIANTES
    UNION ALL SELECT 3, 'PRACTICAS', COUNT(*) FROM PRACTICAS
    UNION ALL SELECT 4, 'VINCULACION', COUNT(*) FROM VINCULACION
    UNION ALL SELECT 5, 'EMPRESAS', COUNT(*) FROM EMPRESAS
    UNION ALL SELECT 6, 'FUNDACIONES', COUNT(*) FROM FUNDACIONES
    UNION ALL SELECT 7, 'VACANTES_PRACTICAS', COUNT(*) FROM VACANTES_PRACTICAS
    UNION ALL SELECT 8, 'PROYECTOS', COUNT(*) FROM PROYECTOS
    UNION ALL SELECT 9, 'PERIODOS_ACADEMICOS', COUNT(*) FROM PERIODOS_ACADEMICOS
    UNION ALL SELECT 10, 'AUDITORIA', COUNT(*) FROM AUDITORIA
) verificacion
ORDER BY orden;
