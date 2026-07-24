-- ============================================================================
-- DEMO — DATOS MASIVOS FICTICIOS PARA PRESENTACIÓN (134 ESTUDIANTES)
-- Ejecutar en el SQL Editor de Supabase. Es idempotente: puede re-ejecutarse
-- sin duplicar filas (cada bloque se protege con ON CONFLICT / IF NOT EXISTS).
-- NUNCA ejecutar contra una base de producción real con datos de personas
-- reales: este script es exclusivamente para el entorno de demo/tesis.
-- ============================================================================
-- Qué genera (todo 100% ficticio, sin datos reales de personas):
--   1. PERIODOS_ACADEMICOS históricos CERRADOS (2025-1, 2025-2, 2026-1) para
--      dar profundidad temporal a la demo; reutiliza el periodo ACTIVO
--      existente (o crea 2026-2 si no hay ninguno activo).
--   2. 12 EMPRESAS ficticias (RUC de 13 dígitos con prefijo 999, no colisiona
--      con RUC reales que empiezan con código de provincia 01-24) repartidas
--      en las 6 carreras ya existentes en el catálogo (no se agrega ninguna
--      carrera nueva).
--   3. 6 FUNDACIONES + 6 PROYECTOS de vinculación ficticios (RUC prefijo 998),
--      uno por carrera, enlazados a PROYECTOS_CARRERAS.
--   4. 18 TUTORES ficticios (usuarios con rol TUTOR): 12 tutores de empresa
--      (tutor_tipo='PRACTICAS', enlazados a TUTORES_EMPRESA /
--      TUTORES_EMPRESA_CARRERAS) y 6 tutores de vinculación
--      (tutor_tipo='VINCULACION', referenciados directo en VINCULACION.tutor_id).
--   5. 24 VACANTES_PRACTICAS (Práctica I y II) para que las empresas no se
--      vean vacías en el catálogo de vacantes.
--   6. 134 ESTUDIANTES (usuario + registro académico) distribuidos en las 6
--      carreras existentes, con su expediente en distintas etapas del flujo Vinculación
--      (160h) -> Práctica I (120h) -> Práctica II (120h):
--        - 20 (~15%)  recién iniciando Vinculación (pendiente / en_curso)
--        - 34 (~25%)  Vinculación completada, Práctica I en curso
--        - 40 (~30%)  Vinculación + Práctica I completadas, Práctica II en curso
--        - 40 (~30%)  las 3 etapas completadas ("egresados" del flujo)
--      Cada etapa completada (o parcial cerrado) genera sus filas en
--      EVALUACIONES_PRACTICAS_DETALLE (notaTutor/notaCoord 7.0-9.5,
--      notaFinal = promedio 50/50, encuestaCompletada=true solo si ambas
--      notas existen) y su ENCUESTAS_SATISFACCION correspondiente.
--
-- Marca / identificación para limpieza posterior:
--   - USUARIOS.cedula LIKE 'DEMO-EST-%'  -> estudiantes
--   - USUARIOS.cedula LIKE 'DEMO-TUT-%'  -> tutores (empresa y vinculación)
--   - USUARIOS.email  LIKE '%@unibe-demo.edu.ec'
--   - ESTUDIANTES.matricula LIKE 'DEMO-MAT-%'
--   - EMPRESAS.nombre / FUNDACIONES.nombre / PROYECTOS.nombre /
--     VACANTES_PRACTICAS.nombre LIKE 'DEMO %'
--   Los nombres/apellidos de personas son genéricos en español (ficticios,
--   sin prefijo visible a propósito, para que la demo se vea natural), pero
--   quedan 100% identificables por cedula/email/matricula con el patrón DEMO.
--
-- Contraseña de demo para TODOS los usuarios creados por este script:
--   Demo2026*
-- (hasheada con pgcrypto/bcrypt igual que fase4_usuarios_prueba.sql, cada
-- fila con su propia sal — todas validan contra el mismo texto plano).
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Periodos históricos cerrados, para dar profundidad temporal a la demo
INSERT INTO PERIODOS_ACADEMICOS (codigo, fecha_inicio, fecha_fin, estado) VALUES
    ('2025-1', '2025-01-01', '2025-06-30', 'CERRADO'),
    ('2025-2', '2025-07-01', '2025-12-31', 'CERRADO'),
    ('2026-1', '2026-01-01', '2026-06-30', 'CERRADO')
ON CONFLICT (codigo) DO NOTHING;

