import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { CorreoService, CorreoCola } from '../../core/services/correo.service';

export function puedeReintentarCorreo(correo: CorreoCola, reintentandoId: number | null): boolean {
  return correo.estado === 'FALLIDO' && reintentandoId !== correo.id;
}

@Component({
  selector: 'app-correos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './correos.html'
})
export class CorreosComponent implements OnInit {
  private readonly correoService = inject(CorreoService);
  private readonly toastService = inject(ToastService);

  correos = signal<CorreoCola[]>([]);
  totalCorreos = signal(0);
  page = signal(0);
  size = signal(10);
  filtroEstado = signal<string>('');
  filtroDestinatario = signal<string>('');
  cargando = signal(false);
  reintentandoId = signal<number | null>(null);

  // Modal para ver detalles
  correoSeleccionado = signal<CorreoCola | null>(null);
  mostrarModal = signal(false);

  ngOnInit(): void {
    this.cargarCorreos(true);
  }

  cargarCorreos(mostrarSpinner = true): void {
    if (mostrarSpinner) {
      this.cargando.set(true);
    }
    this.correoService.obtenerPaginado(
      this.page(),
      this.size(),
      this.filtroEstado() || undefined,
      this.filtroDestinatario() || undefined
    ).subscribe({
      next: (res) => {
        this.correos.set(res.contenido);
        this.totalCorreos.set(res.totalElementos);
        this.cargando.set(false);
      },
      error: () => {
        if (mostrarSpinner) {
          this.toastService.error('No se pudieron cargar los correos.');
        }
        this.cargando.set(false);
      }
    });
  }

  cambiarPagina(nuevaPagina: number): void {
    this.page.set(nuevaPagina);
    this.cargarCorreos(true);
  }

  aplicarFiltros(): void {
    this.page.set(0);
    this.cargarCorreos(true);
  }

  reintentar(correo: CorreoCola): void {
    if (!puedeReintentarCorreo(correo, this.reintentandoId())) return;

    this.reintentandoId.set(correo.id);
    this.correoService.reintentar(correo.id)
      .pipe(finalize(() => this.reintentandoId.set(null)))
      .subscribe({
      next: correoActualizado => {
        this.correos.update(correos => correos.map(item =>
          item.id === correoActualizado.id ? correoActualizado : item
        ));
        if (this.correoSeleccionado()?.id === correoActualizado.id) {
          this.correoSeleccionado.set(correoActualizado);
        }
        this.toastService.success('El correo se marcó como PENDIENTE para reintento.');
        this.cargarCorreos(false);
      },
      error: err => {
        this.toastService.error(err?.error?.error || 'No se pudo reintentar el correo.');
      }
    });
  }

  eliminar(id: number): void {
    if (!confirm('¿Seguro que deseas eliminar este correo de la cola?')) return;
    this.correoService.eliminar(id).subscribe({
      next: () => {
        this.toastService.success('Correo eliminado de la cola.');
        this.cargarCorreos();
      },
      error: () => {
        this.toastService.error('No se pudo eliminar el correo.');
      }
    });
  }

  verDetalles(correo: CorreoCola): void {
    this.correoSeleccionado.set(correo);
    this.mostrarModal.set(true);
  }

  cerrarModal(): void {
    this.mostrarModal.set(false);
    this.correoSeleccionado.set(null);
  }

  claseEstado(estado: string): string {
    if (estado === 'ENVIADO') return 'bg-green-50 border-green-200 text-green-700';
    if (estado === 'PENDIENTE') return 'bg-blue-50 border-blue-200 text-blue-700';
    if (estado === 'FALLIDO') return 'bg-red-50 border-red-200 text-red-700';
    return 'bg-slate-50 border-slate-200 text-slate-700';
  }
}
