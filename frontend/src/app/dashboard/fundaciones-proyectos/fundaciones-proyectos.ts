import { Component, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FundacionService, Fundacion } from '../../core/services/fundacion.service';
import { ProyectoService, Proyecto, modalidadProyectoTexto } from '../../core/services/proyecto.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { Convenio, ConvenioService } from '../../core/services/convenio.service';
import { Carrera, CarreraService } from '../../core/services/carrera.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import {
  OfertaCuposFundacion,
  OfertaCuposFundacionCarrera,
  OfertaCuposFundacionService
} from '../../core/services/oferta-cupos-fundacion.service';
import { ProyectoFormModalComponent } from '../shared/proyecto-form-modal/proyecto-form-modal';
import { TAMANO_PAGINA_DEFECTO } from '../../core/services/paginacion';
import { PaginadorComponent } from '../shared/paginador/paginador';
import { forkJoin, of } from 'rxjs';

@Component({
  selector: 'app-fundaciones-proyectos',
  standalone: true,
  imports: [CommonModule, FormsModule, ProyectoFormModalComponent, PaginadorComponent],
  templateUrl: './fundaciones-proyectos.html',
  styleUrl: './fundaciones-proyectos.css'
})
export class FundacionesProyectosComponent implements OnDestroy {
  private readonly fundacionService = inject(FundacionService);
  private readonly proyectoService = inject(ProyectoService);
  readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly convenioService = inject(ConvenioService);
  private readonly carreraService = inject(CarreraService);
  private readonly expedienteService = inject(ExpedienteService);
  private readonly ofertaCuposService = inject(OfertaCuposFundacionService);

  readonly hoy = new Date().toISOString().split('T')[0];

  fundaciones = signal<Fundacion[]>([]);
  proyectos = signal<Proyecto[]>([]);
  convenios = signal<Convenio[]>([]);
  carrerasCatalogo = signal<Carrera[]>([]);
  ofertasCupos = signal<OfertaCuposFundacion[]>([]);
  loading = signal(true);
  isRefreshing = signal(false);
  guardandoFundacion = signal(false);
  guardandoConvenio = signal(false);
  guardandoOferta = signal(false);

