import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Chart } from 'chart.js/auto';
import { AuthService } from '../../core/services/auth.service';
import { DocumentoService, DocEstudiante } from '../../core/services/documento.service';
import { ToastService } from '../../core/services/toast.service';
import { EstudianteService } from '../../core/services/estudiante.service';
import { EmpresaService } from '../../core/services/empresa.service';
import { PracticaService } from '../../core/services/practica.service';
import { VinculacionService } from '../../core/services/vinculacion.service';
import { EvaluacionService, EvaluacionDetalle } from '../../core/services/evaluacion.service';
import { ConfiguracionService, FechaLimiteCalificacion } from '../../core/services/configuracion.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Estudiante } from '../../core/services/estudiante.service';
import { Empresa } from '../../core/services/empresa.service';
import { Practica } from '../../core/services/practica.service';
import { Vinculacion } from '../../core/services/vinculacion.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import { ReporteService, AsignacionReporte, CupoReporte, TipoReporte } from '../../core/services/reporte.service';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './overview.html',
  styleUrl: './overview.css'
})
export class OverviewComponent {
  readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly estudianteService = inject(EstudianteService);
  private readonly empresaService = inject(EmpresaService);
  private readonly practicaService = inject(PracticaService);
  private readonly vinculacionService = inject(VinculacionService);
  private readonly evaluacionService = inject(EvaluacionService);
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly expediente = inject(ExpedienteService);
  private readonly documentoService = inject(DocumentoService);
  private readonly reporteService = inject(ReporteService);
  private readonly router = inject(Router);

  userRole = signal('');
  userName = signal('');
  loading = signal(true);

  // Coordinator stats
  totalEstudiantes = signal(0);
  totalEmpresas = signal(0);
  totalPracticas = signal(0);
  totalVinculaciones = signal(0);

  // Dashboard analítico (solo gestión): datos de /api/reportes con el
  // alcance del coordinador aplicado en el backend.
  reporteAsignaciones = signal<AsignacionReporte[]>([]);
  reporteCupos = signal<CupoReporte[]>([]);
  cargandoReportes = signal(false);
  exportando = signal<TipoReporte | null>(null);

  private readonly canvasEstados = viewChild<ElementRef<HTMLCanvasElement>>('chartEstados');
  private readonly canvasCupos = viewChild<ElementRef<HTMLCanvasElement>>('chartCupos');
  private graficoEstados: Chart | null = null;
  private graficoCupos: Chart | null = null;

  // Orden fijo de estados (identidad → color estable, paleta validada CVD)
  private readonly ESTADOS_GRAFICO = [
    { clave: 'en_curso', etiqueta: 'En curso', color: '#1a56db' },
    { clave: 'pendiente', etiqueta: 'Pendientes', color: '#d97706' },
    { clave: 'completado', etiqueta: 'Completados', color: '#059669' },
    { clave: 'retirado', etiqueta: 'Retirados', color: '#7c3aed' }
  ];

  // Student flow state
  currentStep = signal<'VINCULACION' | 'PRACTICA_1' | 'PRACTICA_2'>('VINCULACION');
  stepStatuses = signal({
    vinculacion: 'en_curso',
    practica1: 'bloqueado',
    practica2: 'bloqueado'
  });
  studentHours = signal({
    completed: 0,
    required: 160
  });
  assignedTutorName = signal('Sin asignar');
  assignedCompanyName = signal('Sin asignar');

  // Documentos reales del expediente (checklist del Inicio del estudiante)
  documentosEstado = signal<DocEstudiante[]>([]);
  procesoActivo = signal<'VINCULACION' | 'PRACTICAS'>('VINCULACION');

  // Grades list (se llena desde la BD en loadStats)
  parcialesGrades = signal([
    { parcial: 1, tutor: 0, coordinador: 0, final: 0, limitDate: '—', surveyCompleted: false },
    { parcial: 2, tutor: 0, coordinador: 0, final: 0, limitDate: '—', surveyCompleted: false },
    { parcial: 3, tutor: 0, coordinador: 0, final: 0, limitDate: '—', surveyCompleted: false }
  ]);

  constructor() {
    const user = this.authService.currentUser();
    if (user) {
      this.userRole.set(user.rol);
      this.userName.set(`${user.nombre} ${user.apellido}`);
    }
    this.loadStats();
    // Redibuja los gráficos cuando llegan los datos o aparece el canvas
    effect(() => this.renderCharts());
  }