-- ============================================================================
-- Bloque principal (PL/pgSQL): empresas, fundaciones/proyectos, tutores y
-- los 134 estudiantes con su expediente académico completo.
-- ============================================================================
DO $$
DECLARE
    v_periodo                      VARCHAR(20);
    v_fecha_limite_vacante          DATE;
    v_rol_estudiante_id             INT;
    v_rol_tutor_id                  INT;

    carreras_arr   TEXT[] := ARRAY['Derecho','Ingeniería en Software','Psicología','Medicina','Enfermería','Odontología'];
    area_arr       TEXT[] := ARRAY['Legal','Tecnología','Salud Mental','Salud','Salud','Salud Oral'];
    ciudad_arr     TEXT[] := ARRAY['Quito','Quito','Guayaquil','Quito','Cuenca','Quito'];
    nombres_arr    TEXT[] := ARRAY['Ana','Luis','María','Carlos','Sofía','Diego','Valentina','Andrés','Camila','Javier','Daniela','Fernando','Gabriela','Ricardo','Paula','Esteban','Lucía','Mateo','Isabella','Sebastián'];
    apellidos_arr  TEXT[] := ARRAY['Torres','Ramírez','Vega','Cabrera','Salazar','Cevallos','Paredes','Mendoza','Andrade','Yépez','Cárdenas','Suárez','Rivadeneira','Quintero','Espinoza','Loor','Guerrero','Naranjo','Aguirre','Zambrano'];

    empresa_ids                 INT[] := ARRAY[]::INT[];
    empresa_primaria_ids        INT[] := ARRAY[]::INT[];
    tutor_empresa_usuario_ids   INT[] := ARRAY[]::INT[];
    tutor_empresa_por_carrera   INT[] := ARRAY[]::INT[];
    fundacion_ids               INT[] := ARRAY[]::INT[];
    proyecto_ids                INT[] := ARRAY[]::INT[];
    tutor_vinc_ids               INT[] := ARRAY[]::INT[];

    v_carrera_idx   INT;
    v_carrera       TEXT;
    v_carrera_id    INT;

    v_ruc           VARCHAR(13);
    v_nombre_emp    TEXT;
    v_empresa_id    INT;

    v_tutor_cedula      VARCHAR(20);
    v_tutor_usuario_id  INT;
    v_tutor_empresa_id  INT;
    v_nombre_tutor      TEXT;
    v_apellido_tutor    TEXT;

    v_nombre_vacante TEXT;
    v_modalidad      TEXT;

    v_ruc_f        VARCHAR(13);
    v_nombre_f     TEXT;
    v_fundacion_id INT;

    v_nombre_proy TEXT;
    v_proyecto_id INT;

    v_tutor_v_cedula     VARCHAR(20);
    v_tutor_v_usuario_id INT;

    v_cedula     VARCHAR(20);
    v_nombre     TEXT;
    v_apellido   TEXT;
    v_email      TEXT;
    v_usuario_id INT;

    v_matricula     VARCHAR(20);
    v_semestre      INT;
    v_bucket        INT;
    v_estudiante_id INT;

    v_estado_v    TEXT;
    v_horas_v     INT;
    v_fecha_ini_v DATE;
    v_fecha_fin_v DATE;
    v_periodo_v   TEXT;
    v_vinculacion_id INT;

    v_nota_t      NUMERIC(4,2);
    v_nota_c      NUMERIC(4,2);
    v_nota_final  NUMERIC(4,2);

    v_horas_p1 INT;
    v_horas_p2 INT;
    v_practica1_id INT;
    v_practica2_id INT;
    v_cerrados INT;

    v_fecha_ini_p1 DATE;
    v_fecha_fin_p1 DATE;
    v_periodo_p1   TEXT;
    v_fecha_ini_p2 DATE;
    v_fecha_fin_p2 DATE;
    v_periodo_p2   TEXT;

    v_tutor_practicas_id INT;
    v_tutor_vinc_id      INT;
