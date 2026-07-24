import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Asistencia, AsistenciaService } from '../../core/services/asistencia.service';
import { Estudiante, EstudianteService } from '../../core/services/estudiante.service';
import { EvaluacionDetalle, EvaluacionService } from '../../core/services/evaluacion.service';
import { NotaAcademica, NotaAcademicaService } from '../../core/services/nota-academica.service';
import { NotaCoordinacion, NotaCoordinacionService } from '../../core/services/nota-coordinacion.service';
import { Practica, PracticaService } from '../../core/services/practica.service';
import { Vinculacion, VinculacionService } from '../../core/services/vinculacion.service';
import { AsistenciasHistorialPanelComponent } from '../shared/asistencias-historial-panel/asistencias-historial-panel';
import { SIN_PERIODO, coincidePeriodo, periodosDisponibles } from '../shared/periodo-avance';

// Módulo propio del estudiante: consulta de notas académicas y asistencias
// fuera de Prácticas/Vinculación, cargado solo cuando entra a la ruta.

export interface OpcionExpediente {
  clave: string; // 'P-<id>' | 'V-<id>'
  tipo: 'PRACTICA' | 'VINCULACION';
  id: number;
  etiqueta: string;
  activo: boolean;
  periodo: string;
}

// Periodo por defecto de notas: el periodo actual del estudiante si tiene
// notas registradas ahí; si no, el más reciente con notas
export function periodoNotasPorDefecto(periodos: string[], periodoEstudiante?: string | null): string {
  if (periodoEstudiante && periodos.includes(periodoEstudiante)) return periodoEstudiante;
  return periodos.find(periodo => periodo !== SIN_PERIODO) || periodos[0] || 'TODOS';
}

// Expedientes del estudiante como opciones del selector: activos primero,
// luego del periodo más reciente al más antiguo
export function construirOpcionesExpediente(
  practicas: Practica[],
  vinculaciones: Vinculacion[]
): OpcionExpediente[] {
  const esActivo = (estado?: string) =>
    ['pendiente', 'en_curso'].includes(String(estado || '').toLowerCase());
  const opciones: OpcionExpediente[] = [
    ...vinculaciones.filter(v => v.id != null).map(v => ({
      clave: `V-${v.id}`,
      tipo: 'VINCULACION' as const,
      id: v.id!,
      etiqueta: `Vinculación — ${v.proyecto?.nombre || 'Proyecto'}${v.periodoAcademico ? ' · ' + v.periodoAcademico : ''}`,
      activo: esActivo(v.estado),
      periodo: v.periodoAcademico || ''
    })),
    ...practicas.filter(p => p.id != null).map(p => ({
      clave: `P-${p.id}`,
      tipo: 'PRACTICA' as const,
      id: p.id!,
      etiqueta: `Práctica — ${p.empresa?.nombre || 'Empresa'}${p.periodoAcademico ? ' · ' + p.periodoAcademico : ''}`,
      activo: esActivo(p.estado),
      periodo: p.periodoAcademico || ''
    }))
  ];
  return opciones.sort((a, b) =>
    a.activo === b.activo ? b.periodo.localeCompare(a.periodo) : (a.activo ? -1 : 1));
}

@Component({
  selector: 'app-expediente-academico',
  standalone: true,
  imports: [CommonModule, FormsModule, AsistenciasHistorialPanelComponent],
  templateUrl: './expediente-academico.html'
})
export class ExpedienteAcademicoComponent {
  private readonly notaAcademicaService = inject(NotaAcademicaService);
  private readonly notaCoordinacionService = inject(NotaCoordinacionService);
  private readonly asistenciaService = inject(AsistenciaService);
  private readonly practicaService = inject(PracticaService);
  private readonly vinculacionService = inject(VinculacionService);
  private readonly estudianteService = inject(EstudianteService);
  private readonly evaluacionService = inject(EvaluacionService);

  readonly SIN_PERIODO = SIN_PERIODO;

  loading = signal(true);
  estudiante = signal<Estudiante | null>(null);
  evaluaciones = signal<EvaluacionDetalle[]>([]);
  notas = signal<NotaAcademica[]>([]);
  periodoNotas = signal('TODOS');
  opciones = signal<OpcionExpediente[]>([]);
  // Consulta por periodo académico: filtra qué expedientes (y con ellos sus
  // parciales y asistencias) se muestran
  periodoExpediente = signal('TODOS');
  expedienteClave = signal<string | null>(null);
  asistenciasCargando = signal(false);
  asistenciasExpediente = signal<Asistencia[]>([]);
  notasCoordinacionCargando = signal(false);
  notasCoordinacionExpediente = signal<NotaCoordinacion[]>([]);
  // Lazy: las asistencias y notas de coordinación se descargan una sola vez, en la primera consulta
  private todasAsistencias: Asistencia[] | null = null;
  private todasNotasCoordinacion: NotaCoordinacion[] | null = null;

