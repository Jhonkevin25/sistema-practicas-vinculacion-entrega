import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfiguracionService } from '../../core/services/configuracion.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import { Carrera, CarreraService } from '../../core/services/carrera.service';
import {
  PeriodoAcademico,
  PeriodoAcademicoService
} from '../../core/services/periodo-academico.service';

// Formulario editable de una convocatoria (objeto plano para [(ngModel)])
interface FormularioConvocatoria {
  tipo: 'PRACTICAS' | 'VINCULACION';
  titulo: string;
  descripcion: string;
  convocatoriaInicio: string;
  convocatoriaFin: string;
  fechaLimiteDocumentos: string;
  fechaInicioPostulacion: string;
  periodoOrigen: string;
}

@Component({
  selector: 'app-convocatorias',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './convocatorias.html'
})
export class ConvocatoriasComponent {
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly expediente = inject(ExpedienteService);
  readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly carreraService = inject(CarreraService);
  private readonly periodoService = inject(PeriodoAcademicoService);

  // Catálogo de carreras (fase 27): alimenta estudiantes, convenios y alcances
  carreras = signal<Carrera[]>([]);
  nuevaCarrera = '';
  guardandoCarrera = signal(false);

  periodos = signal<PeriodoAcademico[]>([]);
  guardandoPeriodo = signal(false);
  editandoPeriodo = false;
  currentPeriodo: PeriodoAcademico = this.emptyPeriodo();

  loading = signal(true);
  guardandoTipo = signal<'PRACTICAS' | 'VINCULACION' | null>(null);
  userRole = signal('');

  readonly hoy = new Date().toISOString().split('T')[0];

  formularios: FormularioConvocatoria[] = [
    {
      tipo: 'PRACTICAS',
      titulo: 'Prácticas preprofesionales',
      descripcion: 'Rige cuándo se habilitan las vacantes y la postulación meritocrática en la sección Prácticas.',
      convocatoriaInicio: '', convocatoriaFin: '', fechaLimiteDocumentos: '', fechaInicioPostulacion: '',
      periodoOrigen: ''
    },
    {
      tipo: 'VINCULACION',
      titulo: 'Vinculación con la comunidad',
      descripcion: 'Rige cuándo el estudiante ve los proyectos disponibles y puede postularse en la sección Vinculación.',
      convocatoriaInicio: '', convocatoriaFin: '', fechaLimiteDocumentos: '', fechaInicioPostulacion: '',
      periodoOrigen: ''
    }
  ];

  constructor() {
    const user = this.authService.currentUser();
    if (user) this.userRole.set(user.rol);
    this.loadData();
    this.loadCarreras();
    this.loadPeriodos();
  }

  periodoActual(): string {
    return this.expediente.periodoActual();
  }

  loadPeriodos(): void {
    this.periodoService.getAll().subscribe({
      next: periodos => this.periodos.set(periodos),
      error: () => this.periodos.set([])
    });
  }

  nuevoPeriodo(): void {
    this.currentPeriodo = this.emptyPeriodo();
    this.editandoPeriodo = false;
  }

  editarPeriodo(periodo: PeriodoAcademico): void {
    this.currentPeriodo = { ...periodo };
    this.editandoPeriodo = true;
  }

  periodoActivoEnEdicion(): boolean {
    if (!this.editandoPeriodo || !this.currentPeriodo.id) return false;
    return this.periodos().some(periodo =>
      periodo.id === this.currentPeriodo.id && periodo.estado === 'ACTIVO');
  }

