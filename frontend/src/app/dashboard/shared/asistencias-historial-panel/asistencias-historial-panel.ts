import { Component, computed, input } from '@angular/core';
import { Asistencia } from '../../../core/services/asistencia.service';

@Component({
  selector: 'app-asistencias-historial-panel',
  standalone: true,
  templateUrl: './asistencias-historial-panel.html'
})
export class AsistenciasHistorialPanelComponent {
  asistencias = input.required<Asistencia[]>();
  titulo = input('Asistencias registradas');
  descripcion = input('Historial guardado en el expediente académico correspondiente.');

  asistenciasOrdenadas = computed(() => [...this.asistencias()].sort((a, b) => {
    const porFecha = String(b.fecha || '').localeCompare(String(a.fecha || ''));
    return porFecha || (b.id || 0) - (a.id || 0);
  }));

  nombre(asistencia: Asistencia): string {
    const usuario = asistencia.estudiante?.usuario;
    return usuario ? `${usuario.nombre || ''} ${usuario.apellido || ''}`.trim() : 'Sin estudiante';
  }

  claseEstado(estado?: string): string {
    if (estado === 'Presente') return 'border-green-200 bg-green-50 text-green-700';
    if (estado === 'Atraso') return 'border-amber-200 bg-amber-50 text-amber-800';
    return 'border-red-200 bg-red-50 text-red-700';
  }
}
