# Base de datos — PostgreSQL (Supabase)

Fuente de verdad del esquema. No hay framework de migraciones (Flyway/Liquibase):
los scripts se aplican pegándolos en el **SQL Editor de Supabase**, y después se
actualizan las entidades JPA (`spring.jpa.hibernate.ddl-auto=validate` valida al
arrancar). Todos los scripts son **idempotentes** (`IF NOT EXISTS`,
`ON CONFLICT DO NOTHING`): pueden re-ejecutarse sin error.

## Estructura

| Ruta | Contenido |
|---|---|
| `schema_completo.sql` | **Esquema consolidado**: todo el DDL en un solo archivo, listo para levantar una base vacía de cero. Generado concatenando `schema.sql` + migraciones en orden. |
| `schema.sql` | Esquema base original (v1). |
| `seed.sql` | Datos semilla (roles, catálogos mínimos). |
| `migraciones/` | Migraciones incrementales en orden de aplicación (historia real del desarrollo). |
| `utilidades-prueba/` | Scripts de datos de prueba y reseteo — **NUNCA ejecutarlos en producción**. |

## Orden de aplicación (base nueva)

Opción rápida: ejecutar solo `schema_completo.sql` y luego `seed.sql`.

Opción paso a paso: `schema.sql` → `seed.sql` → `migraciones/` en este orden:

1. `migration_v2_iso25010.sql`
2. `fase2_calendario_academico.sql`
3. `fase3_cupos_transaccionales.sql`
4. `fase4_meritocracia_backend.sql`
5. `fase5_docs_alcance.sql`
6. `fase6_bitacoras_reales.sql`
7. `fase7_parciales_notas_encuestas.sql`
8. `fase9_vinculacion_real.sql`
9. `fase11_integracion_documentos_institucionales.sql`
10. `fase12_notificaciones.sql`
11. `fase14_recuperacion_password.sql`
12. `fase15_storage_documentos_constraints.sql`
13. `fase17_paridad_vinculacion.sql`
14. `fase18_cierre_robusto.sql`
15. `fase19_convenios.sql`
16. `fase20_importaciones.sql`
17. `fase26_cierre_mvp.sql`
18. `fase27_catalogo_carreras.sql`
19. `fase28_tutor_tipo.sql`
20. `fase32_ofertas_cupos_empresa.sql`
21. `fase33_historial_academico_casos_excepcionales.sql`
22. `fase34_asistencias_por_expediente.sql`
23. `fase35_horas_oficiales_derivadas.sql`
24. `fase36_endurecimiento_login.sql`
25. `fase38_cupos_fundaciones_proyectos.sql`
26. `fase39_periodos_y_ofertas_fundaciones.sql`
27. `fase42_ciclo_vida_cierre_periodos.sql`
28. `fase45_cola_correos.sql`
29. `fase47_liberar_cupo_practica.sql`
30. `fase48_expediente_estudiante_unico.sql`

## Utilidades de prueba (`utilidades-prueba/`)

- `fase4_usuarios_prueba.sql` — crea los usuarios de prueba por rol.
- `fase4_reset_estudiante.sql` — resetea el expediente del estudiante de prueba
  para repetir el flujo completo.
- `fase4_estudiante_a_practica1.sql` — avanza al estudiante de prueba a la
  etapa Práctica I.
- `fase2_limpieza_calendario_prueba.sql`, `fase4_limpiar_usuarios_prueba.sql`,
  `fase4_limpieza_postulaciones_prueba.sql` — limpiezas de datos de prueba.
- `fix_practica_duplicada.sql` — corrección puntual histórica.
- `reinicio_mvp_solo_admin_preflight.sql` — inventario de solo lectura antes
  de una nueva validación integral.
- `reinicio_mvp_solo_admin.sql` — reinicio protegido de datos de prueba;
  conserva un ADMIN y los catálogos estructurales. Requiere respaldo, detener
  la aplicación y escribir una frase de confirmación explícita.
- `respaldar_supabase_prueba.ps1` — respaldo lógico para el proyecto de prueba
  en plan gratuito; solicita la contraseña de forma oculta y guarda roles,
  esquema, datos y checksums fuera del repositorio.

## Convenciones y conexión

- Tablas en MAYÚSCULAS, columnas en `snake_case` minúsculas.
- Conectarse siempre a través del **pooler** de Supabase (el host directo es
  solo-IPv6); máximo 15 conexiones (la app usa Hikari con tope 5, tests 2).
- Credenciales en `backend/sistema-practicas/.env.properties` (no versionado;
  plantilla en `.env.properties.example`).
- Nota (2026-07-17): los scripts `fase*.sql` vivían en la raíz de `database/`;
  las referencias con esa ruta en documentos históricos corresponden a
  `migraciones/` o `utilidades-prueba/`.