  guardarPeriodo(): void {
    const periodo = this.currentPeriodo;
    if (!periodo.codigo || !periodo.fechaInicio || !periodo.fechaFin) {
      this.toastService.warning('Completa el código y las fechas del periodo.');
      return;
    }
    if (!/^\d{4}-[12]$/.test(periodo.codigo.trim())) {
      this.toastService.warning('El código debe tener el formato AAAA-1 o AAAA-2.');
      return;
    }
    if (periodo.fechaInicio > periodo.fechaFin) {
      this.toastService.warning('La fecha de inicio no puede ser posterior a la fecha de fin.');
      return;
    }
    this.guardandoPeriodo.set(true);
    const payload: PeriodoAcademico = {
      ...periodo,
      codigo: periodo.codigo.trim(),
      estado: this.periodoActivoEnEdicion() ? 'ACTIVO' : periodo.estado
    };
    const request = this.editandoPeriodo && payload.id
      ? this.periodoService.update(payload.id, payload)
      : this.periodoService.create(payload);
    request.subscribe({
      next: () => {
        this.guardandoPeriodo.set(false);
        this.nuevoPeriodo();
        this.loadPeriodos();
        this.periodoService.cargarActivo().subscribe(() => this.loadData());
        this.toastService.success('Periodo académico guardado correctamente.');
      },
      error: err => {
        this.guardandoPeriodo.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo guardar el periodo académico.');
      }
    });
  }

  async eliminarPeriodo(periodo: PeriodoAcademico): Promise<void> {
    if (!periodo.id || periodo.estado !== 'PLANIFICADO'
        || !(await this.confirmService.confirm(`¿Eliminar el periodo planificado ${periodo.codigo}?`))) return;
    this.periodoService.delete(periodo.id).subscribe({
      next: () => {
        this.loadPeriodos();
        this.nuevoPeriodo();
        this.toastService.success('Periodo académico eliminado.');
      },
      error: err => this.toastService.error(
        err?.error?.error || 'No se pudo eliminar el periodo académico.')
    });
  }

  // Fase 42: cierre formal. El backend valida que no existan expedientes
  // activos, expira las postulaciones pendientes y bloquea el periodo.
  cerrandoPeriodoId = signal<number | null>(null);

  async cerrarPeriodo(periodo: PeriodoAcademico): Promise<void> {
    if (!periodo.id || this.cerrandoPeriodoId() !== null) return;
    const mensaje = `¿Cerrar definitivamente el periodo ${periodo.codigo}?\n\n`
      + 'Las postulaciones pendientes quedarán EXPIRADAS, no se admitirán nuevas '
      + 'postulaciones ni asignaciones y los cupos sobrantes NO se trasladan al '
      + 'siguiente periodo. Un periodo cerrado no puede reabrirse.';
    if (!(await this.confirmService.confirm(mensaje))) return;
    this.cerrandoPeriodoId.set(periodo.id);
    this.periodoService.cerrar(periodo.id).subscribe({
      next: () => {
        this.cerrandoPeriodoId.set(null);
        this.nuevoPeriodo();
        this.loadPeriodos();
        this.periodoService.cargarActivo().subscribe(() => this.loadData());
        this.toastService.success(`Periodo ${periodo.codigo} cerrado.`);
      },
      error: err => {
        this.cerrandoPeriodoId.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo cerrar el periodo académico.');
      }
    });
  }

  loadCarreras(): void {
    this.carreraService.getAll().subscribe({
      next: carreras => this.carreras.set(carreras),
      error: () => this.carreras.set([])
    });
  }

  agregarCarrera(): void {
    const nombre = this.nuevaCarrera.trim();
    if (!nombre) {
      this.toastService.warning('Escribe el nombre de la carrera.');
      return;
    }
    this.guardandoCarrera.set(true);
    this.carreraService.create({ nombre }).subscribe({
      next: () => {
        this.guardandoCarrera.set(false);
        this.nuevaCarrera = '';
        this.loadCarreras();
        this.toastService.success('Carrera agregada al catálogo.');
      },
      error: () => this.guardandoCarrera.set(false)
    });
  }

  toggleCarreraActiva(carrera: Carrera): void {
    if (!carrera.id) return;
    this.carreraService.update(carrera.id, { nombre: carrera.nombre, activo: carrera.activo === false }).subscribe({
      next: () => this.loadCarreras()
    });
  }

  async eliminarCarrera(carrera: Carrera): Promise<void> {
    if (!carrera.id) return;
    if (!(await this.confirmService.confirm(`¿Eliminar la carrera "${carrera.nombre}" del catálogo?`))) return;
    this.carreraService.delete(carrera.id).subscribe({
      next: () => {
        this.loadCarreras();
        this.toastService.success('Carrera eliminada del catálogo.');
      }
    });
  }

