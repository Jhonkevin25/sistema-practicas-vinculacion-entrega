import { CommonModule } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, Observable, of } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { NotaCoordinacion, NotaCoordinacionService } from '../../core/services/nota-coordinacion.service';
import { PracticaService, SeguimientoPractica } from '../../core/services/practica.service';
import { ToastService } from '../../core/services/toast.service';
import { SeguimientoVinculacion, VinculacionService } from '../../core/services/vinculacion.service';
import {
  EstadoFinalizacion,
  FinalizacionExcepcional,
  LineaTiempoExpediente,
  LineaTiempoService,
  estadoExpedienteTexto
} from '../../core/services/linea-tiempo.service';
import { LineaTiempoExpedienteComponent } from '../shared/linea-tiempo-expediente/linea-tiempo-expediente';
import { ComentariosSeguimientoModalComponent } from '../shared/comentarios-seguimiento-modal/comentarios-seguimiento-modal';

export type ProcesoSeguimiento = 'PRACTICAS' | 'VINCULACION';
export type FiltroProceso = 'TODOS' | ProcesoSeguimiento;
export type FiltroPendiente = 'TODOS' | 'RIESGO' | 'BITACORAS' | 'NOTAS' | 'ENCUESTAS' | 'CIERRE';

export interface SeguimientoUnificado {
  clave: string;
  expedienteId: number;
  proceso: ProcesoSeguimiento;
  estudianteId: number;
  estudiante: string;
  matricula: string;
  carrera: string;
  entidad: string;
  tutor: string;
  periodoAcademico: string;
  estado: string;
  motivoFinalizacion?: string;
  finalizadoEn?: string;
  horasRequeridas: number;
  horasCompletadas: number;
  porcentajeAvance: number;
  bitacorasPendientes: number;
  bitacorasRechazadas: number;
  bitacorasCorreccion: number;
  diasSinActividad: number | null;
  parcialesCerrados: number;
  notasTutorPendientes: number;
  notasCoordPendientes: number;
  encuestasPendientes: number;
  listoParaCierre: boolean;
  riesgo: boolean;
  alertas: string[];
}

export interface FiltrosSeguimiento {
  busqueda: string;
  proceso: FiltroProceso;
  pendiente: FiltroPendiente;
}

type SeguimientoOrigen = SeguimientoPractica | SeguimientoVinculacion;

// Espejo de VinculacionController/PracticaController.esEstadoTerminalSeguimiento:
// una vez cerrado el expediente, sus contadores historicos (bitacoras que en su
// momento fueron rechazadas/observadas, notas o encuestas que quedaron sin
// completar en un retiro) ya no son una prioridad de coordinacion pendiente.
function esTerminal(estado: string): boolean {
  return ['completado', 'reprobado', 'retirado'].includes((estado || '').toLowerCase());
}

function normalizar(
  item: SeguimientoOrigen,
  proceso: ProcesoSeguimiento,
  expedienteId: number,
  entidad?: string
): SeguimientoUnificado {
  return {
    clave: `${proceso}-${expedienteId}`,
    expedienteId,
    proceso,
    estudianteId: item.estudianteId,
    estudiante: item.estudiante,
    matricula: item.matricula || '',
    carrera: item.carrera || 'Sin carrera registrada',
    entidad: entidad || 'Sin entidad asignada',
    tutor: item.tutor || 'Sin asignar',
    periodoAcademico: item.periodoAcademico || 'Sin periodo',
    estado: item.estado || 'pendiente',
    motivoFinalizacion: item.motivoFinalizacion,
    finalizadoEn: item.finalizadoEn,
    horasRequeridas: item.horasRequeridas || 0,
    horasCompletadas: item.horasCompletadas || 0,
    porcentajeAvance: item.porcentajeAvance || 0,
    bitacorasPendientes: item.bitacorasPendientes || 0,
    bitacorasRechazadas: item.bitacorasRechazadas || 0,
    bitacorasCorreccion: item.bitacorasCorreccion || 0,
    diasSinActividad: item.diasSinActividad === null || item.diasSinActividad === undefined
      ? null
      : Math.max(0, item.diasSinActividad),
    parcialesCerrados: item.parcialesCerrados || 0,
    notasTutorPendientes: item.notasTutorPendientes || 0,
    notasCoordPendientes: item.notasCoordPendientes || 0,
    encuestasPendientes: item.encuestasPendientes || 0,
    listoParaCierre: item.listoParaCierre,
    riesgo: item.riesgo,
    alertas: item.alertas || []
  };
}

