import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EmpresaService, Empresa } from '../../core/services/empresa.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { AuthService } from '../../core/services/auth.service';
import { Convenio, ConvenioService } from '../../core/services/convenio.service';
import { Carrera, CarreraService } from '../../core/services/carrera.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import {
  OfertaCuposEmpresa,
  OfertaCuposEmpresaCarrera,
  OfertaCuposEmpresaService
} from '../../core/services/oferta-cupos-empresa.service';
import { TAMANO_PAGINA_DEFECTO } from '../../core/services/paginacion';
import { PaginadorComponent } from '../shared/paginador/paginador';
import { forkJoin, of } from 'rxjs';
import { Usuario, UsuarioService } from '../../core/services/usuario.service';
import {
  TutorEmpresa,
  TutorEmpresaPayload,
  TutorEmpresaService
} from '../../core/services/tutor-empresa.service';

@Component({
  selector: 'app-empresas',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginadorComponent],
  templateUrl: './empresas.html',
  styleUrl: './empresas.css'
})
export class EmpresasComponent implements OnDestroy {
  private readonly empresaService = inject(EmpresaService);
  private readonly toastService = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  readonly authService = inject(AuthService);
  private readonly convenioService = inject(ConvenioService);
  private readonly carreraService = inject(CarreraService);
  private readonly ofertaCuposService = inject(OfertaCuposEmpresaService);
  private readonly expedienteService = inject(ExpedienteService);
  private readonly usuarioService = inject(UsuarioService);
  private readonly tutorEmpresaService = inject(TutorEmpresaService);

  empresas = signal<Empresa[]>([]);
  convenios = signal<Convenio[]>([]);
  carrerasCatalogo = signal<Carrera[]>([]);
  ofertasCupos = signal<OfertaCuposEmpresa[]>([]);
  loading = signal(true);
  isRefreshing = signal(false);
  showModal = signal(false);
  showConvenioModal = signal(false);
  showOfertaModal = signal(false);
  showTutoresModal = signal(false);
  loadingTutores = signal(false);
  savingTutor = signal(false);

