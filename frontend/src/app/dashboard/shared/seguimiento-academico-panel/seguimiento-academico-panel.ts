import { Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SeguimientoPractica } from '../../../core/services/practica.service';
import { SeguimientoVinculacion } from '../../../core/services/vinculacion.service';

type SeguimientoItem = SeguimientoPractica | SeguimientoVinculacion;

@Component({ selector: 'app-seguimiento-academico-panel', standalone: true, imports: [FormsModule], templateUrl: './seguimiento-academico-panel.html' })
export class SeguimientoAcademicoPanelComponent {
  proceso = input.required<'PRACTICAS' | 'VINCULACION'>();
  items = input.required<SeguimientoItem[]>();
  filtro = input('TODOS');
  cerrandoId = input<number | null>(null);
  cargandoActaId = input<number | null>(null);
  filtroChange = output<string>();
  cerrar = output<number>();
  abrirActa = output<number>();

  filtrados = computed(() => this.items().filter(item => {
    const filtro = this.filtro();
    if (filtro === 'RIESGO') return item.riesgo;
    if (filtro === 'BITACORAS') return item.bitacorasPendientes > 0 || item.bitacorasRechazadas > 0 || item.bitacorasCorreccion > 0;
    if (filtro === 'NOTAS') return item.notasTutorPendientes > 0 || item.notasCoordPendientes > 0;
    if (filtro === 'ENCUESTAS') return item.encuestasPendientes > 0;
    if (filtro === 'CIERRE') return item.listoParaCierre;
    return true;
  }));

  enRiesgo = computed(() => this.items().filter(item => item.riesgo).length);
  bitacorasPendientes = computed(() => this.items().reduce((total, item) => total + item.bitacorasPendientes, 0));
  pendientesAcademicos = computed(() => this.items().reduce(
    (total, item) => total + item.notasTutorPendientes + item.notasCoordPendientes + item.encuestasPendientes, 0));

  id(item: SeguimientoItem): number { return 'practicaId' in item ? item.practicaId : item.vinculacionId; }
  entidad(item: SeguimientoItem): string {
    return (item as SeguimientoPractica).empresa
      || (item as SeguimientoVinculacion).proyecto
      || 'Sin entidad asignada';
  }
  nombreProceso(): string { return this.proceso() === 'PRACTICAS' ? 'Prácticas' : 'Vinculación'; }
}
