import { Component, HostListener, OnInit, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import {
  Proyecto,
  ProyectoCarrera,
  ProyectoService
} from '../../../core/services/proyecto.service';
import { Fundacion, FundacionService } from '../../../core/services/fundacion.service';
import { Carrera, CarreraService } from '../../../core/services/carrera.service';
import {
  OfertaCuposFundacion,
  OfertaCuposFundacionService
} from '../../../core/services/oferta-cupos-fundacion.service';
import {
  PeriodoAcademico,
  PeriodoAcademicoService
} from '../../../core/services/periodo-academico.service';
import { ExpedienteService } from '../../../core/services/expediente.service';
import { ToastService } from '../../../core/services/toast.service';

// Capacidad reservable de una oferta: los cupos libres calculados por el
// backend más la reserva actual del proyecto cuando se está editando.
export function maximoReservaProyecto(
  oferta: Pick<OfertaCuposFundacion, 'activo' | 'cuposDisponibles'> | undefined,
  reservaActual = 0
): number {
  if (!oferta?.activo) return 0;
  return Math.max(0, Number(oferta.cuposDisponibles || 0))
    + Math.max(0, Number(reservaActual || 0));
}

@Component({
  selector: 'app-proyecto-form-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './proyecto-form-modal.html'
})
export class ProyectoFormModalComponent implements OnInit {
  private readonly proyectoService = inject(ProyectoService);
  private readonly fundacionService = inject(FundacionService);
  private readonly carreraService = inject(CarreraService);
  private readonly ofertaCuposService = inject(OfertaCuposFundacionService);
  private readonly periodoService = inject(PeriodoAcademicoService);
  private readonly expedienteService = inject(ExpedienteService);
  private readonly toastService = inject(ToastService);

  // Proyecto original a editar; null para crear uno nuevo
  proyecto = input<Proyecto | null>(null);
  // Fase 42: duplicar copia descripción y condiciones pero NUNCA los cupos
  duplicar = input<boolean>(false);
  guardado = output<Proyecto>();
  cerrar = output<void>();

  cargando = signal(true);
  guardando = signal(false);
  fundaciones = signal<Fundacion[]>([]);
  ofertasCupos = signal<OfertaCuposFundacion[]>([]);
  periodos = signal<PeriodoAcademico[]>([]);
  carrerasCatalogo = signal<Carrera[]>([]);

  currentProyecto: Proyecto = this.emptyProyecto();
  selectedFundacionId: number | null = null;

  esEdicion(): boolean {
    return !!this.proyecto()?.id && !this.duplicar();
  }

  ngOnInit(): void {
    const original = this.proyecto();
    if (original && this.duplicar()) {
      // Copia como proyecto NUEVO: sin id, cupos reiniciados, periodo actual
      this.currentProyecto = {
        ...this.emptyProyecto(),
        nombre: `${original.nombre} (copia)`,
        descripcion: original.descripcion,
        horasRequeridas: original.horasRequeridas,
        ciudad: original.ciudad,
        modalidad: original.modalidad === 'NO_ESPECIFICADA' ? 'PRESENCIAL' : original.modalidad,
        carreras: (original.carreras || []).map(item => ({
          carrera: { ...item.carrera },
          cuposTotales: 1,
          cuposDisponibles: null
        }))
      };
      this.selectedFundacionId = original.fundacion.id || null;
    } else if (original) {
      this.currentProyecto = {
        ...original,
        fundacion: { ...original.fundacion },
        cuposTotales: original.cuposTotales ?? original.cuposDisponibles ?? 0,
        carreras: (original.carreras || []).map(item => ({
          ...item,
          carrera: { ...item.carrera }
        }))
      };
      this.selectedFundacionId = original.fundacion.id || null;
    }
    forkJoin({
      fundaciones: this.fundacionService.getAll(),
      ofertas: this.ofertaCuposService.getAll(),
      periodos: this.periodoService.getAll(),
      carreras: this.carreraService.getAll()
    }).subscribe({
      next: ({ fundaciones, ofertas, periodos, carreras }) => {
        this.fundaciones.set(fundaciones);
        this.ofertasCupos.set(ofertas);
        this.periodos.set(periodos);
        this.carrerasCatalogo.set(carreras);
        this.cargando.set(false);
      },
      error: err => {
        this.cargando.set(false);
        this.toastService.error(err?.error?.error || 'No se pudieron cargar los catálogos del proyecto.');
      }
    });
  }

  @HostListener('document:keydown.escape')
  cerrarConEscape(): void {
    this.cerrar.emit();
  }

  periodoActual(): string {
    return this.expedienteService.periodoActual();
  }

  periodosConfigurables(): PeriodoAcademico[] {
    return this.periodos().filter(periodo => periodo.estado !== 'CERRADO');
  }

  ofertaSeleccionada(): OfertaCuposFundacion | undefined {
    if (this.selectedFundacionId == null) return undefined;
    const fundacionId = Number(this.selectedFundacionId);
    return this.ofertasCupos().find(oferta =>
      oferta.fundacion?.id === fundacionId
      && oferta.periodoAcademico === this.currentProyecto.periodo);
  }

  maximoCuposProyecto(): number {
    const oferta = this.ofertaSeleccionada();
    if (!oferta || oferta.distribucion !== 'GENERAL') return 0;
    const original = this.proyecto();
    const reservaActual = Number(original?.cuposTotales ?? original?.cuposDisponibles ?? 0);
    return maximoReservaProyecto(oferta, reservaActual);
  }

  cuposSolicitadosProyecto(): number {
    const oferta = this.ofertaSeleccionada();
    if (oferta?.distribucion === 'POR_CARRERA') {
      return this.currentProyecto.carreras
        .reduce((total, item) => total + Number(item.cuposTotales || 0), 0);
    }
    return Number(this.currentProyecto.cuposTotales || 0);
  }

  carrerasActivas(): Carrera[] {
    const activas = this.carrerasCatalogo().filter(carrera => carrera.activo !== false);
    const oferta = this.ofertaSeleccionada();
    if (!oferta || oferta.distribucion === 'GENERAL') return activas;
    return activas.filter(carrera => oferta.carreras.some(item => item.carrera.id === carrera.id));
  }

  participacionProyecto(carreraId?: number): ProyectoCarrera | undefined {
    if (carreraId == null) return undefined;
    return this.currentProyecto.carreras.find(item => item.carrera.id === carreraId);
  }

  toggleCarreraProyecto(carrera: Carrera): void {
    const existente = this.participacionProyecto(carrera.id);
    this.currentProyecto.carreras = existente
      ? this.currentProyecto.carreras.filter(item => item.carrera.id !== carrera.id)
      : [...this.currentProyecto.carreras, {
          carrera: { ...carrera },
          cuposTotales: this.ofertaSeleccionada()?.distribucion === 'POR_CARRERA' ? 1 : null,
          cuposDisponibles: null
        }];
  }

  actualizarCuposProyectoCarrera(carrera: Carrera, cupos: number | string): void {
    const participacion = this.participacionProyecto(carrera.id);
    if (participacion) participacion.cuposTotales = Number(cupos);
  }

  maximoCuposCarrera(carrera: Carrera): number {
    const oferta = this.ofertaSeleccionada();
    const detalle = oferta?.carreras.find(item => item.carrera.id === carrera.id);
    const reservaActual = this.proyecto()?.carreras
      ?.find(item => item.carrera.id === carrera.id)?.cuposTotales || 0;
    return maximoReservaProyecto(
      detalle ? { activo: Boolean(oferta?.activo), cuposDisponibles: detalle.cuposDisponibles } : undefined,
      Number(reservaActual));
  }

  cambiarFundacionOPeriodoProyecto(): void {
    if (!this.esEdicion()) {
      this.currentProyecto.carreras = [];
      this.currentProyecto.cuposTotales = 1;
    }
  }

  saveProyecto(): void {
    if (!this.currentProyecto.nombre || !this.selectedFundacionId
        || !this.currentProyecto.periodo || !this.currentProyecto.descripcion
        || !this.currentProyecto.ciudad || !this.currentProyecto.modalidad
        || Number(this.currentProyecto.horasRequeridas) <= 0) {
      this.toastService.warning('Completa los datos obligatorios del proyecto.');
      return;
    }
    const fund = this.fundaciones().find(f => f.id === Number(this.selectedFundacionId));
    if (!fund) {
      this.toastService.warning('Selecciona una fundación válida.');
      return;
    }
    if (this.currentProyecto.carreras.length === 0) {
      this.toastService.warning('Selecciona al menos una carrera participante.');
      return;
    }
    const oferta = this.ofertaSeleccionada();
    if (!oferta || !oferta.activo) {
      this.toastService.warning('La fundación no tiene una oferta de cupos activa para el periodo seleccionado.');
      return;
    }
    let cuposTotales = Number(this.currentProyecto.cuposTotales || 0);
    if (oferta.distribucion === 'GENERAL') {
      if (cuposTotales <= 0 || cuposTotales > this.maximoCuposProyecto()) {
        this.toastService.warning(
          `El proyecto puede reservar entre 1 y ${this.maximoCuposProyecto()} cupo(s).`);
        return;
      }
      this.currentProyecto.carreras.forEach(item => {
        item.cuposTotales = null;
        item.cuposDisponibles = null;
      });
    } else {
      const invalida = this.currentProyecto.carreras.find(item =>
        Number(item.cuposTotales || 0) <= 0
        || Number(item.cuposTotales || 0) > this.maximoCuposCarrera(item.carrera));
      if (invalida) {
        this.toastService.warning(`Revisa los cupos asignados a ${invalida.carrera.nombre}.`);
        return;
      }
      cuposTotales = this.currentProyecto.carreras
        .reduce((total, item) => total + Number(item.cuposTotales), 0);
    }

    const payload: Proyecto = {
      ...this.currentProyecto,
      fundacion: fund,
      cuposTotales,
      horasRequeridas: Number(this.currentProyecto.horasRequeridas),
      carreras: this.currentProyecto.carreras.map(item => ({
        ...item,
        carrera: { ...item.carrera }
      }))
    };
    this.guardando.set(true);
    const request = this.esEdicion() && payload.id
      ? this.proyectoService.update(payload.id, payload)
      : this.proyectoService.create(payload);
    request.subscribe({
      next: creado => {
        this.guardando.set(false);
        this.toastService.success(this.esEdicion()
          ? 'Proyecto actualizado correctamente.'
          : 'Proyecto registrado correctamente.');
        this.guardado.emit(creado);
      },
      error: err => {
        this.guardando.set(false);
        this.toastService.error(err?.error?.error
          || (this.esEdicion() ? 'No se pudo actualizar el proyecto.' : 'No se pudo registrar el proyecto.'));
      }
    });
  }

  private emptyProyecto(): Proyecto {
    return {
      fundacion: {} as Fundacion,
      nombre: '',
      descripcion: '',
      cuposTotales: 1,
      cuposDisponibles: 0,
      estado: true,
      periodo: this.expedienteService.periodoActual(),
      horasRequeridas: 160,
      ciudad: 'Quito',
      modalidad: 'PRESENCIAL',
      carreras: []
    };
  }
}
