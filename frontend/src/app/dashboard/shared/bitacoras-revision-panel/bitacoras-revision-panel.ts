import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Asistencia } from '../../../core/services/asistencia.service';
import { Bitacora } from '../../../core/services/bitacora.service';
import { porcentajeAvance } from '../periodo-avance';

export interface RevisionBitacora {
  bitacora: Bitacora;
  estado: 'aprobada' | 'rechazada' | 'requiere_correccion';
  observacion?: string;
}

export interface GrupoBitacoras {
  clave: string;
  nombre: string;
  matricula: string;
  carrera: string;
  periodo: string;
  lugar: string;
  horas: string;
  pendientes: number;
  bitacoras: Bitacora[];
}

export type FiltroEstadoBitacora = 'pendiente' | 'aprobada' | 'rechazada' | 'TODAS';

@Component({
  selector: 'app-bitacoras-revision-panel',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './bitacoras-revision-panel.html'
})
export class BitacorasRevisionPanelComponent {
  titulo = input('Bitácoras por revisar');
  bitacoras = input.required<Bitacora[]>();
  // Asistencias del mismo proceso: permiten avisar (sin bloquear) cuando una
  // bitácora aprobada no tiene asistencia registrada ese día.
  asistencias = input<Asistencia[]>([]);
  procesandoId = input<number | null>(null);
  procesar = output<RevisionBitacora>();

  bitacoraPorRechazar = signal<Bitacora | null>(null);
  motivoRechazo = signal('');
  accionActual = signal<'rechazada' | 'requiere_correccion'>('rechazada');

  // Filtros tipo plataforma institucional; por defecto lo accionable (pendientes)
  filtroEstado = signal<FiltroEstadoBitacora>('pendiente');
  filtroParcial = signal<'TODOS' | number>('TODOS');

  private coincideFiltro(bitacora: Bitacora): boolean {
    const estado = this.filtroEstado();
    if (estado === 'rechazada') {
      if (!['rechazada', 'requiere_correccion'].includes(bitacora.estado || '')) return false;
    } else if (estado !== 'TODAS' && bitacora.estado !== estado) {
      return false;
    }
    const parcial = this.filtroParcial();
    return parcial === 'TODOS' || Number(bitacora.parcial) === Number(parcial);
  }

  // Agrupadas por estudiante para que el tutor no vea todas de golpe; el
  // contador de pendientes es del expediente completo (no del filtro) y
  // dentro de cada grupo van de la más reciente a la más antigua.
  grupos = computed<GrupoBitacoras[]>(() => {
    const mapa = new Map<string, GrupoBitacoras>();
    for (const bitacora of this.bitacoras()) {
      const clave = String(bitacora.estudiante?.id ?? this.nombre(bitacora));
      let grupo = mapa.get(clave);
      if (!grupo) {
        const expediente = bitacora.practica || bitacora.vinculacion;
        grupo = {
          clave,
          nombre: this.nombre(bitacora),
          matricula: bitacora.estudiante?.matricula || '',
          carrera: bitacora.estudiante?.carrera || '',
          periodo: bitacora.estudiante?.periodoAcademico || '',
          lugar: bitacora.practica?.empresa?.nombre || bitacora.vinculacion?.proyecto?.nombre || '',
          horas: expediente
            ? `${expediente.horasCompletadas ?? 0}/${expediente.horasRequeridas ?? 0} h · ${porcentajeAvance(expediente.horasCompletadas, expediente.horasRequeridas)}%`
            : '',
          pendientes: 0,
          bitacoras: []
        };
        mapa.set(clave, grupo);
      }
      if (!grupo.lugar) {
        grupo.lugar = bitacora.practica?.empresa?.nombre || bitacora.vinculacion?.proyecto?.nombre || '';
      }
      grupo.bitacoras.push(bitacora);
      if (bitacora.estado === 'pendiente') grupo.pendientes++;
    }
    return [...mapa.values()]
      .map(grupo => ({
        ...grupo,
        bitacoras: grupo.bitacoras
          .filter(bitacora => this.coincideFiltro(bitacora))
          .sort((a, b) =>
            String(b.fecha || '').localeCompare(String(a.fecha || '')) || (b.id || 0) - (a.id || 0))
      }))
      .filter(grupo => grupo.bitacoras.length > 0)
      .sort((a, b) => a.nombre.localeCompare(b.nombre));
  });

  abrirObservacion(bitacora: Bitacora, estado: 'rechazada' | 'requiere_correccion'): void {
    this.bitacoraPorRechazar.set(bitacora);
    this.motivoRechazo.set('');
    this.accionActual.set(estado);
  }

  cerrarRechazo(): void {
    this.bitacoraPorRechazar.set(null);
    this.motivoRechazo.set('');
  }

  confirmarRechazo(): void {
    const bitacora = this.bitacoraPorRechazar();
    const observacion = this.motivoRechazo().trim();
    if (!bitacora || !observacion) return;

    this.procesar.emit({ bitacora, estado: this.accionActual(), observacion });
    this.cerrarRechazo();
  }

  nombre(bitacora: Bitacora): string {
    const usuario = bitacora.estudiante?.usuario;
    return usuario ? `${usuario.nombre || ''} ${usuario.apellido || ''}`.trim() : 'Sin estudiante';
  }

  claseEstado(estado?: string): string {
    if (estado === 'aprobada') return 'bg-green-50 border-green-200 text-green-700';
    if (estado === 'rechazada') return 'bg-red-50 border-red-200 text-red-700';
    if (estado === 'requiere_correccion') return 'bg-orange-50 border-orange-200 text-orange-700';
    return 'bg-amber-50 border-amber-200 text-amber-800';
  }

  // Aviso informativo: bitácora aprobada sin asistencia del mismo expediente
  // en la misma fecha. No bloquea (puede ser actividad remota o asincrónica).
  sinAsistenciaCoincidente(bitacora: Bitacora): boolean {
    if (bitacora.estado !== 'aprobada') return false;
    return !this.asistencias().some(asistencia => {
      if (asistencia.fecha !== bitacora.fecha) return false;
      if (bitacora.practica?.id != null) return asistencia.practica?.id === bitacora.practica.id;
      if (bitacora.vinculacion?.id != null) return asistencia.vinculacion?.id === bitacora.vinculacion.id;
      return asistencia.estudiante?.id != null && asistencia.estudiante.id === bitacora.estudiante?.id;
    });
  }

  // Aviso informativo: la bitácora se registró en una fecha distinta a la de la actividad.
  fueraDeFecha(bitacora: Bitacora): boolean {
    return !!bitacora.fechaRegistro && bitacora.fechaRegistro !== bitacora.fecha;
  }
}