BEGIN
    -- ------------------------------------------------------------------
    -- Periodo activo: reutiliza el que ya esté ACTIVO; si no hay ninguno,
    -- crea 2026-2 (mismo periodo usado en fase4_usuarios_prueba.sql).
    -- ------------------------------------------------------------------
    SELECT codigo INTO v_periodo FROM PERIODOS_ACADEMICOS WHERE estado = 'ACTIVO' LIMIT 1;
    IF v_periodo IS NULL THEN
        INSERT INTO PERIODOS_ACADEMICOS (codigo, fecha_inicio, fecha_fin, estado)
        VALUES ('2026-2', '2026-07-01', '2026-12-31', 'ACTIVO')
        ON CONFLICT (codigo) DO NOTHING;
        v_periodo := '2026-2';
    END IF;

    SELECT (fecha_fin - INTERVAL '15 days')::date INTO v_fecha_limite_vacante
    FROM PERIODOS_ACADEMICOS WHERE codigo = v_periodo;

    SELECT id INTO v_rol_estudiante_id FROM ROLES WHERE codigo = 'ESTUDIANTE';
    SELECT id INTO v_rol_tutor_id FROM ROLES WHERE codigo = 'TUTOR';

    -- ==================================================================
    -- EMPRESAS (12) + TUTORES DE EMPRESA + VACANTES
    -- Índices 1-6: mapeo 1 a 1 garantizado con carreras_arr (empresa
    -- "primaria" de cada carrera). Índices 7-12: empresas adicionales
    -- para las carreras de mayor demanda (Derecho, Software, Psicología,
    -- Medicina), solo para variedad visual.
    -- ==================================================================
    FOR i IN 1..12 LOOP
        v_carrera_idx := ((i - 1) % 6) + 1;
        v_carrera := carreras_arr[v_carrera_idx];
        v_ruc := '999' || lpad(i::text, 7, '0') || '001';
        v_nombre_emp := 'DEMO Empresa ' || area_arr[v_carrera_idx] || ' ' || i;

        INSERT INTO EMPRESAS (ruc, nombre, direccion, cupos_disponibles, activo, tiene_convenio)
        VALUES (v_ruc, v_nombre_emp, 'Av. Demo ' || i || ', ' || ciudad_arr[v_carrera_idx], 10 + i, TRUE, FALSE)
        ON CONFLICT (ruc) DO NOTHING;

        SELECT id INTO v_empresa_id FROM EMPRESAS WHERE ruc = v_ruc;
        empresa_ids := empresa_ids || v_empresa_id;
        IF i <= 6 THEN
            empresa_primaria_ids[v_carrera_idx] := v_empresa_id;
        END IF;

        -- Tutor de empresa (rol TUTOR, tutor_tipo='PRACTICAS')
        v_tutor_cedula := 'DEMO-TUT-EMP-' || lpad(i::text, 3, '0');
        IF NOT EXISTS (SELECT 1 FROM USUARIOS WHERE cedula = v_tutor_cedula) THEN
            v_nombre_tutor := nombres_arr[((i * 3 - 1) % 20) + 1];
            v_apellido_tutor := apellidos_arr[((i * 5 - 1) % 20) + 1];
            INSERT INTO USUARIOS (cedula, nombre, apellido, email, password_hash, activo, primer_login, fuente, external_id, tutor_tipo)
            VALUES (v_tutor_cedula, v_nombre_tutor, v_apellido_tutor,
                    'demo.tutemp' || lpad(i::text, 3, '0') || '@unibe-demo.edu.ec',
                    crypt('Demo2026*', gen_salt('bf', 10)), TRUE, FALSE, 'MANUAL', v_tutor_cedula, 'PRACTICAS')
            RETURNING id INTO v_tutor_usuario_id;
            INSERT INTO USUARIOS_ROLES (usuario_id, rol_id) VALUES (v_tutor_usuario_id, v_rol_tutor_id);
        ELSE
            SELECT id INTO v_tutor_usuario_id FROM USUARIOS WHERE cedula = v_tutor_cedula;
        END IF;
        tutor_empresa_usuario_ids := tutor_empresa_usuario_ids || v_tutor_usuario_id;
        IF i <= 6 THEN
            tutor_empresa_por_carrera[v_carrera_idx] := v_tutor_usuario_id;
        END IF;

        INSERT INTO TUTORES_EMPRESA (empresa_id, nombre, cargo, usuario_id, activo)
        SELECT v_empresa_id, u.nombre || ' ' || u.apellido, 'Tutor de Prácticas', v_tutor_usuario_id, TRUE
        FROM USUARIOS u WHERE u.id = v_tutor_usuario_id
        ON CONFLICT (usuario_id, empresa_id) WHERE usuario_id IS NOT NULL DO NOTHING;

        SELECT id INTO v_carrera_id FROM CARRERAS WHERE nombre = v_carrera;
        INSERT INTO TUTORES_EMPRESA_CARRERAS (tutor_empresa_id, carrera_id)
        SELECT te.id, v_carrera_id FROM TUTORES_EMPRESA te
        WHERE te.usuario_id = v_tutor_usuario_id AND te.empresa_id = v_empresa_id
        ON CONFLICT DO NOTHING;

        -- Vacantes de Práctica I y Práctica II para esta empresa
        FOR m IN 1..2 LOOP
            v_modalidad := CASE m WHEN 1 THEN 'Práctica I' ELSE 'Práctica II' END;
            v_nombre_vacante := 'DEMO Vacante ' || v_modalidad || ' - ' || v_nombre_emp;
            IF NOT EXISTS (SELECT 1 FROM VACANTES_PRACTICAS WHERE nombre = v_nombre_vacante) THEN
                INSERT INTO VACANTES_PRACTICAS
                    (nombre, empresa_id, cupos, horas, descripcion, carrera, modalidad_academica,
                     area, ciudad, tipo_empresa, modalidad_trabajo, fecha_limite, alta_demanda,
                     requisitos, periodo_academico, activa)
                VALUES
                    (v_nombre_vacante, v_empresa_id, 5 + i, 120,
                     'Vacante de demostración generada automáticamente para fines de presentación.',
                     v_carrera, v_modalidad, area_arr[v_carrera_idx], ciudad_arr[v_carrera_idx],
                     CASE WHEN i % 5 = 0 THEN 'Pública' ELSE 'Privada' END,
                     (ARRAY['Presencial', 'Híbrida', 'Virtual'])[1 + (i % 3)],
                     v_fecha_limite_vacante, (i % 4 = 0),
                     'Requisitos generados para fines de demostración.', v_periodo, TRUE);
            END IF;
        END LOOP;
    END LOOP;

    -- ==================================================================
    -- FUNDACIONES + PROYECTOS (6, uno por carrera) + TUTORES DE VINCULACIÓN
    -- ==================================================================
    FOR c IN 1..6 LOOP
        v_carrera := carreras_arr[c];
        v_ruc_f := '998' || lpad(c::text, 7, '0') || '001';
        v_nombre_f := 'DEMO Fundación ' || area_arr[c];

        INSERT INTO FUNDACIONES (ruc, nombre, mision, area_intervencion, activa, tiene_convenio)
        VALUES (v_ruc_f, v_nombre_f,
                'Fundación ficticia de demostración orientada a proyectos de ' || area_arr[c] || '.',
                area_arr[c], TRUE, FALSE)
        ON CONFLICT (ruc) DO NOTHING;

        SELECT id INTO v_fundacion_id FROM FUNDACIONES WHERE ruc = v_ruc_f;
        fundacion_ids[c] := v_fundacion_id;

        v_nombre_proy := 'DEMO Proyecto de Vinculación - ' || v_carrera;
        IF NOT EXISTS (SELECT 1 FROM PROYECTOS WHERE nombre = v_nombre_proy AND fundacion_id = v_fundacion_id) THEN
            INSERT INTO PROYECTOS
                (fundacion_id, nombre, descripcion, cupos_totales, cupos_disponibles,
                 horas_requeridas, ciudad, modalidad, estado, periodo)
            VALUES
                (v_fundacion_id, v_nombre_proy,
                 'Proyecto de vinculación con la sociedad ficticio para fines de demostración.',
                 25, 25, 160, ciudad_arr[c], 'HIBRIDA', TRUE, v_periodo);
        END IF;

        SELECT id INTO v_proyecto_id FROM PROYECTOS WHERE nombre = v_nombre_proy AND fundacion_id = v_fundacion_id;
        proyecto_ids[c] := v_proyecto_id;

        SELECT id INTO v_carrera_id FROM CARRERAS WHERE nombre = v_carrera;
        INSERT INTO PROYECTOS_CARRERAS (proyecto_id, carrera_id)
        VALUES (v_proyecto_id, v_carrera_id)
        ON CONFLICT (proyecto_id, carrera_id) DO NOTHING;

        -- Tutor de vinculación (rol TUTOR, tutor_tipo='VINCULACION')
        v_tutor_v_cedula := 'DEMO-TUT-VIN-' || lpad(c::text, 3, '0');
        IF NOT EXISTS (SELECT 1 FROM USUARIOS WHERE cedula = v_tutor_v_cedula) THEN
            INSERT INTO USUARIOS (cedula, nombre, apellido, email, password_hash, activo, primer_login, fuente, external_id, tutor_tipo)
            VALUES (v_tutor_v_cedula,
                    nombres_arr[((c * 7 - 1) % 20) + 1], apellidos_arr[((c * 11 - 1) % 20) + 1],
                    'demo.tutvin' || lpad(c::text, 3, '0') || '@unibe-demo.edu.ec',
                    crypt('Demo2026*', gen_salt('bf', 10)), TRUE, FALSE, 'MANUAL', v_tutor_v_cedula, 'VINCULACION')
            RETURNING id INTO v_tutor_v_usuario_id;
            INSERT INTO USUARIOS_ROLES (usuario_id, rol_id) VALUES (v_tutor_v_usuario_id, v_rol_tutor_id);
        ELSE
            SELECT id INTO v_tutor_v_usuario_id FROM USUARIOS WHERE cedula = v_tutor_v_cedula;
        END IF;
        tutor_vinc_ids[c] := v_tutor_v_usuario_id;
    END LOOP;

    -- ==================================================================
    -- 134 ESTUDIANTES + EXPEDIENTE ACADÉMICO
    -- Distribución: 1-20 (Vinculación iniciando) / 21-54 (Vinculación
    -- completa + Práctica I en curso) / 55-94 (+ Práctica I completa,
    -- Práctica II en curso) / 95-134 (las 3 etapas completadas).
    -- ==================================================================
    FOR i IN 1..134 LOOP
        v_cedula := 'DEMO-EST-' || lpad(i::text, 4, '0');
        IF NOT EXISTS (SELECT 1 FROM USUARIOS WHERE cedula = v_cedula) THEN

            v_carrera_idx := ((i - 1) % 6) + 1;
            v_carrera := carreras_arr[v_carrera_idx];
            v_nombre := nombres_arr[((i - 1) % 20) + 1];
            v_apellido := apellidos_arr[((i * 7 - 1) % 20) + 1];
            v_email := 'demo.est' || lpad(i::text, 4, '0') || '@unibe-demo.edu.ec';

            INSERT INTO USUARIOS (cedula, nombre, apellido, email, password_hash, activo, primer_login, fuente, external_id)
            VALUES (v_cedula, v_nombre, v_apellido, v_email,
                    crypt('Demo2026*', gen_salt('bf', 10)), TRUE, FALSE, 'MANUAL', v_cedula)
            RETURNING id INTO v_usuario_id;

            INSERT INTO USUARIOS_ROLES (usuario_id, rol_id) VALUES (v_usuario_id, v_rol_estudiante_id);

            IF i <= 20 THEN
                v_bucket := 1;
            ELSIF i <= 54 THEN
                v_bucket := 2;
            ELSIF i <= 94 THEN
                v_bucket := 3;
            ELSE
                v_bucket := 4;
            END IF;

            v_semestre := CASE v_bucket
                WHEN 1 THEN 3 + (i % 3)
                WHEN 2 THEN 5 + (i % 3)
                WHEN 3 THEN 6 + (i % 3)
                ELSE 8 + (i % 3)
            END;

            v_matricula := 'DEMO-MAT-' || lpad(i::text, 4, '0');
            INSERT INTO ESTUDIANTES (usuario_id, matricula, carrera, semestre, periodo_academico)
            VALUES (v_usuario_id, v_matricula, v_carrera, v_semestre, v_periodo)
            RETURNING id INTO v_estudiante_id;

            v_fundacion_id := fundacion_ids[v_carrera_idx];
            v_proyecto_id := proyecto_ids[v_carrera_idx];
            v_empresa_id := empresa_primaria_ids[v_carrera_idx];
            v_tutor_practicas_id := tutor_empresa_por_carrera[v_carrera_idx];
            v_tutor_vinc_id := tutor_vinc_ids[v_carrera_idx];

            -- ----------------------------------------------------------
            -- BUCKET 1: recién iniciando Vinculación (pendiente / en_curso)
            -- ----------------------------------------------------------
            IF v_bucket = 1 THEN
                IF i % 3 = 0 THEN
                    v_estado_v := 'pendiente';
                    v_horas_v := 0;
                    v_fecha_ini_v := NULL;
                ELSE
                    v_estado_v := 'en_curso';
                    v_horas_v := 8 + (i * 7 % 42);
                    v_fecha_ini_v := CURRENT_DATE - ((i % 20) + 5);
                END IF;

                INSERT INTO VINCULACION (estudiante_id, fundacion_id, proyecto_id, tutor_id, estado,
                                          horas_requeridas, horas_completadas, fecha_inicio, periodo_academico)
                VALUES (v_estudiante_id, v_fundacion_id, v_proyecto_id, v_tutor_vinc_id, v_estado_v,
                        160, v_horas_v, v_fecha_ini_v, v_periodo);
                UPDATE PROYECTOS SET cupos_disponibles = GREATEST(cupos_disponibles - 1, 0) WHERE id = v_proyecto_id;
            END IF;

            -- ----------------------------------------------------------
            -- BUCKETS 2, 3, 4: Vinculación completada (160h + 3 parciales)
            -- ----------------------------------------------------------
            IF v_bucket IN (2, 3, 4) THEN
                v_periodo_v := CASE v_bucket WHEN 2 THEN '2026-1' WHEN 3 THEN '2025-2' ELSE '2025-1' END;
                v_fecha_ini_v := CASE v_bucket WHEN 2 THEN DATE '2026-01-20' WHEN 3 THEN DATE '2025-07-20' ELSE DATE '2025-01-20' END;
                v_fecha_fin_v := CASE v_bucket WHEN 2 THEN DATE '2026-05-25' WHEN 3 THEN DATE '2025-11-25' ELSE DATE '2025-05-25' END;

                INSERT INTO VINCULACION (estudiante_id, fundacion_id, proyecto_id, tutor_id, estado,
                                          horas_requeridas, horas_completadas, fecha_inicio, fecha_fin, periodo_academico)
                VALUES (v_estudiante_id, v_fundacion_id, v_proyecto_id, v_tutor_vinc_id, 'completado',
                        160, 160, v_fecha_ini_v, v_fecha_fin_v, v_periodo_v)
                RETURNING id INTO v_vinculacion_id;
                UPDATE PROYECTOS SET cupos_disponibles = GREATEST(cupos_disponibles - 1, 0) WHERE id = v_proyecto_id;

                FOR p IN 1..3 LOOP
                    v_nota_t := (7.0 + ((i * 3 + p * 7) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_c := (7.0 + ((i * 5 + p * 11) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_final := round((v_nota_t * 0.5 + v_nota_c * 0.5)::numeric, 2);
                    INSERT INTO EVALUACIONES_PRACTICAS_DETALLE (vinculacion_id, parcial, nota_tutor, nota_coord, nota_final, encuesta_completada)
                    VALUES (v_vinculacion_id, p, v_nota_t, v_nota_c, v_nota_final, TRUE);
                    INSERT INTO ENCUESTAS_SATISFACCION (estudiante_id, vinculacion_id, parcial, satisfaccion_tutor,
                                                         satisfaccion_empresa_proyecto, relacion_carrera, claridad_instrucciones, comentario)
                    VALUES (v_estudiante_id, v_vinculacion_id, p, 4 + (i % 2), 4 + ((i + 1) % 2), 4 + (i % 2), 4 + ((i + 1) % 2),
                            'Comentario de satisfacción generado automáticamente para fines de demostración (dato ficticio).');
                END LOOP;
            END IF;

            -- ----------------------------------------------------------
            -- BUCKET 2: Práctica I en curso
            -- ----------------------------------------------------------
            IF v_bucket = 2 THEN
                v_horas_p1 := 15 + (i * 13 % 90);
                INSERT INTO PRACTICAS (estudiante_id, empresa_id, tutor_id, estado,
                                        horas_requeridas, horas_completadas, fecha_inicio, periodo_academico)
                VALUES (v_estudiante_id, v_empresa_id, v_tutor_practicas_id, 'en_curso',
                        120, v_horas_p1, CURRENT_DATE - ((i % 25) + 7), v_periodo)
                RETURNING id INTO v_practica1_id;

                v_cerrados := floor(v_horas_p1 / 40.0);
                FOR p IN 1..v_cerrados LOOP
                    v_nota_t := (7.0 + ((i * 3 + p * 7) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_c := (7.0 + ((i * 5 + p * 11) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_final := round((v_nota_t * 0.5 + v_nota_c * 0.5)::numeric, 2);
                    INSERT INTO EVALUACIONES_PRACTICAS_DETALLE (practica_id, parcial, nota_tutor, nota_coord, nota_final, encuesta_completada)
                    VALUES (v_practica1_id, p, v_nota_t, v_nota_c, v_nota_final, TRUE);
                    INSERT INTO ENCUESTAS_SATISFACCION (estudiante_id, practica_id, parcial, satisfaccion_tutor,
                                                         satisfaccion_empresa_proyecto, relacion_carrera, claridad_instrucciones, comentario)
                    VALUES (v_estudiante_id, v_practica1_id, p, 4 + (i % 2), 4 + ((i + 1) % 2), 4 + (i % 2), 4 + ((i + 1) % 2),
                            'Comentario de satisfacción generado automáticamente para fines de demostración (dato ficticio).');
                END LOOP;
            END IF;

            -- ----------------------------------------------------------
            -- BUCKET 3: Práctica I completada, Práctica II en curso
            -- ----------------------------------------------------------
            IF v_bucket = 3 THEN
                v_fecha_ini_p1 := DATE '2026-02-01';
                v_fecha_fin_p1 := DATE '2026-05-15';
                INSERT INTO PRACTICAS (estudiante_id, empresa_id, tutor_id, estado,
                                        horas_requeridas, horas_completadas, fecha_inicio, fecha_fin, periodo_academico)
                VALUES (v_estudiante_id, v_empresa_id, v_tutor_practicas_id, 'completado',
                        120, 120, v_fecha_ini_p1, v_fecha_fin_p1, '2026-1')
                RETURNING id INTO v_practica1_id;

                FOR p IN 1..3 LOOP
                    v_nota_t := (7.0 + ((i * 3 + p * 7) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_c := (7.0 + ((i * 5 + p * 11) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_final := round((v_nota_t * 0.5 + v_nota_c * 0.5)::numeric, 2);
                    INSERT INTO EVALUACIONES_PRACTICAS_DETALLE (practica_id, parcial, nota_tutor, nota_coord, nota_final, encuesta_completada)
                    VALUES (v_practica1_id, p, v_nota_t, v_nota_c, v_nota_final, TRUE);
                    INSERT INTO ENCUESTAS_SATISFACCION (estudiante_id, practica_id, parcial, satisfaccion_tutor,
                                                         satisfaccion_empresa_proyecto, relacion_carrera, claridad_instrucciones, comentario)
                    VALUES (v_estudiante_id, v_practica1_id, p, 4 + (i % 2), 4 + ((i + 1) % 2), 4 + (i % 2), 4 + ((i + 1) % 2),
                            'Comentario de satisfacción generado automáticamente para fines de demostración (dato ficticio).');
                END LOOP;

                v_horas_p2 := 15 + (i * 17 % 90);
                INSERT INTO PRACTICAS (estudiante_id, empresa_id, tutor_id, estado,
                                        horas_requeridas, horas_completadas, fecha_inicio, periodo_academico,
                                        tipo_asignacion, practica_origen_id)
                VALUES (v_estudiante_id, v_empresa_id, v_tutor_practicas_id, 'en_curso',
                        120, v_horas_p2, CURRENT_DATE - ((i % 20) + 3), v_periodo,
                        'CONTINUIDAD', v_practica1_id)
                RETURNING id INTO v_practica2_id;

                v_cerrados := floor(v_horas_p2 / 40.0);
                FOR p IN 1..v_cerrados LOOP
                    v_nota_t := (7.0 + ((i * 3 + p * 7 + 1) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_c := (7.0 + ((i * 5 + p * 11 + 1) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_final := round((v_nota_t * 0.5 + v_nota_c * 0.5)::numeric, 2);
                    INSERT INTO EVALUACIONES_PRACTICAS_DETALLE (practica_id, parcial, nota_tutor, nota_coord, nota_final, encuesta_completada)
                    VALUES (v_practica2_id, p, v_nota_t, v_nota_c, v_nota_final, TRUE);
                    INSERT INTO ENCUESTAS_SATISFACCION (estudiante_id, practica_id, parcial, satisfaccion_tutor,
                                                         satisfaccion_empresa_proyecto, relacion_carrera, claridad_instrucciones, comentario)
                    VALUES (v_estudiante_id, v_practica2_id, p, 4 + (i % 2), 4 + ((i + 1) % 2), 4 + (i % 2), 4 + ((i + 1) % 2),
                            'Comentario de satisfacción generado automáticamente para fines de demostración (dato ficticio).');
                END LOOP;
            END IF;

            -- ----------------------------------------------------------
            -- BUCKET 4: Práctica I y II completadas ("egresados" del flujo)
            -- ----------------------------------------------------------
            IF v_bucket = 4 THEN
                v_fecha_ini_p1 := DATE '2025-08-01';
                v_fecha_fin_p1 := DATE '2025-11-15';
                INSERT INTO PRACTICAS (estudiante_id, empresa_id, tutor_id, estado,
                                        horas_requeridas, horas_completadas, fecha_inicio, fecha_fin, periodo_academico)
                VALUES (v_estudiante_id, v_empresa_id, v_tutor_practicas_id, 'completado',
                        120, 120, v_fecha_ini_p1, v_fecha_fin_p1, '2025-2')
                RETURNING id INTO v_practica1_id;

                FOR p IN 1..3 LOOP
                    v_nota_t := (7.0 + ((i * 3 + p * 7) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_c := (7.0 + ((i * 5 + p * 11) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_final := round((v_nota_t * 0.5 + v_nota_c * 0.5)::numeric, 2);
                    INSERT INTO EVALUACIONES_PRACTICAS_DETALLE (practica_id, parcial, nota_tutor, nota_coord, nota_final, encuesta_completada)
                    VALUES (v_practica1_id, p, v_nota_t, v_nota_c, v_nota_final, TRUE);
                    INSERT INTO ENCUESTAS_SATISFACCION (estudiante_id, practica_id, parcial, satisfaccion_tutor,
                                                         satisfaccion_empresa_proyecto, relacion_carrera, claridad_instrucciones, comentario)
                    VALUES (v_estudiante_id, v_practica1_id, p, 4 + (i % 2), 4 + ((i + 1) % 2), 4 + (i % 2), 4 + ((i + 1) % 2),
                            'Comentario de satisfacción generado automáticamente para fines de demostración (dato ficticio).');
                END LOOP;

                v_fecha_ini_p2 := DATE '2026-02-01';
                v_fecha_fin_p2 := DATE '2026-05-15';
                INSERT INTO PRACTICAS (estudiante_id, empresa_id, tutor_id, estado,
                                        horas_requeridas, horas_completadas, fecha_inicio, fecha_fin, periodo_academico,
                                        tipo_asignacion, practica_origen_id)
                VALUES (v_estudiante_id, v_empresa_id, v_tutor_practicas_id, 'completado',
                        120, 120, v_fecha_ini_p2, v_fecha_fin_p2, '2026-1',
                        'CONTINUIDAD', v_practica1_id)
                RETURNING id INTO v_practica2_id;

                FOR p IN 1..3 LOOP
                    v_nota_t := (7.0 + ((i * 3 + p * 7 + 1) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_c := (7.0 + ((i * 5 + p * 11 + 1) % 26)::numeric / 10)::numeric(4,2);
                    v_nota_final := round((v_nota_t * 0.5 + v_nota_c * 0.5)::numeric, 2);
                    INSERT INTO EVALUACIONES_PRACTICAS_DETALLE (practica_id, parcial, nota_tutor, nota_coord, nota_final, encuesta_completada)
                    VALUES (v_practica2_id, p, v_nota_t, v_nota_c, v_nota_final, TRUE);
                    INSERT INTO ENCUESTAS_SATISFACCION (estudiante_id, practica_id, parcial, satisfaccion_tutor,
                                                         satisfaccion_empresa_proyecto, relacion_carrera, claridad_instrucciones, comentario)
                    VALUES (v_estudiante_id, v_practica2_id, p, 4 + (i % 2), 4 + ((i + 1) % 2), 4 + (i % 2), 4 + ((i + 1) % 2),
                            'Comentario de satisfacción generado automáticamente para fines de demostración (dato ficticio).');
                END LOOP;
            END IF;

        END IF; -- IF NOT EXISTS (usuario ya creado en una corrida anterior)
    END LOOP;

END $$;

-- ============================================================================
-- Verificación
-- ============================================================================
SELECT COUNT(*) AS estudiantes_demo FROM ESTUDIANTES WHERE matricula LIKE 'DEMO-MAT-%';

SELECT v.estado, COUNT(*) AS total
FROM VINCULACION v
JOIN ESTUDIANTES e ON e.id = v.estudiante_id
WHERE e.matricula LIKE 'DEMO-MAT-%'
GROUP BY v.estado
ORDER BY v.estado;

SELECT p.estado, COUNT(*) AS total
FROM PRACTICAS p
JOIN ESTUDIANTES e ON e.id = p.estudiante_id
WHERE e.matricula LIKE 'DEMO-MAT-%'
GROUP BY p.estado
ORDER BY p.estado;

SELECT
    (SELECT COUNT(*) FROM EMPRESAS WHERE nombre LIKE 'DEMO %')            AS empresas_demo,
    (SELECT COUNT(*) FROM FUNDACIONES WHERE nombre LIKE 'DEMO %')         AS fundaciones_demo,
    (SELECT COUNT(*) FROM PROYECTOS WHERE nombre LIKE 'DEMO %')           AS proyectos_demo,
    (SELECT COUNT(*) FROM VACANTES_PRACTICAS WHERE nombre LIKE 'DEMO %')  AS vacantes_demo,
    (SELECT COUNT(*) FROM USUARIOS WHERE cedula LIKE 'DEMO-TUT-%')        AS tutores_demo;

SELECT COUNT(*) AS evaluaciones_demo
FROM EVALUACIONES_PRACTICAS_DETALLE d
WHERE d.practica_id IN (
        SELECT p.id FROM PRACTICAS p
        JOIN ESTUDIANTES e ON e.id = p.estudiante_id
        WHERE e.matricula LIKE 'DEMO-MAT-%'
      )
   OR d.vinculacion_id IN (
        SELECT v.id FROM VINCULACION v
        JOIN ESTUDIANTES e ON e.id = v.estudiante_id
        WHERE e.matricula LIKE 'DEMO-MAT-%'
      );
