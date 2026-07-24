import { CommonModule } from '@angular/common';
import { Component, HostListener, computed, input, output } from '@angular/core';
import {
  EventoLineaTiempo,
  LineaTiempoExpediente,
  estadoExpedienteTexto
} from '../../../core/services/linea-tiempo.service';

export function ordenarEventos(eventos: EventoLineaTiempo[] = []): EventoLineaTiempo[] {
  return eventos
    .map((evento, indice) => ({ evento, indice }))
    .sort((a, b) => {
      const fechaA = Date.parse(a.evento.fecha);
      const fechaB = Date.parse(b.evento.fecha);
      if (Number.isNaN(fechaA) || Number.isNaN(fechaB)) return a.indice - b.indice;
      return fechaA - fechaB || a.indice - b.indice;
    })
    .map(({ evento }) => evento);
}

@Component({
  selector: 'app-linea-tiempo-expediente',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './linea-tiempo-expediente.html'
})
export class LineaTiempoExpedienteComponent {
  titulo = input('Historial del expediente');
  lineaTiempo = input<LineaTiempoExpediente | null>(null);
  cargando = input(false);
  cerrar = output<void>();

  readonly eventos = computed(() => ordenarEventos(this.lineaTiempo()?.eventos));

  @HostListener('document:keydown.escape')
  cerrarConEscape(): void {
    this.cerrar.emit();
  }

  estadoTexto(estado?: string | null): string {
    return estadoExpedienteTexto(estado);
  }

  estadoClase(estado?: string | null): string {
    switch (String(estado || '').toLowerCase()) {
      case 'completado': return 'border-green-200 bg-green-50 text-green-800';
      case 'reprobado': return 'border-red-200 bg-red-50 text-red-800';
      case 'retirado': return 'border-amber-200 bg-amber-50 text-amber-900';
      case 'en_curso': return 'border-blue-200 bg-blue-50 text-blue-800';
      default: return 'border-slate-200 bg-slate-50 text-slate-700';
    }
  }

  eventoClase(evento: EventoLineaTiempo): string {
    const estado = String(evento.estado || '').toLowerCase();
    if (['reprobado', 'rechazado', 'retirado'].includes(estado)) return 'bg-red-50 text-red-700 ring-red-200';
    if (['completado', 'aprobado', 'cerrado'].includes(estado)) return 'bg-green-50 text-green-700 ring-green-200';
    return 'bg-blue-50 text-blue-700 ring-blue-200';
  }

  fechaLegible(fecha?: string): string {
    if (!fecha) return 'Fecha no registrada';
    const valor = new Date(fecha);
    if (Number.isNaN(valor.getTime())) return fecha;
    return new Intl.DateTimeFormat('es-EC', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(valor);
  }

  iconoEvento(tipo?: string): string {
    const clave = String(tipo || '').toLowerCase();
    if (clave.includes('document')) return 'M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2Zm-9 4h4m-4 4h8m-8 4h5';
    if (clave.includes('nota') || clave.includes('evalu')) return 'M9 12h6m-6 4h6m2 5H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h6l4 4v12a2 2 0 0 1-2 2Zm-3-18v4h4';
    if (clave.includes('final') || clave.includes('cierre') || clave.includes('reti')) return 'm5 12 4 4L19 6';
    if (clave.includes('alert') || clave.includes('reprob')) return 'M12 9v4m0 4h.01M10.3 3.7 2.8 17a2 2 0 0 0 1.7 3h15a2 2 0 0 0 1.7-3L13.7 3.7a2 2 0 0 0-3.4 0Z';
    return 'M12 6v6l4 2m5-2a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z';
  }
}