export function puedeFinalizarExpediente(item: Pick<SeguimientoUnificado, 'estado'>): boolean {
  return ['pendiente', 'en_curso'].includes(String(item.estado || '').trim().toLowerCase());
}

export function validarFinalizacion(
  item: Pick<SeguimientoUnificado, 'estado'>,
  estado: EstadoFinalizacion,
  motivo: string
): string | null {
  const estadoActual = String(item.estado || '').trim().toLowerCase();
  if (!puedeFinalizarExpediente(item)) return 'Este expediente ya se encuentra en un estado terminal.';
  if (!['REPROBADO', 'RETIRADO'].includes(estado)) return 'Selecciona un estado de finalización válido.';
  if (estado === 'REPROBADO' && estadoActual !== 'en_curso') {
    return 'Reprobado solo está disponible para expedientes en curso.';
  }
  if (motivo.trim().length < 10) return 'El motivo debe tener al menos 10 caracteres.';
  return null;
}

export function unificarSeguimientos(
  practicas: SeguimientoPractica[],
  vinculaciones: SeguimientoVinculacion[]
): SeguimientoUnificado[] {
  const items = [
    ...practicas.map(item => normalizar(item, 'PRACTICAS', item.practicaId, item.empresa)),
    ...vinculaciones.map(item => normalizar(item, 'VINCULACION', item.vinculacionId, item.proyecto))
  ];

  return items.sort((a, b) => {
    const prioridadA = a.riesgo ? 0 : a.listoParaCierre ? 1 : 2;
    const prioridadB = b.riesgo ? 0 : b.listoParaCierre ? 1 : 2;
    return prioridadA - prioridadB || a.estudiante.localeCompare(b.estudiante, 'es');
  });
}

export function filtrarSeguimientos(
  items: SeguimientoUnificado[],
  filtros: FiltrosSeguimiento
): SeguimientoUnificado[] {
  const busqueda = filtros.busqueda.trim().toLocaleLowerCase('es');

  return items.filter(item => {
    if (filtros.proceso !== 'TODOS' && item.proceso !== filtros.proceso) return false;

    if (busqueda) {
      const contenido = [item.estudiante, item.matricula, item.carrera, item.entidad, item.tutor]
        .join(' ')
        .toLocaleLowerCase('es');
      if (!contenido.includes(busqueda)) return false;
    }

    if (filtros.pendiente === 'RIESGO') return item.riesgo;
    if (filtros.pendiente === 'BITACORAS') {
      return !esTerminal(item.estado)
        && item.bitacorasPendientes + item.bitacorasRechazadas + item.bitacorasCorreccion > 0;
    }
    if (filtros.pendiente === 'NOTAS') {
      return !esTerminal(item.estado) && item.notasTutorPendientes + item.notasCoordPendientes > 0;
    }
    if (filtros.pendiente === 'ENCUESTAS') return !esTerminal(item.estado) && item.encuestasPendientes > 0;
    if (filtros.pendiente === 'CIERRE') return item.listoParaCierre;
    return true;
  });
}