  constructor() {
    forkJoin({
      notas: this.notaAcademicaService.getMisNotas().pipe(catchError(() => of([] as NotaAcademica[]))),
      practicas: this.practicaService.getMine().pipe(catchError(() => of([] as Practica[]))),
      vinculaciones: this.vinculacionService.getMine().pipe(catchError(() => of([] as Vinculacion[]))),
      evaluaciones: this.evaluacionService.getAll().pipe(catchError(() => of([] as EvaluacionDetalle[]))),
      estudiante: this.estudianteService.getMe().pipe(catchError(() => of(null as Estudiante | null)))
    }).subscribe(res => {
      this.estudiante.set(res.estudiante);
      this.evaluaciones.set(res.evaluaciones);
      this.notas.set([...res.notas].sort((a, b) =>
        String(b.periodoAcademico || '').localeCompare(String(a.periodoAcademico || ''))));
      this.periodoNotas.set(periodoNotasPorDefecto(
        this.periodosNotas(), res.estudiante?.periodoAcademico));
      this.opciones.set(construirOpcionesExpediente(res.practicas, res.vinculaciones));
      // Por defecto, el periodo del expediente más relevante (activo primero)
      const primera = this.opciones()[0];
      this.periodoExpediente.set(primera?.periodo ? primera.periodo : 'TODOS');
      if (primera) this.seleccionarExpediente(primera.clave);
      this.loading.set(false);
    });
  }

  periodosExpedientes(): string[] {
    return periodosDisponibles(this.opciones().map(opcion => opcion.periodo || null));
  }

  opcionesFiltradas(): OpcionExpediente[] {
    return this.opciones().filter(opcion =>
      coincidePeriodo(this.periodoExpediente(), opcion.periodo || null));
  }

  cambiarPeriodoExpediente(periodo: string): void {
    this.periodoExpediente.set(periodo);
    const primera = this.opcionesFiltradas()[0];
    this.seleccionarExpediente(primera ? primera.clave : null);
  }

  periodosNotas(): string[] {
    return periodosDisponibles(this.notas().map(nota => nota.periodoAcademico));
  }

  notasFiltradas(): NotaAcademica[] {
    return this.notas().filter(nota =>
      coincidePeriodo(this.periodoNotas(), nota.periodoAcademico));
  }

  notaAnteriorVerificada(): NotaAcademica | undefined {
    const est = this.estudiante();
    if (!est?.semestre || est.semestre <= 1) return undefined;
    return this.notas().find(n => n.semestre === est.semestre! - 1 && n.estado === 'VERIFICADO');
  }

  opcionSeleccionada(): OpcionExpediente | null {
    return this.opciones().find(opcion => opcion.clave === this.expedienteClave()) || null;
  }

  // Calificaciones por parcial del expediente seleccionado: misma lógica de
  // "Mi avance" (la nota final se revela al completar la encuesta), solo lectura
  private evaluacionDe(parcial: number): EvaluacionDetalle | undefined {
    const opcion = this.opcionSeleccionada();
    if (!opcion) return undefined;
    return this.evaluaciones().find(ev => ev.parcial === parcial
      && (opcion.tipo === 'PRACTICA'
        ? ev.practica?.id === opcion.id
        : ev.vinculacion?.id === opcion.id));
  }

  encuestaCompletada(parcial: number): boolean {
    return !!this.evaluacionDe(parcial)?.encuestaCompletada;
  }

  notasRegistradas(parcial: number): boolean {
    const ev = this.evaluacionDe(parcial);
    return !!ev && (ev.notaTutor || 0) > 0 && (ev.notaCoord || 0) > 0;
  }

  notaFinalParcial(parcial: number): number {
    return this.evaluacionDe(parcial)?.notaFinal || 0;
  }

  seleccionarExpediente(clave: string | null): void {
    this.expedienteClave.set(clave);
    const opcion = clave ? this.opciones().find(o => o.clave === clave) : undefined;
    if (!opcion) {
      this.asistenciasExpediente.set([]);
      this.notasCoordinacionExpediente.set([]);
      return;
    }
    if (this.todasAsistencias && this.todasNotasCoordinacion) {
      this.filtrarAsistencias(opcion);
      this.filtrarNotasCoordinacion(opcion);
      return;
    }
    this.asistenciasCargando.set(true);
    this.notasCoordinacionCargando.set(true);
    forkJoin({
      asistencias: this.asistenciaService.getMine().pipe(catchError(() => of([] as Asistencia[]))),
      notas: this.notaCoordinacionService.getMisNotas().pipe(catchError(() => of([] as NotaCoordinacion[])))
    }).subscribe(({ asistencias, notas }) => {
      this.todasAsistencias = asistencias;
      this.todasNotasCoordinacion = notas;
      this.asistenciasCargando.set(false);
      this.notasCoordinacionCargando.set(false);
      this.filtrarAsistencias(opcion);
      this.filtrarNotasCoordinacion(opcion);
    });
  }

  private filtrarAsistencias(opcion: OpcionExpediente): void {
    const todas = this.todasAsistencias || [];
    this.asistenciasExpediente.set(todas.filter(asistencia =>
      opcion.tipo === 'PRACTICA'
        ? asistencia.practica?.id === opcion.id
        : asistencia.vinculacion?.id === opcion.id));
  }

  private filtrarNotasCoordinacion(opcion: OpcionExpediente): void {
    const todas = this.todasNotasCoordinacion || [];
    this.notasCoordinacionExpediente.set(todas
      .filter(nota => opcion.tipo === 'PRACTICA'
        ? nota.practica?.id === opcion.id
        : nota.vinculacion?.id === opcion.id)
      .sort((a, b) => String(b.fechaCreacion || '').localeCompare(String(a.fechaCreacion || ''))));
  }
}