  loadData(): void {
    this.loading.set(true);
    this.configuracionService.getAll().subscribe({
      next: configs => {
        for (const form of this.formularios) {
          // Preferir la config del periodo actual; si no existe, la vigente más reciente
          const delPeriodo = configs.find(c =>
            c.tipo === form.tipo && c.periodoAcademico === this.periodoActual());
          const config = delPeriodo || this.expediente.configVigente(configs, form.tipo);
          if (config) {
            form.convocatoriaInicio = config.convocatoriaInicio;
            form.convocatoriaFin = config.convocatoriaFin;
            form.fechaLimiteDocumentos = config.fechaLimiteDocumentos;
            form.fechaInicioPostulacion = config.fechaInicioPostulacion;
            form.periodoOrigen = config.periodoAcademico;
          }
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  esAdmin(): boolean {
    return this.userRole() === 'ADMIN';
  }

  // COORDINADOR solo ve el proceso que coordina; ADMIN ve ambos
  formulariosVisibles(): FormularioConvocatoria[] {
    if (this.userRole() !== 'COORDINADOR') return this.formularios;
    const tipo = this.authService.coordinacionTipo();
    if (tipo === 'AMBOS') return this.formularios;
    return this.formularios.filter(f => f.tipo === tipo);
  }

  estadoConvocatoria(form: FormularioConvocatoria): { etiqueta: string; clase: string } {
    if (!form.fechaInicioPostulacion || !form.convocatoriaFin) {
      return { etiqueta: 'Sin configurar', clase: 'bg-slate-100 text-slate-500 border-slate-200' };
    }
    if (this.hoy > form.convocatoriaFin) {
      return { etiqueta: 'Convocatoria cerrada', clase: 'bg-red-50 text-red-600 border-red-200' };
    }
    if (this.hoy >= form.fechaInicioPostulacion) {
      return { etiqueta: 'Postulación abierta', clase: 'bg-green-50 text-green-700 border-green-200' };
    }
    return { etiqueta: 'Postulación abre el ' + form.fechaInicioPostulacion, clase: 'bg-amber-50 text-amber-700 border-amber-200' };
  }

  // Espejo de la validación del backend para avisar antes de enviar
  private validarOrden(form: FormularioConvocatoria): string | null {
    if (!form.convocatoriaInicio || !form.fechaLimiteDocumentos || !form.fechaInicioPostulacion || !form.convocatoriaFin) {
      return 'Debes configurar inicio de convocatoria, límite de documentos, inicio de postulación y cierre.';
    }
    if (form.fechaLimiteDocumentos < form.convocatoriaInicio) {
      return 'La fecha límite de documentos no puede ser anterior al inicio de convocatoria.';
    }
    if (form.fechaInicioPostulacion < form.fechaLimiteDocumentos) {
      return 'El inicio de postulación no puede ser anterior al límite de documentos.';
    }
    if (form.convocatoriaFin < form.fechaInicioPostulacion) {
      return 'El cierre de convocatoria no puede ser anterior al inicio de postulación.';
    }
    return null;
  }

  guardar(form: FormularioConvocatoria): void {
    const error = this.validarOrden(form);
    if (error) {
      this.toastService.warning(error);
      return;
    }
    this.guardandoTipo.set(form.tipo);
    this.configuracionService.create({
      periodoAcademico: this.periodoActual(),
      tipo: form.tipo,
      convocatoriaInicio: form.convocatoriaInicio,
      convocatoriaFin: form.convocatoriaFin,
      fechaLimiteDocumentos: form.fechaLimiteDocumentos,
      fechaInicioPostulacion: form.fechaInicioPostulacion
    }).subscribe({
      next: () => {
        this.guardandoTipo.set(null);
        form.periodoOrigen = this.periodoActual();
        this.toastService.success(
          `Fechas de ${form.titulo} guardadas para el periodo ${this.periodoActual()}.`);
      },
      // El errorInterceptor muestra el motivo exacto devuelto por el backend
      error: () => this.guardandoTipo.set(null)
    });
  }

  private emptyPeriodo(): PeriodoAcademico {
    return {
      codigo: '',
      fechaInicio: '',
      fechaFin: '',
      estado: 'PLANIFICADO'
    };
  }
}