@Component({
  selector: 'app-seguimiento',
  standalone: true,
  imports: [CommonModule, FormsModule, LineaTiempoExpedienteComponent, ComentariosSeguimientoModalComponent],
  templateUrl: './seguimiento.html'
})
export class SeguimientoComponent implements OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly practicaService = inject(PracticaService);
  private readonly vinculacionService = inject(VinculacionService);
  private readonly toastService = inject(ToastService);
  private readonly lineaTiempoService = inject(LineaTiempoService);
  private readonly notaCoordinacionService = inject(NotaCoordinacionService);
  private readonly router = inject(Router);

  readonly cargando = signal(true);
  readonly items = signal<SeguimientoUnificado[]>([]);
  readonly busqueda = signal('');
  readonly proceso = signal<FiltroProceso>('TODOS');
  readonly pendiente = signal<FiltroPendiente>('TODOS');

  readonly filtrados = computed(() => filtrarSeguimientos(this.items(), {
    busqueda: this.busqueda(),
    proceso: this.proceso(),
    pendiente: this.pendiente()
  }));
  readonly enRiesgo = computed(() => this.items().filter(item => item.riesgo).length);
  readonly bitacorasObservadas = computed(() => this.items()
    .filter(item => !esTerminal(item.estado))
    .reduce((total, item) => total + item.bitacorasRechazadas + item.bitacorasCorreccion, 0));
  readonly notasPendientes = computed(() => this.items()
    .filter(item => !esTerminal(item.estado))
    .reduce((total, item) => total + item.notasTutorPendientes + item.notasCoordPendientes, 0));
  readonly listosParaCierre = computed(() => this.items().filter(item => item.listoParaCierre).length);
  readonly historialExpediente = signal<SeguimientoUnificado | null>(null);
  readonly historial = signal<LineaTiempoExpediente | null>(null);
  readonly cargandoHistorial = signal(false);
  readonly finalizacionExpediente = signal<SeguimientoUnificado | null>(null);
  readonly estadoFinalizacion = signal<EstadoFinalizacion>('RETIRADO');
  readonly motivoFinalizacion = signal('');
  readonly finalizando = signal(false);
  readonly notaExpediente = signal<SeguimientoUnificado | null>(null);
  readonly notasHistorial = signal<NotaCoordinacion[]>([]);
  readonly cargandoNotas = signal(false);
  readonly mensajeNota = signal('');
  readonly guardandoNota = signal(false);
  readonly comentarioExpediente = signal<SeguimientoUnificado | null>(null);

  // Polling discreto (mismo patron que la campana de notificaciones en
  // dashboard.ts): mientras el coordinador tiene esta pantalla abierta, un
  // tutor o estudiante puede estar actuando desde otro dispositivo (aprobar
  // bitacora, subir nota); sin esto el coordinador solo ve ese cambio al
  // recargar la pagina.
  private pollingId: ReturnType<typeof setInterval> | null = null;

  constructor() {
    const rol = this.authService.getRole();
    if (rol !== 'ADMIN' && rol !== 'COORDINADOR') {
      this.cargando.set(false);
      this.router.navigate(['/dashboard/overview']);
      return;
    }
    this.cargar();
    this.pollingId = setInterval(() => this.cargar(true), 45000);
  }

  ngOnDestroy(): void {
    if (this.pollingId !== null) {
      clearInterval(this.pollingId);
      this.pollingId = null;
    }
  }

  cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);
    const tipo = this.authService.getRole() === 'COORDINADOR'
      ? this.authService.coordinacionTipo()
      : 'AMBOS';

    forkJoin({
      practicas: tipo === 'VINCULACION'
        ? of([] as SeguimientoPractica[])
        : this.practicaService.getSeguimiento(silencioso),
      vinculaciones: tipo === 'PRACTICAS'
        ? of([] as SeguimientoVinculacion[])
        : this.vinculacionService.getSeguimiento(silencioso)
    }).pipe(finalize(() => { if (!silencioso) this.cargando.set(false); })).subscribe({
      next: res => this.items.set(unificarSeguimientos(res.practicas, res.vinculaciones)),
      error: err => {
        if (!silencioso) {
          this.toastService.error(err?.error?.error || 'No se pudo cargar el seguimiento académico.');
        }
      }
    });
  }

  puedeVer(proceso: ProcesoSeguimiento): boolean {
    if (this.authService.getRole() === 'ADMIN') return true;
    const tipo = this.authService.coordinacionTipo();
    return tipo === 'AMBOS' || tipo === proceso;
  }

  seleccionarProceso(proceso: FiltroProceso): void {
    this.proceso.set(proceso);
  }

  aplicarPendiente(pendiente: FiltroPendiente): void {
    this.pendiente.set(pendiente);
  }

  limpiarFiltros(): void {
    this.busqueda.set('');
    this.proceso.set('TODOS');
    this.pendiente.set('TODOS');
  }

  abrirHistorial(item: SeguimientoUnificado): void {
    this.historialExpediente.set(item);
    this.historial.set(null);
    this.cargandoHistorial.set(true);
    const carga = item.proceso === 'PRACTICAS'
      ? this.lineaTiempoService.getPractica(item.expedienteId)
      : this.lineaTiempoService.getVinculacion(item.expedienteId);

    carga.pipe(finalize(() => this.cargandoHistorial.set(false))).subscribe({
      next: historial => this.historial.set(historial),
      error: err => this.toastService.error(err?.error?.error || 'No se pudo cargar el historial del expediente.')
    });
  }

  cerrarHistorial(): void {
    this.historialExpediente.set(null);
    this.historial.set(null);
  }

  abrirComunicaciones(item: SeguimientoUnificado): void {
    this.comentarioExpediente.set(item);
  }

  cerrarComunicaciones(): void {
    this.comentarioExpediente.set(null);
  }

  abrirNota(item: SeguimientoUnificado): void {
    this.notaExpediente.set(item);
    this.mensajeNota.set('');
    this.notasHistorial.set([]);
    this.cargandoNotas.set(true);
    this.notaCoordinacionService.getByEstudiante(item.estudianteId).subscribe({
      next: notas => {
        this.cargandoNotas.set(false);
        this.notasHistorial.set(notas.filter(n =>
          item.proceso === 'PRACTICAS' ? n.practica?.id === item.expedienteId : n.vinculacion?.id === item.expedienteId));
      },
      error: err => {
        this.cargandoNotas.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo cargar el historial de notas.');
      }
    });
  }

  cerrarNota(): void {
    if (this.guardandoNota()) return;
    this.notaExpediente.set(null);
    this.mensajeNota.set('');
    this.notasHistorial.set([]);
  }

  confirmarNota(): void {
    const item = this.notaExpediente();
    const mensaje = this.mensajeNota().trim();
    if (!item) return;
    if (mensaje.length < 5) {
      this.toastService.warning('La nota debe tener al menos 5 caracteres.');
      return;
    }

    this.guardandoNota.set(true);
    this.notaCoordinacionService.crear({
      estudianteId: item.estudianteId,
      practicaId: item.proceso === 'PRACTICAS' ? item.expedienteId : undefined,
      vinculacionId: item.proceso === 'VINCULACION' ? item.expedienteId : undefined,
      mensaje
    }).pipe(finalize(() => this.guardandoNota.set(false))).subscribe({
      next: () => {
        this.cerrarNota();
        this.cargar();
        this.toastService.success('Nota de coordinación registrada. El estudiante fue notificado.');
      },
      error: (err: any) => this.toastService.error(err?.error?.error || 'No se pudo guardar la nota.')
    });
  }

  abrirFinalizacion(item: SeguimientoUnificado): void {
    if (!puedeFinalizarExpediente(item)) return;
    this.finalizacionExpediente.set(item);
    this.estadoFinalizacion.set('RETIRADO');
    this.motivoFinalizacion.set('');
  }

  cerrarFinalizacion(): void {
    if (this.finalizando()) return;
    this.finalizacionExpediente.set(null);
    this.motivoFinalizacion.set('');
  }

  confirmarFinalizacion(): void {
    const item = this.finalizacionExpediente();
    if (!item) return;
    const motivo = this.motivoFinalizacion().trim();
    const error = validarFinalizacion(item, this.estadoFinalizacion(), motivo);
    if (error) {
      this.toastService.warning(error);
      return;
    }

    const payload: FinalizacionExcepcional = {
      estado: this.estadoFinalizacion(),
      motivo
    };
    this.finalizando.set(true);
    const solicitud: Observable<unknown> = item.proceso === 'PRACTICAS'
      ? this.lineaTiempoService.finalizarPractica(item.expedienteId, payload)
      : this.lineaTiempoService.finalizarVinculacion(item.expedienteId, payload);

    solicitud.pipe(finalize(() => this.finalizando.set(false))).subscribe({
      next: () => {
        this.cerrarFinalizacion();
        this.cargar();
        this.toastService.success(`Expediente finalizado como ${estadoExpedienteTexto(payload.estado)}.`);
      },
      error: (err: any) => this.toastService.error(err?.error?.error || 'No se pudo finalizar el expediente.')
    });
  }

  abrirModulo(item: SeguimientoUnificado): void {
    const ruta = item.proceso === 'PRACTICAS' ? '/dashboard/practicas' : '/dashboard/vinculacion';
    this.router.navigate([ruta]);
  }

  nombreProceso(proceso: ProcesoSeguimiento): string {
    return proceso === 'PRACTICAS' ? 'Prácticas' : 'Vinculación';
  }

  estadoTexto(estado: string): string {
    return estadoExpedienteTexto(estado);
  }

  puedeFinalizarExpediente(item: SeguimientoUnificado): boolean {
    return puedeFinalizarExpediente(item);
  }

  esTerminal(estado: string): boolean {
    return ['completado', 'reprobado', 'retirado'].includes(String(estado || '').toLowerCase());
  }

  rolActual(): string {
    return this.authService.getRole() || '';
  }
}