  // Fase 43: paginación y filtros del listado de fundaciones (backend)
  readonly tamanoPagina = TAMANO_PAGINA_DEFECTO;
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);
  filtroTexto = '';
  filtroActiva = 'TODOS';
  private timerBusqueda: ReturnType<typeof setTimeout> | null = null;

  // Fase de optimización: paginación del listado de proyectos (antes traía
  // todos los periodos sin paginar)
  paginaProyectos = signal(0);
  totalPaginasProyectos = signal(0);
  totalElementosProyectos = signal(0);

  ngOnDestroy(): void {
    if (this.timerBusqueda) clearTimeout(this.timerBusqueda);
  }

  showFundacionModal = signal(false);
  showProyectoModal = signal(false);
  showConvenioModal = signal(false);
  showOfertaModal = signal(false);

  // Models
  currentFundacion: Fundacion = this.emptyFundacion();
  proyectoEnEdicion: Proyecto | null = null;
  isEdit = false;
  isEditConvenio = false;
  selectedFundacionConvenio = signal<Fundacion | null>(null);
  currentConvenio: Convenio = this.emptyConvenio();
  carrerasSeleccionadas: string[] = [];
  selectedFundacionOferta = signal<Fundacion | null>(null);
  currentOferta: OfertaCuposFundacion = this.emptyOferta();

  constructor() {
    this.loadData(true);
  }

  // Opciones del selector: catalogo activo + valores heredados ya guardados
  opcionesCarrerasConvenio(): string[] {
    const activas = this.carrerasCatalogo().filter(c => c.activo !== false).map(c => c.nombre);
    const heredadas = this.carrerasSeleccionadas.filter(nombre => !activas.includes(nombre));
    return [...activas, ...heredadas];
  }

  carreraSeleccionada(nombre: string): boolean {
    return this.carrerasSeleccionadas.includes(nombre);
  }

  toggleCarreraConvenio(nombre: string): void {
    this.carrerasSeleccionadas = this.carreraSeleccionada(nombre)
      ? this.carrerasSeleccionadas.filter(c => c !== nombre)
      : [...this.carrerasSeleccionadas, nombre];
  }

  cambiarPagina(pagina: number): void {
    this.loadData(false, pagina);
  }

  cambiarPaginaProyectos(paginaProyectos: number): void {
    this.loadData(false, this.pagina(), paginaProyectos);
  }

  // Búsqueda con pequeña espera para no disparar una petición por tecla
  buscarConRetardo(): void {
    if (this.timerBusqueda) clearTimeout(this.timerBusqueda);
    this.timerBusqueda = setTimeout(() => this.aplicarFiltros(), 350);
  }

  aplicarFiltros(): void {
    this.loadData(false, 0);
  }

  loadData(isInitial = false, pagina = this.pagina(), paginaProyectos = this.paginaProyectos()): void {
    if (isInitial) this.loading.set(true); else this.isRefreshing.set(true);
    const rol = this.authService.getRole();
    forkJoin({
      fundaciones: this.fundacionService.getPaginado(pagina, this.tamanoPagina, {
        texto: this.filtroTexto,
        activa: this.filtroActiva
      }),
      proyectos: this.proyectoService.getPaginado(paginaProyectos, this.tamanoPagina),
      convenios: rol === 'ADMIN' || rol === 'COORDINADOR'
        ? this.convenioService.getAll()
        : of([] as Convenio[]),
      ofertas: rol === 'ADMIN' || rol === 'COORDINADOR'
        ? this.ofertaCuposService.getAll()
        : of([] as OfertaCuposFundacion[]),
      carreras: rol === 'ADMIN' || rol === 'COORDINADOR'
        ? this.carreraService.getAll()
        : of([] as Carrera[])
    }).subscribe({
      next: ({ fundaciones, proyectos, convenios, ofertas, carreras }) => {
        this.fundaciones.set(fundaciones.contenido);
        this.pagina.set(fundaciones.pagina);
        this.totalPaginas.set(fundaciones.totalPaginas);
        this.totalElementos.set(fundaciones.totalElementos);
        this.proyectos.set(proyectos.contenido);
        this.paginaProyectos.set(proyectos.pagina);
        this.totalPaginasProyectos.set(proyectos.totalPaginas);
        this.totalElementosProyectos.set(proyectos.totalElementos);
        this.convenios.set(convenios);
        this.ofertasCupos.set(ofertas);
        this.carrerasCatalogo.set(carreras);
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      },
      error: () => {
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      }
    });
  }

  // Fundaciones Operations
  openCreateFundacionModal(): void {
    this.currentFundacion = this.emptyFundacion();
    this.isEdit = false;
    this.showFundacionModal.set(true);
  }

  openEditFundacionModal(fundacion: Fundacion): void {
    this.currentFundacion = { ...fundacion };
    this.isEdit = true;
    this.showFundacionModal.set(true);
  }

  saveFundacion(): void {
    if (this.guardandoFundacion()) return;
    if (!this.currentFundacion.ruc || !this.currentFundacion.nombre) {
      this.toastService.warning('Completa el RUC y el nombre de la fundación.');
      return;
    }
    const editando = this.isEdit && !!this.currentFundacion.id;
    this.guardandoFundacion.set(true);
    const request = editando
      ? this.fundacionService.update(this.currentFundacion.id!, this.currentFundacion)
      : this.fundacionService.create(this.currentFundacion);
    request.subscribe({
      next: () => {
        this.guardandoFundacion.set(false);
        // La fundación puede afectar la oferta de cupos visible (p. ej. su
        // estado activa/inactiva); refrescamos el caché al guardar, no en cada carga.
        this.ofertaCuposService.clearCache();
        this.loadData();
        this.showFundacionModal.set(false);
        this.toastService.success(editando
          ? 'Fundación actualizada correctamente.'
          : 'Fundación registrada correctamente.');
      },
      error: (err) => {
        this.guardandoFundacion.set(false);
        this.toastService.error(err?.error?.error
          || (editando ? 'No se pudo actualizar la fundación.' : 'No se pudo registrar la fundación.'));
      }
    });
  }

  async deleteFundacion(id: number): Promise<void> {
    if (await this.confirmService.confirm('¿Deseas eliminar esta fundación?')) {
      this.fundacionService.delete(id).subscribe({
        next: () => {
          this.ofertaCuposService.clearCache();
          this.loadData();
          this.toastService.success('Fundación eliminada.');
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo eliminar la fundación.')
      });
    }
  }

  // Proyectos Operations (formulario compartido en dashboard/shared/proyecto-form-modal)
  openCreateProyectoModal(): void {
    this.proyectoEnEdicion = null;
    this.showProyectoModal.set(true);
  }

  openEditProyectoModal(proyecto: Proyecto): void {
    this.proyectoEnEdicion = proyecto;
    this.showProyectoModal.set(true);
  }

  proyectoGuardado(): void {
    this.showProyectoModal.set(false);
    this.proyectoEnEdicion = null;
    // Un proyecto guardado cambia los cupos comprometidos de la oferta de su
    // fundación; refrescamos el caché al guardar, no en cada carga/paginación.
    this.ofertaCuposService.clearCache();
    this.loadData();
  }

  modalidadTexto(modalidad?: string): string {
    return modalidadProyectoTexto(modalidad);
  }

  periodoActual(): string {
    return this.expedienteService.periodoActual();
  }

  ofertaDeFundacion(
    fundacionId?: number,
    periodo = this.periodoActual()
  ): OfertaCuposFundacion | undefined {
    if (fundacionId == null) return undefined;
    return this.ofertasCupos().find(oferta =>
      oferta.fundacion?.id === fundacionId && oferta.periodoAcademico === periodo);
  }

  resumenOferta(fundacionId?: number): string {
    const oferta = this.ofertaDeFundacion(fundacionId);
    if (!oferta) return 'Sin oferta configurada';
    if (!oferta.activo) return 'Oferta inactiva';
    return `${oferta.cuposDisponibles || 0} libres de ${oferta.cuposTotales}`;
  }

  claseOferta(fundacionId?: number): string {
    const oferta = this.ofertaDeFundacion(fundacionId);
    if (!oferta || !oferta.activo) return 'text-slate-500';
    return (oferta.cuposDisponibles || 0) > 0 ? 'text-emerald-700' : 'text-red-700';
  }

  claseOfertaBoton(fundacionId?: number): string {
    const oferta = this.ofertaDeFundacion(fundacionId);
    if (!oferta || !oferta.activo) {
      return 'bg-slate-50 border-slate-200 text-slate-500 hover:bg-slate-100 hover:border-slate-300';
    }
    return (oferta.cuposDisponibles || 0) > 0
      ? 'bg-emerald-50 border-emerald-200 text-emerald-800 hover:bg-emerald-100 hover:border-emerald-300'
      : 'bg-red-50 border-red-200 text-red-800 hover:bg-red-100 hover:border-red-300';
  }

  toggleActiveFundacion(fundacion: Fundacion): void {
    if (!fundacion.id || !this.isAdmin()) return;
    const actualizada = { ...fundacion, activa: fundacion.activa === false };
    this.fundacionService.update(fundacion.id, actualizada).subscribe({
      next: () => {
        this.ofertaCuposService.clearCache();
        this.loadData();
        this.toastService.success(actualizada.activa
          ? 'Fundación habilitada.'
          : 'Fundación deshabilitada.');
      },
      error: err => this.toastService.error(
        err?.error?.error || 'No se pudo cambiar el estado de la fundación.')
    });
  }

  carrerasCatalogoActivas(): Carrera[] {
    return this.carrerasCatalogo().filter(carrera => carrera.activo !== false);
  }

  async deleteProyecto(id: number): Promise<void> {
    if (await this.confirmService.confirm('¿Deseas eliminar este proyecto?')) {
      this.proyectoService.delete(id).subscribe({
        next: () => {
          this.ofertaCuposService.clearCache();
          this.loadData();
          this.toastService.success('Proyecto eliminado.');
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo eliminar el proyecto.')
      });
    }
  }

  isAdmin(): boolean {
    return this.authService.getRole() === 'ADMIN';
  }

  openOferta(fundacion: Fundacion): void {
    this.selectedFundacionOferta.set(fundacion);
    const oferta = this.ofertaDeFundacion(fundacion.id);
    this.currentOferta = oferta
      ? {
          ...oferta,
          fundacion: { ...fundacion },
          carreras: oferta.carreras.map(item => ({
            ...item,
            carrera: { ...item.carrera }
          }))
        }
      : this.emptyOferta(fundacion);
    this.showOfertaModal.set(true);
  }

  closeOferta(): void {
    this.showOfertaModal.set(false);
    this.selectedFundacionOferta.set(null);
    this.currentOferta = this.emptyOferta();
  }

  asignacionCarreraOferta(carreraId?: number): OfertaCuposFundacionCarrera | undefined {
    if (carreraId == null) return undefined;
    return this.currentOferta.carreras.find(item => item.carrera.id === carreraId);
  }

  toggleCarreraOferta(carrera: Carrera): void {
    const existente = this.asignacionCarreraOferta(carrera.id);
    this.currentOferta.carreras = existente
      ? this.currentOferta.carreras.filter(item => item.carrera.id !== carrera.id)
      : [...this.currentOferta.carreras, { carrera: { ...carrera }, cupos: 1 }];
  }

  actualizarCuposOfertaCarrera(carrera: Carrera, cupos: number | string): void {
    const asignacion = this.asignacionCarreraOferta(carrera.id);
    if (asignacion) asignacion.cupos = Number(cupos);
  }

  cambiarDistribucionOferta(): void {
    if (this.currentOferta.distribucion === 'GENERAL') {
      this.currentOferta.carreras = [];
    }
  }

  saveOferta(): void {
    const fundacion = this.selectedFundacionOferta();
    if (!fundacion?.id) return;
    if (this.currentOferta.distribucion === 'GENERAL'
        && Number(this.currentOferta.cuposTotales) <= 0) {
      this.toastService.warning('La oferta general debe tener al menos un cupo.');
      return;
    }
    if (this.currentOferta.distribucion === 'POR_CARRERA') {
      if (this.currentOferta.carreras.length === 0) {
        this.toastService.warning('Selecciona al menos una carrera para distribuir los cupos.');
        return;
      }
      if (this.currentOferta.carreras.some(item => Number(item.cupos) <= 0)) {
        this.toastService.warning('Cada carrera seleccionada debe tener al menos un cupo.');
        return;
      }
      this.currentOferta.cuposTotales = this.currentOferta.carreras
        .reduce((total, item) => total + Number(item.cupos), 0);
    }

    const payload: OfertaCuposFundacion = {
      ...this.currentOferta,
      fundacion: { ...fundacion },
      periodoAcademico: this.periodoActual(),
      carreras: this.currentOferta.carreras.map(item => ({
        ...item,
        carrera: { ...item.carrera }
      }))
    };
    this.guardandoOferta.set(true);
    const request = payload.id
      ? this.ofertaCuposService.update(payload.id, payload)
      : this.ofertaCuposService.create(payload);
    request.subscribe({
      next: () => {
        this.guardandoOferta.set(false);
        this.loadData();
        this.closeOferta();
        this.toastService.success('Oferta de cupos guardada correctamente.');
      },
      error: err => {
        this.guardandoOferta.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo guardar la oferta de cupos.');
      }
    });
  }

  async deleteOferta(): Promise<void> {
    if (!this.currentOferta.id || !(await this.confirmService.confirm('¿Deseas eliminar esta oferta de cupos?'))) return;
    this.ofertaCuposService.delete(this.currentOferta.id).subscribe({
      next: () => {
        this.loadData();
        this.closeOferta();
        this.toastService.success('Oferta de cupos eliminada.');
      },
      error: err => this.toastService.error(
        err?.error?.error || 'No se pudo eliminar la oferta de cupos.')
    });
  }

  conveniosDeFundacion(fundacionId?: number): Convenio[] {
    if (fundacionId == null) return [];
    return this.convenios()
      .filter(convenio => convenio.fundacion?.id === fundacionId)
      .sort((a, b) => b.fechaFin.localeCompare(a.fechaFin));
  }

  estadoConvenio(fundacionId?: number): string {
    const convenios = this.conveniosDeFundacion(fundacionId);
    if (convenios.some(convenio => this.esConvenioVigente(convenio))) return 'Vigente';
    if (convenios.some(convenio => convenio.estado === 'SUSPENDIDO')) return 'Suspendido';
    if (convenios.length > 0) return 'Vencido';
    return 'Sin convenio';
  }

  claseEstadoConvenio(fundacionId?: number): string {
    const estado = this.estadoConvenio(fundacionId);
    if (estado === 'Vigente') return 'text-green-700 bg-green-50 border-green-200';
    if (estado === 'Suspendido') return 'text-amber-700 bg-amber-50 border-amber-200';
    if (estado === 'Vencido') return 'text-red-700 bg-red-50 border-red-200';
    return 'text-slate-500 bg-slate-100 border-slate-200';
  }

  claseConvenio(convenio: Convenio): string {
    if (this.esConvenioVigente(convenio)) return 'text-green-700 bg-green-50 border-green-200';
    if (convenio.estado === 'SUSPENDIDO') return 'text-amber-700 bg-amber-50 border-amber-200';
    return 'text-red-700 bg-red-50 border-red-200';
  }

  openConvenios(fundacion: Fundacion): void {
    this.selectedFundacionConvenio.set(fundacion);
    this.nuevoConvenio();
    this.showConvenioModal.set(true);
  }

  closeConvenios(): void {
    this.showConvenioModal.set(false);
    this.selectedFundacionConvenio.set(null);
  }

  nuevoConvenio(): void {
    this.currentConvenio = this.emptyConvenio();
    this.carrerasSeleccionadas = [];
    this.isEditConvenio = false;
  }

  editarConvenio(convenio: Convenio): void {
    this.currentConvenio = { ...convenio, carreras: [...(convenio.carreras || [])] };
    this.carrerasSeleccionadas = [...(convenio.carreras || [])];
    this.isEditConvenio = true;
  }

  saveConvenio(): void {
    if (this.guardandoConvenio()) return;
    const fundacion = this.selectedFundacionConvenio();
    if (!fundacion?.id || !this.currentConvenio.codigo || !this.currentConvenio.fechaInicio || !this.currentConvenio.fechaFin) {
      this.toastService.warning('Completa el código y las fechas del convenio.');
      return;
    }
    const payload: Convenio = {
      ...this.currentConvenio,
      empresa: null,
      fundacion: { ...fundacion },
      cuposPactados: 0,
      carreras: [...this.carrerasSeleccionadas]
    };
    this.guardandoConvenio.set(true);
    const request = this.isEditConvenio && payload.id
      ? this.convenioService.update(payload.id, payload)
      : this.convenioService.create(payload);
    request.subscribe({
      next: () => {
        this.guardandoConvenio.set(false);
        this.closeConvenios();
        this.loadData();
        this.toastService.success('Convenio guardado correctamente.');
      },
      error: (err) => {
        this.guardandoConvenio.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo guardar el convenio.');
      }
    });
  }

  async deleteConvenio(id: number): Promise<void> {
    if (!(await this.confirmService.confirm('¿Deseas eliminar este convenio?'))) return;
    this.convenioService.delete(id).subscribe({
      next: () => {
        this.closeConvenios();
        this.loadData();
        this.toastService.success('Convenio eliminado.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo eliminar el convenio.')
    });
  }

  private esConvenioVigente(convenio: Convenio): boolean {
    const hoy = new Date().toISOString().split('T')[0];
    return convenio.estado === 'VIGENTE'
      && convenio.fechaInicio <= hoy
      && convenio.fechaFin >= hoy;
  }

  private emptyFundacion(): Fundacion {
    return { ruc: '', nombre: '', mision: '', areaIntervencion: '', activa: true, tieneConvenio: false };
  }

  private emptyOferta(fundacion?: Fundacion): OfertaCuposFundacion {
    return {
      fundacion: fundacion ? { ...fundacion } : null as unknown as Fundacion,
      periodoAcademico: this.periodoActual(),
      distribucion: 'GENERAL',
      cuposTotales: 1,
      activo: true,
      observacion: '',
      carreras: [],
      cuposReservados: 0,
      cuposOcupados: 0,
      cuposDisponibles: 1
    };
  }

  private emptyConvenio(): Convenio {
    const inicio = new Date();
    const fin = new Date(inicio);
    fin.setFullYear(fin.getFullYear() + 1);
    return {
      codigo: '',
      empresa: null,
      fundacion: null,
      fechaInicio: inicio.toISOString().split('T')[0],
      fechaFin: fin.toISOString().split('T')[0],
      estado: 'VIGENTE',
      urlDocumento: '',
      cuposPactados: 0,
      carreras: []
    };
  }
}
