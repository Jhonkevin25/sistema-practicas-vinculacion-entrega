// Helpers puros para ordenar el módulo del tutor por periodo académico y
// mostrar el avance de horas como porcentaje. Compartidos por Prácticas y
// Vinculación.

// Expedientes antiguos sin periodo_academico guardado se agrupan aquí
export const SIN_PERIODO = 'SIN_PERIODO';

export function periodosDisponibles(periodos: Array<string | null | undefined>): string[] {
  const distintos = new Set<string>();
  let haySinPeriodo = false;
  for (const periodo of periodos) {
    if (periodo && periodo.trim()) distintos.add(periodo.trim());
    else haySinPeriodo = true;
  }
  const lista = [...distintos].sort((a, b) => b.localeCompare(a));
  if (haySinPeriodo) lista.push(SIN_PERIODO);
  return lista;
}

export function coincidePeriodo(seleccion: string, periodo?: string | null): boolean {
  if (seleccion === 'TODOS') return true;
  if (seleccion === SIN_PERIODO) return !periodo || !periodo.trim();
  return periodo === seleccion;
}

export function porcentajeAvance(completadas?: number | null, requeridas?: number | null): number {
  const total = Number(requeridas) || 0;
  if (total <= 0) return 0;
  const porcentaje = Math.round(((Number(completadas) || 0) / total) * 100);
  return Math.max(0, Math.min(100, porcentaje));
}