  // Fase 43: paginación y filtros resueltos en el backend
  readonly tamanoPagina = TAMANO_PAGINA_DEFECTO;
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);
  filtroTexto = '';
  filtroActivo = 'TODOS';
  private timerBusqueda: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
    if (this.timerBusqueda) clearTimeout(this.timerBusqueda);
  }

  // Form Model
  currentEmpresa: Empresa = this.emptyEmpresa();
  isEdit = false;
  isEditConvenio = false;
  selectedEmpresaConvenio = signal<Empresa | null>(null);
  currentConvenio: Convenio = this.emptyConvenio();
  carrerasSeleccionadas: string[] = [];
  selectedEmpresaOferta = signal<Empresa | null>(null);
  currentOferta: OfertaCuposEmpresa = this.emptyOferta();
  selectedEmpresaTutor = signal<Empresa | null>(null);
  tutoresEmpresa = signal<TutorEmpresa[]>([]);
  candidatosTutor = signal<Usuario[]>([]);
  filtroTutorTexto = signal('');
  filtroTutorCarrera = signal('TODOS');
  busquedaCuentaTutor = signal('');
  currentTutorEmpresa: TutorEmpresaPayload & { id?: number } = this.emptyTutorEmpresa();

  carrerasFiltroTutores = computed(() => [...new Set(
    this.tutoresEmpresa().flatMap(vinculo => vinculo.carreras.map(carrera => carrera.nombre))
  )].sort((a, b) => a.localeCompare(b, 'es')));

  tutoresEmpresaFiltrados = computed(() => {
    const termino = this.normalizar(this.filtroTutorTexto());
    const carrera = this.filtroTutorCarrera();
    return this.tutoresEmpresa().filter(vinculo => {
      const coincideTexto = !termino || this.normalizar(
        `${vinculo.nombre} ${vinculo.apellido} ${vinculo.email || ''} ${vinculo.cargo || ''}`
      ).includes(termino);
      const coincideCarrera = carrera === 'TODOS'
        || vinculo.carreras.some(item => item.nombre === carrera);
      return coincideTexto && coincideCarrera;
    });
  });

  constructor() {
    this.loadEmpresas(true);
    this.carreraService.getAll().subscribe({
      next: carreras => this.carrerasCatalogo.set(carreras),
      error: () => this.carrerasCatalogo.set([])
    });
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
    this.loadEmpresas(false, pagina);
  }

  // Búsqueda con pequeña espera para no disparar una petición por tecla
  buscarConRetardo(): void {
    if (this.timerBusqueda) clearTimeout(this.timerBusqueda);
    this.timerBusqueda = setTimeout(() => this.aplicarFiltros(), 350);
  }

  aplicarFiltros(): void {
    this.loadEmpresas(false, 0);
  }

  loadEmpresas(isInitial = false, pagina = this.pagina()): void {
    if (isInitial) this.loading.set(true); else this.isRefreshing.set(true);
    const rol = this.authService.getRole();
    forkJoin({
      empresas: this.empresaService.getPaginado(pagina, this.tamanoPagina, {
        texto: this.filtroTexto,
        activo: this.filtroActivo
      }),
      convenios: rol === 'ADMIN' || rol === 'COORDINADOR'
        ? this.convenioService.getAll()
        : of([] as Convenio[]),
      ofertas: rol === 'ADMIN' || rol === 'COORDINADOR'
        ? this.ofertaCuposService.getAll(this.periodoActual())
        : of([] as OfertaCuposEmpresa[])
    }).subscribe({
      next: ({ empresas, convenios, ofertas }) => {
        this.empresas.set(empresas.contenido);
        this.pagina.set(empresas.pagina);
        this.totalPaginas.set(empresas.totalPaginas);
        this.totalElementos.set(empresas.totalElementos);
        this.convenios.set(convenios);
        this.ofertasCupos.set(ofertas);
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      },
      error: () => {
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.currentEmpresa = this.emptyEmpresa();
    this.isEdit = false;
    this.showModal.set(true);
  }

  openEditModal(empresa: Empresa): void {
    this.currentEmpresa = { ...empresa };
    this.isEdit = true;
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  saveEmpresa(): void {
    if (!this.currentEmpresa.ruc || !this.currentEmpresa.nombre) {
      this.toastService.warning('Por favor, completa los campos requeridos.');
      return;
    }

    if (this.isEdit && this.currentEmpresa.id) {
      this.empresaService.update(this.currentEmpresa.id, this.currentEmpresa).subscribe({
        next: () => {
          this.loadEmpresas();
          this.closeModal();
          this.toastService.success('Empresa actualizada correctamente.');
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo actualizar la empresa.')
      });
    } else {
      this.empresaService.create(this.currentEmpresa).subscribe({
        next: () => {
          this.loadEmpresas();
          this.closeModal();
          this.toastService.success('Empresa registrada correctamente.');
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo registrar la empresa.')
      });
    }
  }

  async deleteEmpresa(id: number): Promise<void> {
    if (await this.confirmService.confirm('¿Estás seguro de que deseas eliminar esta empresa?')) {
      this.empresaService.delete(id).subscribe({
        next: () => {
          this.loadEmpresas();
          this.toastService.success('Empresa eliminada.');
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo eliminar la empresa.')
      });
    }
  }

  toggleActive(empresa: Empresa): void {
    if (empresa.id) {
      const updated = { ...empresa, activo: !empresa.activo };
      this.empresaService.update(empresa.id, updated).subscribe({
        next: () => {
          this.loadEmpresas();
          this.toastService.success(updated.activo ? 'Empresa activada.' : 'Empresa desactivada.');
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo cambiar el estado de la empresa.')
      });
    }
  }

  isAdmin(): boolean {
    return this.authService.getRole() === 'ADMIN';
  }

  openTutores(empresa: Empresa): void {
    if (!empresa.id) return;
    this.selectedEmpresaTutor.set(empresa);
    this.showTutoresModal.set(true);
    this.loadingTutores.set(true);
    this.limpiarFiltrosTutores();
    this.currentTutorEmpresa = this.emptyTutorEmpresa(empresa.id);
    forkJoin({
      vinculos: this.tutorEmpresaService.getPorEmpresa(empresa.id),
      candidatos: this.isAdmin() ? this.usuarioService.getTutores() : of([] as Usuario[])
    }).subscribe({
      next: ({ vinculos, candidatos }) => {
        this.tutoresEmpresa.set(vinculos);
        this.candidatosTutor.set(candidatos.filter(usuario => {
          const tipo = String(usuario.tutorTipo || '').toUpperCase();
          return usuario.activo !== false && (tipo === 'PRACTICAS' || tipo === 'AMBOS');
        }));
        this.loadingTutores.set(false);
      },
      error: err => {
        this.loadingTutores.set(false);
        this.toastService.error(err?.error?.error || 'No se pudieron cargar los tutores de la empresa.');
      }
    });
  }

  closeTutores(): void {
    this.showTutoresModal.set(false);
    this.selectedEmpresaTutor.set(null);
    this.tutoresEmpresa.set([]);
    this.candidatosTutor.set([]);
    this.limpiarFiltrosTutores();
    this.currentTutorEmpresa = this.emptyTutorEmpresa();
  }

  nuevoTutorEmpresa(): void {
    this.currentTutorEmpresa = this.emptyTutorEmpresa(this.selectedEmpresaTutor()?.id);
    this.busquedaCuentaTutor.set('');
  }

  editarTutorEmpresa(vinculo: TutorEmpresa): void {
    this.currentTutorEmpresa = {
      id: vinculo.id,
      tutorId: vinculo.tutorId,
      empresaId: vinculo.empresaId,
      cargo: vinculo.cargo || '',
      activo: vinculo.activo,
      carreraIds: vinculo.carreras.map(carrera => carrera.id)
    };
  }

  tutoresCandidatosDisponibles(): Usuario[] {
    const ocupados = new Set(this.tutoresEmpresa()
      .filter(vinculo => vinculo.id !== this.currentTutorEmpresa.id)
      .map(vinculo => vinculo.tutorId));
    const termino = this.normalizar(this.busquedaCuentaTutor());
    return this.candidatosTutor().filter(usuario => {
      if (usuario.id == null || ocupados.has(usuario.id)) return false;
      if (usuario.id === this.currentTutorEmpresa.tutorId) return true;
      return !termino || this.normalizar(
        `${usuario.nombre} ${usuario.apellido} ${usuario.email}`
      ).includes(termino);
    });
  }

  limpiarFiltrosTutores(): void {
    this.filtroTutorTexto.set('');
    this.filtroTutorCarrera.set('TODOS');
    this.busquedaCuentaTutor.set('');
  }

  carreraTutorSeleccionada(carreraId?: number): boolean {
    return carreraId != null && this.currentTutorEmpresa.carreraIds.includes(carreraId);
  }

  toggleCarreraTutor(carreraId?: number): void {
    if (carreraId == null) return;
    this.currentTutorEmpresa.carreraIds = this.carreraTutorSeleccionada(carreraId)
      ? this.currentTutorEmpresa.carreraIds.filter(id => id !== carreraId)
      : [...this.currentTutorEmpresa.carreraIds, carreraId];
  }

  saveTutorEmpresa(): void {
    const empresaId = this.selectedEmpresaTutor()?.id;
    if (!empresaId || !this.currentTutorEmpresa.tutorId) {
      this.toastService.warning('Selecciona una cuenta de tutor externo.');
      return;
    }
    if (this.currentTutorEmpresa.carreraIds.length === 0) {
      this.toastService.warning('Selecciona al menos una carrera para el tutor.');
      return;
    }
    const payload: TutorEmpresaPayload = {
      ...this.currentTutorEmpresa,
      empresaId
    };
    const request = this.currentTutorEmpresa.id
      ? this.tutorEmpresaService.update(this.currentTutorEmpresa.id, payload)
      : this.tutorEmpresaService.create(payload);
    this.savingTutor.set(true);
    request.subscribe({
      next: guardado => {
        this.tutoresEmpresa.update(actuales => {
          const existe = actuales.some(item => item.id === guardado.id);
          return existe
            ? actuales.map(item => item.id === guardado.id ? guardado : item)
            : [...actuales, guardado].sort((a, b) => `${a.nombre} ${a.apellido}`.localeCompare(`${b.nombre} ${b.apellido}`));
        });
        this.currentTutorEmpresa = this.emptyTutorEmpresa(empresaId);
        this.savingTutor.set(false);
        this.toastService.success('Tutor externo vinculado con la empresa y sus carreras.');
      },
      error: err => {
        this.savingTutor.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo guardar el tutor externo.');
      }
    });
  }

  async deleteTutorEmpresa(vinculo: TutorEmpresa): Promise<void> {
    if (!(await this.confirmService.confirm(
      `¿Eliminar el vínculo de ${vinculo.nombre} ${vinculo.apellido} con esta empresa?`))) return;
    this.tutorEmpresaService.delete(vinculo.id).subscribe({
      next: () => {
        this.tutoresEmpresa.update(actuales => actuales.filter(item => item.id !== vinculo.id));
        if (this.currentTutorEmpresa.id === vinculo.id) this.nuevoTutorEmpresa();
        this.toastService.success('Vínculo del tutor eliminado.');
      },
      error: err => this.toastService.error(err?.error?.error || 'No se pudo eliminar el vínculo del tutor.')
    });
  }

  periodoActual(): string {
    return this.expedienteService.periodoActual();
  }

  ofertaDeEmpresa(empresaId?: number): OfertaCuposEmpresa | undefined {
    if (empresaId == null) return undefined;
    return this.ofertasCupos().find(oferta => oferta.empresa?.id === empresaId);
  }

  resumenOferta(empresaId?: number): string {
    const oferta = this.ofertaDeEmpresa(empresaId);
    if (!oferta) return 'Sin cupos configurados';
    if (!oferta.activo) return 'Capacidad inactiva';
    return `${oferta.cuposDisponibles || 0} libres de ${oferta.cuposTotales}`;
  }

  claseOfertaBoton(empresaId?: number): string {
    const oferta = this.ofertaDeEmpresa(empresaId);
    if (!oferta || !oferta.activo) return 'bg-slate-50 border-slate-200 text-slate-500 hover:bg-slate-100 hover:border-slate-300';
    return (oferta.cuposDisponibles || 0) > 0 
      ? 'bg-emerald-50 border-emerald-200 text-emerald-800 hover:bg-emerald-100 hover:border-emerald-300' 
      : 'bg-red-50 border-red-200 text-red-800 hover:bg-red-100 hover:border-red-300';
  }

  openOferta(empresa: Empresa): void {
    this.selectedEmpresaOferta.set(empresa);
    const oferta = this.ofertaDeEmpresa(empresa.id);
    this.currentOferta = oferta
      ? {
          ...oferta,
          empresa: { ...empresa },
          carreras: oferta.carreras.map(item => ({
            ...item,
            carrera: { ...item.carrera }
          }))
        }
      : this.emptyOferta(empresa);
    this.showOfertaModal.set(true);
  }

  closeOferta(): void {
    this.showOfertaModal.set(false);
    this.selectedEmpresaOferta.set(null);
    this.currentOferta = this.emptyOferta();
  }

  carrerasActivas(): Carrera[] {
    return this.carrerasCatalogo().filter(carrera => carrera.activo !== false);
  }

  asignacionCarrera(carreraId?: number): OfertaCuposEmpresaCarrera | undefined {
    if (carreraId == null) return undefined;
    return this.currentOferta.carreras.find(item => item.carrera.id === carreraId);
  }

  toggleCarreraOferta(carrera: Carrera): void {
    const existente = this.asignacionCarrera(carrera.id);
    this.currentOferta.carreras = existente
      ? this.currentOferta.carreras.filter(item => item.carrera.id !== carrera.id)
      : [...this.currentOferta.carreras, { carrera: { ...carrera }, cupos: 1 }];
  }

  actualizarCuposCarrera(carrera: Carrera, cupos: number | string): void {
    const asignacion = this.asignacionCarrera(carrera.id);
    if (asignacion) asignacion.cupos = Number(cupos);
  }

  cambiarDistribucion(): void {
    if (this.currentOferta.distribucion === 'GENERAL') {
      this.currentOferta.carreras = [];
    }
  }

  saveOferta(): void {
    const empresa = this.selectedEmpresaOferta();
    if (!empresa?.id) return;
    if (this.currentOferta.distribucion === 'GENERAL' && Number(this.currentOferta.cuposTotales) <= 0) {
      this.toastService.warning('La capacidad general debe tener al menos un cupo.');
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

    const payload: OfertaCuposEmpresa = {
      ...this.currentOferta,
      empresa: { ...empresa },
      periodoAcademico: this.periodoActual()
    };
    const request = payload.id
      ? this.ofertaCuposService.update(payload.id, payload)
      : this.ofertaCuposService.create(payload);
    request.subscribe({
      next: () => {
        this.loadEmpresas();
        this.closeOferta();
        this.toastService.success('Cupos del periodo guardados correctamente.');
      },
      error: err => this.toastService.error(
        err?.error?.error || 'No se pudieron guardar los cupos del periodo.')
    });
  }

  async deleteOferta(): Promise<void> {
    if (!this.currentOferta.id || !(await this.confirmService.confirm('¿Deseas eliminar la capacidad de cupos de esta empresa para el periodo?'))) return;
    this.ofertaCuposService.delete(this.currentOferta.id).subscribe({
      next: () => {
        this.loadEmpresas();
        this.closeOferta();
        this.toastService.success('Capacidad de cupos eliminada.');
      },
      error: err => this.toastService.error(
        err?.error?.error || 'No se pudo eliminar la capacidad de cupos.')
    });
  }

  conveniosDeEmpresa(empresaId?: number): Convenio[] {
    if (empresaId == null) return [];
    return this.convenios()
      .filter(convenio => convenio.empresa?.id === empresaId)
      .sort((a, b) => b.fechaFin.localeCompare(a.fechaFin));
  }

  estadoConvenio(empresaId?: number): string {
    const convenios = this.conveniosDeEmpresa(empresaId);
    if (convenios.some(convenio => this.esConvenioVigente(convenio))) return 'Vigente';
    if (convenios.some(convenio => convenio.estado === 'SUSPENDIDO')) return 'Suspendido';
    if (convenios.length > 0) return 'Vencido';
    return 'Sin convenio';
  }

  claseEstadoConvenio(empresaId?: number): string {
    const estado = this.estadoConvenio(empresaId);
    if (estado === 'Vigente') return 'bg-green-50 border-green-200 text-green-700';
    if (estado === 'Suspendido') return 'bg-amber-50 border-amber-200 text-amber-700';
    if (estado === 'Vencido') return 'bg-red-50 border-red-200 text-red-700';
    return 'bg-slate-100 border-slate-200 text-slate-500';
  }

  claseConvenio(convenio: Convenio): string {
    if (this.esConvenioVigente(convenio)) return 'bg-green-50 border-green-200 text-green-700';
    if (convenio.estado === 'SUSPENDIDO') return 'bg-amber-50 border-amber-200 text-amber-700';
    return 'bg-red-50 border-red-200 text-red-700';
  }

  openConvenios(empresa: Empresa): void {
    this.selectedEmpresaConvenio.set(empresa);
    this.nuevoConvenio();
    this.showConvenioModal.set(true);
  }

  closeConvenios(): void {
    this.showConvenioModal.set(false);
    this.selectedEmpresaConvenio.set(null);
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
    const empresa = this.selectedEmpresaConvenio();
    if (!empresa?.id || !this.currentConvenio.codigo || !this.currentConvenio.fechaInicio || !this.currentConvenio.fechaFin) {
      this.toastService.warning('Completa el código y las fechas del convenio.');
      return;
    }
    const payload: Convenio = {
      ...this.currentConvenio,
      empresa: { ...empresa },
      fundacion: null,
      carreras: [...this.carrerasSeleccionadas]
    };
    const request = this.isEditConvenio && payload.id
      ? this.convenioService.update(payload.id, payload)
      : this.convenioService.create(payload);
    request.subscribe({
      next: () => {
        this.loadEmpresas();
        this.nuevoConvenio();
        this.toastService.success('Convenio guardado correctamente.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo guardar el convenio.')
    });
  }

  async deleteConvenio(id: number): Promise<void> {
    if (!(await this.confirmService.confirm('¿Deseas eliminar este convenio?'))) return;
    this.convenioService.delete(id).subscribe({
      next: () => {
        this.loadEmpresas();
        this.nuevoConvenio();
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

  private emptyEmpresa(): Empresa {
    return {
      ruc: '',
      nombre: '',
      direccion: '',
      activo: true,
      tieneConvenio: false
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

  private emptyOferta(empresa?: Empresa): OfertaCuposEmpresa {
    return {
      empresa: empresa ? { ...empresa } : null as unknown as Empresa,
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

  private emptyTutorEmpresa(empresaId?: number): TutorEmpresaPayload & { id?: number } {
    return {
      tutorId: null,
      empresaId: empresaId || null,
      cargo: '',
      activo: true,
      carreraIds: []
    };
  }

  private normalizar(valor: string): string {
    return valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  }
}
