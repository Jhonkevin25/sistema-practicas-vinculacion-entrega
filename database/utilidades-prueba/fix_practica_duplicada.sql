-- ============================================================
-- ELIMINAR PRACTICA ACTIVA DUPLICADA DEL ESTUDIANTE DE PRUEBA
-- Ejecutar en Supabase SQL Editor.
-- ============================================================
-- Cambio:  elimina la practica duplicada (id=2) del estudiante
--          EST-2026-0001, conservando la id=1 (que ya tiene
--          evaluaciones registradas).
-- Motivo:  la consolidacion se ejecuto dos veces y el backend aun no
--          impedia procesos activos duplicados (regla agregada en Fase 6).
-- Impacto: PRACTICAS (las evaluaciones/documentos de la practica borrada
--          caen en cascada; la id=2 no tiene).

DELETE FROM PRACTICAS
WHERE id = 2
  AND estudiante_id = (SELECT id FROM ESTUDIANTES WHERE matricula = 'EST-2026-0001');

-- Verificacion: debe quedar UNA practica del estudiante de prueba
SELECT p.id, e.matricula, p.estado, p.horas_completadas, p.horas_requeridas
FROM PRACTICAS p
JOIN ESTUDIANTES e ON e.id = p.estudiante_id
WHERE e.matricula = 'EST-2026-0001';
