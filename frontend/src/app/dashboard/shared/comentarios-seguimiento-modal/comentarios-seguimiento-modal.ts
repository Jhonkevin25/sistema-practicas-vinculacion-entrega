import { CommonModule } from '@angular/common';
import { Component, EventEmitter, inject, Input, OnChanges, OnDestroy, OnInit, Output, signal, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import {
  AudienciaComentario,
  ComentarioSeguimiento,
  ComentarioSeguimientoService,
  ProcesoComentario
} from '../../../core/services/comentario-seguimiento.service';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-comentarios-seguimiento-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './comentarios-seguimiento-modal.html'
})
export class ComentariosSeguimientoModalComponent implements OnChanges, OnInit, OnDestroy {
  private readonly service = inject(ComentarioSeguimientoService);
  private readonly toast = inject(ToastService);

  @Input({ required: true }) proceso!: ProcesoComentario;
  @Input({ required: true }) expedienteId!: number;
  @Input() titulo = 'Comunicaciones del expediente';
  @Input() rol = '';
  @Input() cerrado = false;
  @Output() cerrar = new EventEmitter<void>();

  readonly comentarios = signal<ComentarioSeguimiento[]>([]);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  mensaje = '';
  audiencia: AudienciaComentario = 'TUTOR';

  // Mientras el modal está abierto, refresca en segundo plano cada 15s para
  // ver los mensajes que registre la otra parte (tutor/coordinación/estudiante)
  // sin necesitar cerrar y volver a abrir.
  private pollingId: ReturnType<typeof setInterval> | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['expedienteId'] || changes['proceso'] || changes['rol']) {
      this.audiencia = this.audiencias()[0]?.valor || 'TUTOR';
      if (this.expedienteId) this.cargar();
    }
  }

  ngOnInit(): void {
    this.pollingId = setInterval(() => this.cargar(true), 15000);
  }

  ngOnDestroy(): void {
    if (this.pollingId !== null) {
      clearInterval(this.pollingId);
      this.pollingId = null;
    }
  }

  audiencias(): { valor: AudienciaComentario; etiqueta: string }[] {
    if (this.rol === 'ESTUDIANTE') return [{ valor: 'TUTOR', etiqueta: 'Tutor académico' }];
    if (this.rol === 'TUTOR') return [
      { valor: 'ESTUDIANTE', etiqueta: 'Estudiante' },
      { valor: 'COORDINACION', etiqueta: 'Coordinación' }
    ];
    return [
      { valor: 'ESTUDIANTE', etiqueta: 'Estudiante' },
      { valor: 'TUTOR', etiqueta: 'Tutor académico' },
      { valor: 'TODOS', etiqueta: 'Estudiante y tutor' }
    ];
  }

  cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);
    this.service.getByExpediente(this.proceso, this.expedienteId)
      .pipe(finalize(() => { if (!silencioso) this.cargando.set(false); }))
      .subscribe({
        next: comentarios => this.comentarios.set(comentarios),
        error: err => { if (!silencioso) this.toast.error(err?.error?.error || 'No se pudieron cargar las comunicaciones.'); }
      });
  }

  enviar(): void {
    const mensaje = this.mensaje.trim();
    if (mensaje.length < 5) {
      this.toast.warning('Escribe un mensaje de al menos 5 caracteres.');
      return;
    }
    this.guardando.set(true);
    this.service.create({
      practicaId: this.proceso === 'PRACTICAS' ? this.expedienteId : null,
      vinculacionId: this.proceso === 'VINCULACION' ? this.expedienteId : null,
      audiencia: this.audiencia,
      mensaje
    }).pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: comentario => {
        this.comentarios.update(actuales => [...actuales, comentario]);
        this.mensaje = '';
        this.toast.success('Comentario enviado y destinatario notificado.');
      },
      error: err => this.toast.error(err?.error?.error || 'No se pudo enviar el comentario.')
    });
  }

  etiquetaAudiencia(audiencia: string): string {
    return {
      ESTUDIANTE: 'Para estudiante', TUTOR: 'Para tutor',
      COORDINACION: 'Para coordinación', TODOS: 'Para estudiante y tutor'
    }[audiencia] || audiencia;
  }
}
