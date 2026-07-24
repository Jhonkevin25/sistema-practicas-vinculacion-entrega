import { Asistencia } from '../../core/services/asistencia.service';

// Lógica pura del "pase de lista" de asistencia, compartida por los módulos
// de Prácticas y Vinculación.

export type EstadoPase = 'Presente' | 'Atraso' | 'Falta' | 'OMITIR';

export interface FilaPase {
  estado: EstadoPase;
  observaciones: string;
}

// Al tomar lista todos parten como "Presente"; el tutor cambia los casos especiales
export function nuevaFilaPase(): FilaPase {
  return { estado: 'Presente', observaciones: '' };
}

// Fecha local en formato ISO (yyyy-MM-dd); toISOString() usaría UTC y en
// Ecuador (UTC-5) cambiaría de día por la noche
export function fechaLocalISO(hoy = new Date()): string {
  const mes = String(hoy.getMonth() + 1).padStart(2, '0');
  const dia = String(hoy.getDate()).padStart(2, '0');
  return `${hoy.getFullYear()}-${mes}-${dia}`;
}

// Asistencia ya registrada para ese expediente en esa fecha (el backend solo
// admite una por expediente y día; estas filas se excluyen del guardado)
export function asistenciaEnFecha(
  asistencias: Asistencia[],
  fecha: string,
  ref: { practicaId?: number; vinculacionId?: number }
): Asistencia | undefined {
  return asistencias.find(asistencia => asistencia.fecha === fecha
    && (ref.practicaId != null
      ? asistencia.practica?.id === ref.practicaId
      : ref.vinculacionId != null && asistencia.vinculacion?.id === ref.vinculacionId));
}