  private loadReportes(): void {
    this.cargandoReportes.set(true);
    forkJoin({
      asignaciones: this.reporteService.asignaciones({}).pipe(catchError(() => of([] as AsignacionReporte[]))),
      cupos: this.reporteService.cupos({}).pipe(catchError(() => of([] as CupoReporte[])))
    }).subscribe(res => {
      this.reporteAsignaciones.set(res.asignaciones);
      this.reporteCupos.set(res.cupos);
      this.cargandoReportes.set(false);
    });
  }

  // Distribución por estado para el gráfico de anillo (solo categorías con datos)
  distribucionEstados(): { etiqueta: string; valor: number; color: string }[] {
    const filas = this.reporteAsignaciones();
    return this.ESTADOS_GRAFICO
      .map(e => ({
        etiqueta: e.etiqueta,
        color: e.color,
        valor: filas.filter(f => (f.estado || '').toLowerCase() === e.clave).length
      }))
      .filter(e => e.valor > 0);
  }

  // Top de entidades con cupos disponibles para el gráfico de barras
  cuposPorEntidad(): { entidad: string; disponibles: number }[] {
    const porEntidad = new Map<string, number>();
    for (const c of this.reporteCupos()) {
      const clave = c.entidad || 'Sin entidad';
      porEntidad.set(clave, (porEntidad.get(clave) || 0) + (c.cuposDisponibles || 0));
    }
    return Array.from(porEntidad, ([entidad, disponibles]) => ({ entidad, disponibles }))
      .sort((a, b) => b.disponibles - a.disponibles)
      .slice(0, 8);
  }

  private renderCharts(): void {
    const distribucion = this.distribucionEstados();
    const cupos = this.cuposPorEntidad();
    const canvasEstados = this.canvasEstados()?.nativeElement;
    const canvasCupos = this.canvasCupos()?.nativeElement;

    if (canvasEstados && distribucion.length > 0) {
      this.graficoEstados?.destroy();
      this.graficoEstados = new Chart(canvasEstados, {
        type: 'doughnut',
        data: {
          labels: distribucion.map(d => d.etiqueta),
          datasets: [{
            data: distribucion.map(d => d.valor),
            backgroundColor: distribucion.map(d => d.color),
            borderColor: '#ffffff',
            borderWidth: 2,
            hoverOffset: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '65%',
          animation: { animateRotate: true, duration: 800 },
          plugins: {
            legend: {
              position: 'bottom',
              labels: { color: '#334155', boxWidth: 10, boxHeight: 10, usePointStyle: true, font: { size: 11 } }
            }
          }
        }
      });
    }

    if (canvasCupos && cupos.length > 0) {
      this.graficoCupos?.destroy();
      this.graficoCupos = new Chart(canvasCupos, {
        type: 'bar',
        data: {
          labels: cupos.map(c => c.entidad),
          datasets: [{
            label: 'Cupos disponibles',
            data: cupos.map(c => c.disponibles),
            backgroundColor: '#1a56db',
            hoverBackgroundColor: '#002f6c',
            borderRadius: 4,
            maxBarThickness: 26
          }]
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          animation: { duration: 800 },
          plugins: { legend: { display: false } },
          scales: {
            x: {
              beginAtZero: true,
              ticks: { color: '#64748b', precision: 0, font: { size: 11 } },
              grid: { color: '#eef2f7' }
            },
            y: {
              ticks: { color: '#334155', font: { size: 11 } },
              grid: { display: false }
            }
          }
        }
      });
    }
  }

  exportarReporte(tipo: TipoReporte): void {
    if (this.exportando()) return;
    this.exportando.set(tipo);
    this.reporteService.exportar(tipo, {}).subscribe({
      next: archivo => {
        const url = URL.createObjectURL(archivo);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `reporte_${tipo.toLowerCase()}.csv`;
        enlace.click();
        URL.revokeObjectURL(url);
        this.exportando.set(null);
        this.toastService.success('Reporte CSV generado.');
      },
      error: err => {
        this.exportando.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo exportar el reporte.');
      }
    });
  }

  loadStats(): void {
    this.loading.set(true);
    if (this.userRole() === 'ADMIN' || this.userRole() === 'COORDINADOR') {
      const esCoordinador = this.userRole() === 'COORDINADOR';
      const type = esCoordinador ? this.authService.coordinacionTipo() : 'AMBOS';
      this.loadReportes();

      forkJoin({
        estudiantes: this.estudianteService.getAll(),
        empresas: this.empresaService.getAll(),
        practicas: type === 'VINCULACION' ? of([]) : this.practicaService.getAll(),
        vinculaciones: type === 'PRACTICAS' ? of([]) : this.vinculacionService.getAll()
      }).subscribe({
        next: (res: { estudiantes: Estudiante[], empresas: Empresa[], practicas: Practica[], vinculaciones: Vinculacion[] }) => {
          const assignedCareers = this.authService.carrerasAsignadas();

          // Filter students by assigned careers
          const filteredStudents = esCoordinador
            ? res.estudiantes.filter((e: Estudiante) => assignedCareers.includes(e.carrera))
            : res.estudiantes;
          this.totalEstudiantes.set(filteredStudents.length);

          // Total companies (can show all or filter if company has area match)
          this.totalEmpresas.set(res.empresas.length);

          // Filter practices by career and coordinator type
          if (type === 'VINCULACION') {
            this.totalPracticas.set(0);
          } else {
            const filteredPracticas = esCoordinador
              ? res.practicas.filter((p: Practica) => assignedCareers.includes(p.estudiante.carrera))
              : res.practicas;
            this.totalPracticas.set(filteredPracticas.length);
          }

          // Filter vinculación by career and coordinator type
          if (type === 'PRACTICAS') {
            this.totalVinculaciones.set(0);
          } else {
            const filteredVinculaciones = esCoordinador
              ? res.vinculaciones.filter((v: Vinculacion) => assignedCareers.includes(v.estudiante.carrera))
              : res.vinculaciones;
            this.totalVinculaciones.set(filteredVinculaciones.length);
          }

          this.loading.set(false);
        },
        error: () => {
          this.toastService.error('No se pudieron cargar las estadísticas. Verifica tu conexión.');
          this.loading.set(false);
        }
      });
    } else if (this.userRole() === 'ESTUDIANTE') {
      // Avance REAL del estudiante derivado de su expediente en la BD:
      // Vinculación (160h) -> Práctica I (120h) -> Práctica II (120h)
      forkJoin({
        me: this.estudianteService.getMe().pipe(catchError(() => of(null))),
        practicas: this.practicaService.getAll().pipe(catchError(() => of([] as Practica[]))),
        vinculaciones: this.vinculacionService.getAll().pipe(catchError(() => of([] as Vinculacion[]))),
        evaluaciones: this.evaluacionService.getAll().pipe(catchError(() => of([] as EvaluacionDetalle[]))),
        fechasLimite: this.configuracionService.getFechasLimite().pipe(catchError(() => of([] as FechaLimiteCalificacion[])))
      }).subscribe({
        next: (res) => {
          const myId = res.me?.id;
          const misPracs = res.practicas.filter(p => p.estudiante?.id === myId);
          const misVincs = res.vinculaciones.filter(v => v.estudiante?.id === myId);

          // Misma derivacion de etapa que practicas.ts (compartida en el servicio)
          const etapa = this.expediente.etapaDe(misPracs, misVincs);
          const vincActiva = misVincs.find(v => v.estado === 'en_curso' || v.estado === 'pendiente');
          const pracsCompletadas = misPracs.filter(p => p.estado === 'completado').length;
          const pracActiva = misPracs.find(p => p.estado === 'en_curso' || p.estado === 'pendiente');

          // Documentos reales del proceso activo
          const proceso: 'VINCULACION' | 'PRACTICAS' = etapa === 'Vinculación' ? 'VINCULACION' : 'PRACTICAS';
          this.procesoActivo.set(proceso);
          this.documentoService.getMisDocumentosDetalle(proceso).subscribe({
            next: docs => this.documentosEstado.set(docs),
            error: () => this.documentosEstado.set([])
          });

          if (etapa === 'Vinculación') {
            this.currentStep.set('VINCULACION');
            this.stepStatuses.set({
              vinculacion: vincActiva ? 'en_curso' : 'pendiente',
              practica1: 'bloqueado',
              practica2: 'bloqueado'
            });
            this.studentHours.set({
              completed: vincActiva?.horasCompletadas || 0,
              required: vincActiva?.horasRequeridas || 160
            });
            this.assignedTutorName.set(vincActiva?.tutor ? `${vincActiva.tutor.nombre} ${vincActiva.tutor.apellido}` : 'Sin asignar');
            this.assignedCompanyName.set(vincActiva?.fundacion?.nombre || 'Sin asignar');
          } else {
            this.currentStep.set(etapa === 'Práctica I' ? 'PRACTICA_1' : 'PRACTICA_2');
            this.stepStatuses.set({
              vinculacion: 'completado',
              practica1: pracsCompletadas >= 1 ? 'completado' : (pracActiva ? 'en_curso' : 'pendiente'),
              practica2: pracsCompletadas >= 2 ? 'completado' : (pracsCompletadas === 1 && pracActiva ? 'en_curso' : (pracsCompletadas >= 1 ? 'pendiente' : 'bloqueado'))
            });
            this.studentHours.set({
              completed: pracActiva?.horasCompletadas || 0,
              required: pracActiva?.horasRequeridas || 120
            });
            this.assignedTutorName.set(pracActiva?.tutor ? `${pracActiva.tutor.nombre} ${pracActiva.tutor.apellido}` : 'Sin asignar');
            this.assignedCompanyName.set(pracActiva?.empresa?.nombre || 'Sin asignar');
          }

          // Notas reales por parcial de la practica activa
          const limites = [...res.fechasLimite]
            .sort((a, b) => String(b.periodoAcademico).localeCompare(String(a.periodoAcademico)));
          const limiteDe = (p: number) => limites.find(l => l.parcial === p)?.fechaLimite || '—';
          // Evaluaciones del expediente activo: practica o vinculacion
          const evalDe = (p: number) => {
            if (pracActiva) return res.evaluaciones.find(e => e.practica?.id === pracActiva.id && e.parcial === p);
            if (vincActiva) return res.evaluaciones.find(e => e.vinculacion?.id === vincActiva.id && e.parcial === p);
            return undefined;
          };
          this.parcialesGrades.set([1, 2, 3].map(p => {
            const ev = evalDe(p);
            return {
              parcial: p,
              tutor: ev?.notaTutor || 0,
              coordinador: ev?.notaCoord || 0,
              final: ev?.notaFinal || 0,
              limitDate: limiteDe(p),
              surveyCompleted: !!ev?.encuestaCompletada
            };
          }));

          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
    } else {
      this.loading.set(false);
    }
  }

  // Checklist documental real (los tres documentos de pre-postulacion)
  documentosChecklist(): { label: string; estado: string }[] {
    const tipos = [
      { tipo: 'cv', label: 'Hoja de Vida (CV)' },
      { tipo: 'carta', label: 'Carta de Solicitud' },
      { tipo: 'cedula', label: 'Copia de Cédula' }
    ];
    return tipos.map(item => ({
      label: item.label,
      estado: this.documentosEstado().find(d => d.tipoDocumento === item.tipo)?.estado || 'pendiente'
    }));
  }

  estadoDocTexto(estado: string): string {
    const textos: Record<string, string> = {
      pendiente: 'Pendiente', cargado: 'En revisión', en_revision: 'En revisión',
      aprobado: 'Aprobado', rechazado: 'Rechazado', requiere_correccion: 'Corregir'
    };
    return textos[estado] || estado;
  }

  estadoDocClase(estado: string): string {
    if (estado === 'aprobado') return 'bg-green-50 border-green-200 text-green-800';
    if (estado === 'rechazado' || estado === 'requiere_correccion') return 'bg-red-50 border-red-200 text-red-700';
    if (estado === 'cargado' || estado === 'en_revision') return 'bg-amber-50 border-amber-200 text-amber-800';
    return 'bg-slate-50 border-slate-200 text-slate-500';
  }

  // Encuestas reales pendientes: parciales con ambas notas y sin encuesta
  encuestasPendientes() {
    return this.parcialesGrades().filter(g => g.tutor > 0 && g.coordinador > 0 && !g.surveyCompleted);
  }

  // Oculta tarjetas y accesos rápidos del proceso fuera del alcance del
  // coordinador (mismo criterio que ya filtra el menú lateral en dashboard.ts)
  mostrarPracticasGestion(): boolean {
    return this.userRole() === 'ADMIN' || this.authService.coordinacionTipo() !== 'VINCULACION';
  }

  mostrarVinculacionGestion(): boolean {
    return this.userRole() === 'ADMIN' || this.authService.coordinacionTipo() !== 'PRACTICAS';
  }

  descripcionAlcanceGestion(): string {
    if (this.userRole() === 'ADMIN') return 'Todos los procesos y carreras';
    const tipo = this.authService.coordinacionTipo();
    const area = tipo === 'PRACTICAS'
      ? 'Prácticas'
      : tipo === 'VINCULACION' ? 'Vinculación' : 'Prácticas y Vinculación';
    const carreras = this.authService.carrerasAsignadas();
    return carreras.length > 0 ? `${area} · ${carreras.join(', ')}` : `${area} · Sin carreras asignadas`;
  }

  periodoActual(): string {
    return this.expediente.periodoActual();
  }

  estadoPasoTexto(estado: string): string {
    switch (estado) {
      case 'completado': return 'Completado';
      case 'en_curso': return 'En curso';
      case 'bloqueado': return 'Bloqueado';
      default: return 'Pendiente';
    }
  }

  irA(ruta: string): void {
    this.router.navigate([ruta]);
  }

  irAMiExpediente(): void {
    this.router.navigate([this.procesoActivo() === 'VINCULACION' ? '/dashboard/vinculacion' : '/dashboard/practicas']);
  }
}
