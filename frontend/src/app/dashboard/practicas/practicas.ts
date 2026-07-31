import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PracticaService, Practica, SeguimientoPractica } from '../../core/services/practica.service';
import { Estudiante, EstudianteService } from '../../core/services/estudiante.service';
import { EmpresaService, Empresa } from '../../core/services/empresa.service';
import { Usuario } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { finalize, forkJoin, of, switchMap } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { ConfiguracionService, FechaLimiteCalificacion } from '../../core/services/configuracion.service';
import { EvaluacionService, EvaluacionDetalle, EncuestaSatisfaccion } from '../../core/services/evaluacion.service';
import { NotaCoordinacion, NotaCoordinacionService } from '../../core/services/nota-coordinacion.service';
import { VacanteService, VacantePractica as Vacante } from '../../core/services/vacante.service';
import { PostulacionService, PostulacionMeritocratica } from '../../core/services/postulacion.service';
import { BitacoraService, Bitacora, correccionBitacoraPendiente } from '../../core/services/bitacora.service';
import { AsistenciaService, Asistencia } from '../../core/services/asistencia.service';
import { VinculacionService, Vinculacion } from '../../core/services/vinculacion.service';
import { FavoritoService } from '../../core/services/favorito.service';
import { AuditoriaService, RegistroAuditoria } from '../../core/services/auditoria.service';
import { Convenio, ConvenioService } from '../../core/services/convenio.service';
import { DocumentoService, TipoDocumento, DocEstudiante } from '../../core/services/documento.service';
import { ExpedienteService, EtapaAcademica } from '../../core/services/expediente.service';
import { NotaAcademicaService, NotaAcademica } from '../../core/services/nota-academica.service';
import { PaginadorComponent } from '../shared/paginador/paginador';
import { TAMANO_PAGINA_DEFECTO } from '../../core/services/paginacion';
import { BandejaTab, BandejaTabsComponent } from '../shared/bandeja-tabs/bandeja-tabs';
import { AccionDocumentoRevision, DocumentosRevisionPanelComponent, FiltrosDocumentosRevision } from '../shared/documentos-revision-panel/documentos-revision-panel';
import { SeguimientoAcademicoPanelComponent } from '../shared/seguimiento-academico-panel/seguimiento-academico-panel';
import { BitacorasRevisionPanelComponent } from '../shared/bitacoras-revision-panel/bitacoras-revision-panel';
import { AsistenciasHistorialPanelComponent } from '../shared/asistencias-historial-panel/asistencias-historial-panel';
import { FilaPase, asistenciaEnFecha, fechaLocalISO, nuevaFilaPase } from '../shared/pase-lista';
import { SIN_PERIODO, coincidePeriodo, periodosDisponibles, porcentajeAvance } from '../shared/periodo-avance';
import { CargaDocumentosPanelComponent } from '../shared/carga-documentos-panel/carga-documentos-panel';
import { CalificacionModalComponent } from '../shared/calificacion-modal/calificacion-modal';
import { ActaCierre, ActaCierreModalComponent } from '../shared/acta-cierre-modal/acta-cierre-modal';
import { Carrera, CarreraService } from '../../core/services/carrera.service';
import {
  OfertaCuposEmpresa,
  OfertaCuposEmpresaService
} from '../../core/services/oferta-cupos-empresa.service';
import { LineaTiempoExpediente, LineaTiempoService, estadoExpedienteTexto } from '../../core/services/linea-tiempo.service';
import { LineaTiempoExpedienteComponent } from '../shared/linea-tiempo-expediente/linea-tiempo-expediente';
import { ComentariosSeguimientoModalComponent } from '../shared/comentarios-seguimiento-modal/comentarios-seguimiento-modal';
import { TutorEmpresa, TutorEmpresaService } from '../../core/services/tutor-empresa.service';

export function postulacionPracticaActiva(estado?: string | null): boolean {
  return ['pendiente', 'procesado'].includes(String(estado || '').trim().toLowerCase());
}

export function tienePracticaActiva(practicas: Pick<Practica, 'estado'>[]): boolean {
  return practicas.some(practica =>
    ['pendiente', 'en_curso'].includes(String(practica.estado || '').trim().toLowerCase()));
}

export function asistenciaParaPractica(
  practicaId: number,
  datos: Pick<Asistencia, 'fecha' | 'horaIngreso' | 'horaSalida' | 'estado' | 'observaciones'>
): Asistencia {
  return { practica: { id: practicaId }, ...datos };
}

export function carrerasBaseParaVacante(
  rol: string,
  catalogo: Carrera[],
  asignadas: string[]
): string[] {
  const activas = catalogo.filter(c => c.activo !== false).map(c => c.nombre);
  if (rol === 'ADMIN') return activas;
  if (rol !== 'COORDINADOR') return [];
  const normalizar = (valor: string) => valor.trim().toLowerCase()
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  const alcance = new Set(asignadas.map(normalizar));
  return activas.filter(carrera => alcance.has(normalizar(carrera)));
}

export function cuposDisponiblesOferta(
  oferta: OfertaCuposEmpresa | undefined,
  carrera: string
): number {
  if (!oferta || !oferta.activo || !carrera) return 0;
  if (oferta.distribucion === 'GENERAL') return Number(oferta.cuposDisponibles || 0);
  const normalizar = (valor: string) => valor.trim().toLowerCase()
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  return Number(oferta.carreras.find(item =>
    normalizar(item.carrera.nombre) === normalizar(carrera)
  )?.cuposDisponibles || 0);
}

import { AutocompleteComponent } from '../shared/autocomplete/autocomplete.component';

@Component({
  selector: 'app-practicas',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, BandejaTabsComponent, DocumentosRevisionPanelComponent,
    SeguimientoAcademicoPanelComponent, BitacorasRevisionPanelComponent, AsistenciasHistorialPanelComponent, CargaDocumentosPanelComponent,
    CalificacionModalComponent, ActaCierreModalComponent, LineaTiempoExpedienteComponent,
    ComentariosSeguimientoModalComponent, PaginadorComponent, AutocompleteComponent],
  templateUrl: './practicas.html',
  styleUrl: './practicas.css'
})
export class PracticasComponent {
  private readonly practicaService = inject(PracticaService);
  private readonly estudianteService = inject(EstudianteService);
  private readonly empresaService = inject(EmpresaService);
  readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly vacanteService = inject(VacanteService);
  private readonly postulacionService = inject(PostulacionService);
  private readonly bitacoraService = inject(BitacoraService);
  private readonly asistenciaService = inject(AsistenciaService);
  private readonly vinculacionService = inject(VinculacionService);
  private readonly evaluacionService = inject(EvaluacionService);
  private readonly notaCoordinacionService = inject(NotaCoordinacionService);
  private readonly favoritoService = inject(FavoritoService);
  private readonly auditoriaService = inject(AuditoriaService);
  private readonly convenioService = inject(ConvenioService);
  private readonly documentoService = inject(DocumentoService);
  private readonly expediente = inject(ExpedienteService);
  private readonly notaAcademicaService = inject(NotaAcademicaService);
  private readonly carreraService = inject(CarreraService);
  private readonly ofertaCuposService = inject(OfertaCuposEmpresaService);
  private readonly lineaTiempoService = inject(LineaTiempoService);
  private readonly tutorEmpresaService = inject(TutorEmpresaService);

  // General State
  loading = signal(true);
  userRole = signal('');
  activeTab = signal('lista');

  // Fase 47: liberación única y justificada del cupo de empresa de un retiro
  liberandoPractica = signal<Practica | null>(null);
  motivoLiberacion = '';
  liberandoCupo = signal(false);

  tabsVisibles(): BandejaTab[] {
    if (this.userRole() === 'ADMIN' || this.userRole() === 'COORDINADOR') {
      return [
        { id: 'lista', label: 'Asignaciones' }, { id: 'seguimiento_coord', label: 'Seguimiento' },
        { id: 'meritocracia', label: 'Meritocracia' }, { id: 'vacantes', label: 'Vacantes' },
        { id: 'calificaciones_adm', label: 'Notas y plazos' }, { id: 'directa', label: 'Asignación directa' },
        { id: 'documentos_revision', label: 'Documentos' }
      ];
    }
    if (this.userRole() === 'TUTOR') {
      return [{ id: 'tutorados', label: 'Estudiantes a cargo' }, { id: 'asistencia_tutor', label: 'Asistencia' },
        { id: 'bitacoras_tutor', label: 'Revisar bitácoras' }];
    }
    // Las notas académicas y asistencias del estudiante viven en el módulo
    // "Mi Expediente" de la barra lateral
    const tabs: BandejaTab[] = [{ id: 'mi_progreso', label: 'Mi avance' }];
    // El flujo de postulación a prácticas solo se habilita cuando la etapa
    // real del estudiante es Práctica I/II (Vinculación va primero; el
    // backend rechaza igual, aquí se bloquea la UI con el motivo visible).
    if (this.enEtapaPracticas()) {
      if (!this.documentosCompletos()) tabs.push({ id: 'cargar_docs', label: '1. Documentos' });
      if (this.documentosCompletos() && !this.postulacionHabilitada() && !this.actividadActiva() && !this.miPostulacionActiva()) {
        tabs.push({ id: 'espera_postulacion', label: 'En espera', emphasis: 'warning' });
      }
      if (this.puedeIniciarPostulacion()) {
        tabs.push({ id: 'ofertas', label: '2. Ofertas' }, { id: 'preferencias_merit', label: '3. Preferencias' });
      }
    }
    if (tienePracticaActiva(this.practicas())) tabs.push({ id: 'bitacora_estudiante', label: 'Registrar bitácora' });
    return tabs;
  }

  cambiarTab(tab: string): void {
    if (this.userRole() === 'ESTUDIANTE'
        && ['cargar_docs', 'ofertas', 'preferencias_merit', 'espera_postulacion'].includes(tab)
        && !this.enEtapaPracticas()) {
      this.activeTab.set('mi_progreso');
      this.toastService.warning('Tu proceso académico actual es Vinculación. Continúa ese proceso antes de acceder al flujo de Prácticas.');
      return;
    }
    this.activeTab.set(tab);
    if (tab === 'documentos_revision') this.loadDocumentosRevision();
    if (tab === 'lista' && ['ADMIN', 'COORDINADOR'].includes(this.userRole())) this.cargarPaginaLista();
    if (['seguimiento_coord', 'calificaciones_adm'].includes(tab) && ['ADMIN', 'COORDINADOR'].includes(this.userRole())) this.cargarPaginaSeguimiento();
    // El estudiante puede enviar/corregir una bitacora desde otra sesion en
    // cualquier momento; sin esto la bandeja del tutor queda con el snapshot
    // del login hasta un F5 completo.
    if (tab === 'bitacoras_tutor' && this.userRole() === 'TUTOR') {
      this.bitacoraService.getAll(true).subscribe(bitacoras => this.bitacoras.set(bitacoras));
    }
    // El tutor y el coordinador pueden calificar/completar una encuesta desde
    // otra sesion en cualquier momento; sin esto "Mi avance" queda con el
    // snapshot del login hasta un F5 completo.
    if (tab === 'mi_progreso' && this.userRole() === 'ESTUDIANTE') {
      this.evaluacionService.getAll().subscribe(evaluaciones => this.evaluaciones.set(evaluaciones));
    }
  }

  cambiarFiltrosDocumentos(filtros: FiltrosDocumentosRevision): void {
    this.filtroDocProceso = filtros.proceso;
    this.filtroDocEstado = filtros.estado;
    this.filtroDocCarrera = filtros.carrera;
    this.loadDocumentosRevision();
  }

  resolverBitacora(evento: { bitacora: Bitacora; estado: 'aprobada' | 'rechazada' | 'requiere_correccion'; observacion?: string }): void {
    this.processBitacora(evento.bitacora, evento.estado, evento.observacion);
  }

  // DB Data
  practicas = signal<Practica[]>([]);
  estudiantes = signal<Estudiante[]>([]);
  empresas = signal<Empresa[]>([]);
  tutores = signal<Usuario[]>([]);
  // Vinculaciones propias del estudiante: determinan su etapa academica
  misVinculaciones = signal<Vinculacion[]>([]);
  lineaTiempoPersonal = signal<LineaTiempoExpediente[]>([]);
  lineaTiempoPersonalCargando = signal(false);
  historialPersonal = signal<Practica | null>(null);
  historialPersonalSeleccionado = signal<LineaTiempoExpediente | null>(null);

  // Convocatoria Dates
  convocatoriaInicio = '2025-06-01';
  convocatoriaFin = '2025-07-31';
  fechaLimiteDocumentos = '2025-06-20';
  fechaInicioPostulacion = '2025-06-25';
  vacantes = signal<Vacante[]>([]);
  convenios = signal<Convenio[]>([]);
  carrerasCatalogo = signal<Carrera[]>([]);
  ofertasCupos = signal<OfertaCuposEmpresa[]>([]);
  showVacanteModal = signal(false);

  // Computed: postulación habilitada si la fecha actual >= fechaInicioPostulacion
  postulacionHabilitada(): boolean {
    const hoy = new Date().toISOString().split('T')[0];
    return hoy >= this.fechaInicioPostulacion;
  }

  // Search and Advanced Filters Signals
  searchText = signal('');

  // Autocomplete bindings
  buscarEstudiante = (texto: string) => this.estudianteService.getPaginado(0, 10, { texto }).pipe(map(res => res.contenido));
  displayEstudiante = (est: Estudiante) => `${est.usuario?.nombre || ''} ${est.usuario?.apellido || ''} (Carrera: ${est.carrera || 'N/A'})`;
  resolverEstudiante = (id: number) => this.estudianteService.getById(id);

  buscarVacante = (texto: string) => of(this.getFilteredVacantes().filter(v => 
    v.nombre.toLowerCase().includes(texto.toLowerCase()) || 
    v.empresa.nombre.toLowerCase().includes(texto.toLowerCase())
  ).slice(0, 10));
  displayVacante = (vac: Vacante) => `${vac.nombre} - ${vac.empresa.nombre} (${vac.cupos} cupos)`;
  resolverVacante = (id: number) => of(this.vacantes().find(v => v.id === id)!);
  filtroModalidadTrabajo = signal('TODOS');
  filtroTipoEmpresa = signal('TODOS');
  filtroSoloCupos = signal(false);
  
  // Vacantes specific filters
  filtroVacanteEmpresa = signal('TODOS');
  filtroVacanteCarrera = signal('TODOS');
  filtroVacanteModalidad = signal('TODOS');
  favoritasList = signal<number[]>([]);

  // Selected Detail Modal Signal
  selectedDetailVacante = signal<Vacante | null>(null);
  showDetailModal = signal(false);

  // New Vacante Model
  newVacante: Vacante = this.emptyVacante();

  // Student documents & preferences states
  cvCargado = signal(false);
  cartaCargada = signal(false);
  cedulaCargada = signal(false);
  cartaAceptacionCargada = signal(false);
  documentosDetalle = signal<DocEstudiante[]>([]);
  subiendoDocumento = signal<TipoDocumento | null>(null);
  notasAcademicas = signal<NotaAcademica[]>([]);

  pref1Id: number | null = null;
  pref2Id: number | null = null;
  pref3Id: number | null = null;

  // Meritocracy applications list
  postulaciones = signal<PostulacionMeritocratica[]>([]);
  tutoresMeritocracia = signal<Record<number, TutorEmpresa[]>>({});
  tutorMeritocraciaSeleccionado = signal<Record<number, number | null>>({});

  // Bitacoras & Asistencias
  bitacoras = signal<Bitacora[]>([]);
  procesandoBitacoraId = signal<number | null>(null);
  bitacoraEnCorreccion = signal<Bitacora | null>(null);
  guardandoBitacora = signal(false);
  newBitacora = { fecha: '', actividad: '', horas: 4, horasExtra: 0, parcial: 1 as 1 | 2 | 3, observaciones: '' };

  asistencias = signal<Asistencia[]>([]);

  // Grading (persistido en EVALUACIONES_PRACTICAS_DETALLE y
  // FECHAS_LIMITE_CALIFICACION; sin fecha configurada no se bloquea)
  gradeDeadlines = signal({ parcial1: '', parcial2: '', parcial3: '' });
  evaluaciones = signal<EvaluacionDetalle[]>([]);

  selectedPracticaForGrading = signal<Practica | null>(null);
  tGrade: number = 0;
  cGrade: number = 0;
  selectedParcial = signal(1);

  // Surveys
  showSurveyModal = signal(false);
  surveyTargetParcial = signal(1);

  // Monitoring
  seguimiento = signal<SeguimientoPractica[]>([]);
  filtroSeguimiento = signal('TODOS');
  showSeguimientoModal = signal(false);
  selectedPracForSeguimiento = signal<Practica | null>(null);
  encuestasSeguimiento = signal<EncuestaSatisfaccion[]>([]);
  comentariosCoordinacionSeguimiento = signal<NotaCoordinacion[]>([]);
  surveyAnswers = { tutorScore: 5, companyScore: 5, relevanceScore: 5, clarityScore: 5, comment: '' };

  // Advanced Filters
  filtroFase = signal('TODOS');
  // Default "EN_CURSO" (no "TODOS"): con cada periodo se acumulan expedientes
  // cerrados y sin esto la bandeja diaria del coordinador se llena de historial.
  filtroEstado = signal('EN_CURSO');

  // Paginación lista
  paginaLista = signal(0);
  tamanoPaginaLista = signal(TAMANO_PAGINA_DEFECTO);
  totalElementosLista = signal(0);
  totalPaginasLista = signal(0);
  cargandoLista = signal(false);

  // Filtros del seguimiento/calificaciones (se envían al backend)
  filtroSeguimientoPeriodo = signal(this.expediente.periodoActual());
  filtroSeguimientoCarrera = signal('TODOS');
  filtroSeguimientoTutor = signal('TODOS');

  // Paginación seguimiento
  paginaSeguimiento = signal(0);
  tamanoPaginaSeguimiento = signal(TAMANO_PAGINA_DEFECTO);
  totalElementosSeguimiento = signal(0);
  totalPaginasSeguimiento = signal(0);
  cargandoSeguimiento = signal(false);
  cerrandoPracticaId = signal<number | null>(null);
  cargandoActaPracticaId = signal<number | null>(null);

  // Form selections
  selectedEstudianteId: number | null = null;
  selectedEstudianteObj: Estudiante | null = null;
  selectedVacanteId: number | null = null;
  selectedVacanteObj: Vacante | null = null;
  selectedTutorId = signal<number | null>(null);
  tutoresCompatiblesDirecta = signal<TutorEmpresa[]>([]);
  cargandoTutoresDirecta = signal(false);
  tutoresCompatiblesPractica = signal<Record<number, TutorEmpresa[]>>({});
  continuidadOrigenId = signal<number | null>(null);

  // Manual Adjustment Modal
  showAjusteModal = signal(false);
  selectedPostForAjuste = signal<PostulacionMeritocratica | null>(null);
  ajusteEmpresaId: number | null = null;
  ajusteJustificacion: string = '';

  // Audit Logs (tabla AUDITORIA, eventos de asignaciones meritocraticas)
  auditLogs = signal<RegistroAuditoria[]>([]);
  documentosRevision = signal<DocEstudiante[]>([]);
  cargandoDocumentosRevision = signal(false);
  accionDocumentoRevision = signal<AccionDocumentoRevision | null>(null);
  filtroDocProceso: 'TODOS' | 'GENERAL' | 'PRACTICAS' | 'VINCULACION' = 'PRACTICAS';
  filtroDocEstado: 'TODOS' | 'pendiente' | 'cargado' | 'en_revision' | 'aprobado' | 'rechazado' | 'requiere_correccion' = 'cargado';
  filtroDocCarrera = 'TODOS';
  observacionRevision: Record<number, string> = {};

  constructor() {
    const user = this.authService.currentUser();
    if (user) {
      this.userRole.set(user.rol);
      if (user.rol === 'ESTUDIANTE') {
        this.activeTab.set('mi_progreso');
      } else if (user.rol === 'TUTOR') {
        this.activeTab.set('tutorados');
      } else {
        this.activeTab.set('lista');
      }
    }
    this.loadData();
    this.loadFavoritosYAuditoria();
  }

  // Fecha limite por defecto para nuevas vacantes: 30 dias desde hoy
  private fechaLimiteDefault(): string {
    const d = new Date();
    d.setDate(d.getDate() + 30);
    return d.toISOString().split('T')[0];
  }

  emptyVacante(): Vacante {
    return {
      nombre: '',
      empresa: null as unknown as Empresa,
      cupos: 5,
      horas: 120,
      descripcion: '',
      carrera: '',
      modalidadAcademica: 'Práctica I',
      area: '',
      ciudad: 'Quito',
      tipoEmpresa: 'Privada',
      modalidadTrabajo: 'Presencial',
      fechaLimite: this.fechaLimiteDefault(),
      altaDemanda: false,
      requisitos: '',
      perfilRequerido: '',
      periodoAcademico: this.expediente.periodoActual()
    };
  }

  loadData(): void {
    this.loading.set(true);
    const user = this.authService.currentUser();
    if (!user) return;
    const esGestion = user.rol === 'ADMIN' || user.rol === 'COORDINADOR';
    const esTutor = user.rol === 'TUTOR';
    const esEstudiante = user.rol === 'ESTUDIANTE';

    forkJoin({
      empresas: esTutor ? of([] as Empresa[]) : this.empresaService.getAll(),
      estudiantes: this.expediente.estudiantesSegunRol(user.rol),
      usuarios: esGestion ? this.expediente.usuariosSegunRol(user.rol) : of([] as Usuario[]),
      // ADMIN/COORDINADOR obtienen su listado con cargarPaginaLista() (paginado
      // en BD); pedir aquí practicasSegunRol() para ellos traía TODAS las
      // prácticas (getAll sin paginar) solo para descartar el resultado abajo.
      practicas: esGestion ? of([] as Practica[]) : this.expediente.practicasSegunRol(user.rol),
      configuraciones: esTutor ? of([]) : this.configuracionService.getByTipo('PRACTICAS'),
      vacantes: esTutor ? of([] as Vacante[]) : this.vacanteService.getAll(this.expediente.periodoActual()),
      postulaciones: this.expediente.postulacionesPracticasSegunRol(user.rol),
      bitacoras: this.expediente.bitacorasSegunRol(user.rol),
      asistencias: this.expediente.asistenciasSegunRol(user.rol),
      vinculaciones: esEstudiante
        ? this.expediente.vinculacionesSegunRol(user.rol)
        : of([] as Vinculacion[]),
      evaluaciones: esGestion ? of([] as EvaluacionDetalle[]) : this.evaluacionService.getAll(),
      fechasLimite: this.configuracionService.getFechasLimite(),
      notasAcademicas: esEstudiante
        ? this.notaAcademicaService.getMisNotas()
        : of([] as NotaAcademica[]),
      seguimiento: of([] as SeguimientoPractica[]),
      convenios: esGestion
        ? this.convenioService.getAll()
        : of([] as Convenio[]),
      carreras: esGestion
        ? this.carreraService.getAll()
        : of([] as Carrera[]),
      ofertasCupos: esGestion
        ? this.ofertaCuposService.getAll(this.expediente.periodoActual())
        : of([] as OfertaCuposEmpresa[])
    }).subscribe({
      next: (res: { estudiantes: Estudiante[], empresas: Empresa[], usuarios: Usuario[], practicas: Practica[], configuraciones: any[], vacantes: Vacante[], postulaciones: PostulacionMeritocratica[], bitacoras: Bitacora[], asistencias: Asistencia[], vinculaciones: Vinculacion[], evaluaciones: EvaluacionDetalle[], fechasLimite: FechaLimiteCalificacion[], notasAcademicas: NotaAcademica[], seguimiento: SeguimientoPractica[], convenios: Convenio[], carreras: Carrera[], ofertasCupos: OfertaCuposEmpresa[] }) => {
        const assigned = this.authService.carrerasAsignadas();

        this.vacantes.set(res.vacantes);
        this.postulaciones.set(res.postulaciones);
        this.bitacoras.set(res.bitacoras);
        this.asistencias.set(res.asistencias);
        this.evaluaciones.set(res.evaluaciones);
        this.notasAcademicas.set([...res.notasAcademicas].sort((a, b) =>
          String(b.periodoAcademico || '').localeCompare(String(a.periodoAcademico || ''))));
        this.seguimiento.set(res.seguimiento);
        this.convenios.set(res.convenios);
        this.carrerasCatalogo.set(res.carreras.filter(c => c.activo !== false));
        this.ofertasCupos.set(res.ofertasCupos);

        // Fechas limite de calificacion del periodo mas reciente
        const limites = [...res.fechasLimite]
          .sort((a, b) => String(b.periodoAcademico).localeCompare(String(a.periodoAcademico)));
        const limiteDe = (p: number) => limites.find(l => l.parcial === p)?.fechaLimite || '';
        this.gradeDeadlines.set({
          parcial1: limiteDe(1),
          parcial2: limiteDe(2),
          parcial3: limiteDe(3)
        });

        // Convocatoria vigente: la del periodo academico mas reciente
        const practicasConfig = this.expediente.configVigente(res.configuraciones, 'PRACTICAS');
        if (practicasConfig) {
          this.convocatoriaInicio = practicasConfig.convocatoriaInicio;
          this.convocatoriaFin = practicasConfig.convocatoriaFin;
          this.fechaLimiteDocumentos = practicasConfig.fechaLimiteDocumentos;
          this.fechaInicioPostulacion = practicasConfig.fechaInicioPostulacion;
        }

        if (user.rol === 'COORDINADOR') {
          this.estudiantes.set(res.estudiantes.filter((e: Estudiante) => assigned.includes(e.carrera)));
        } else {
          this.estudiantes.set(res.estudiantes);
        }

        this.tutores.set(this.expediente.soloTutores(res.usuarios, 'PRACTICAS'));

        // 2. Empresas
        this.empresas.set(res.empresas.filter((e: Empresa) => e.activo));

        // 3. Tutores: solo usuarios con rol TUTOR
        this.tutores.set(this.expediente.soloTutores(res.usuarios, 'PRACTICAS'));

        // Los endpoints personales ya resuelven estudiante/tutor desde el JWT.
        if (user.rol === 'ESTUDIANTE') {
          this.practicas.set(res.practicas);
          this.misVinculaciones.set(res.vinculaciones);
        } else if (user.rol === 'TUTOR') {
          // El tutor trabaja sobre sus tutorías (antes caían al else y se perdían)
          this.practicas.set(res.practicas);
          this.inicializarPeriodoTutor();
        } else if (user.rol === 'COORDINADOR') {
          // No cargamos TODAS para coordinador. Se usa cargarPaginaLista.
          this.practicas.set([]);
        } else {
          this.practicas.set([]);
        }

        this.loading.set(false);
        if (esGestion) this.cargarTutoresMeritocracia();
        if (this.activeTab() === 'lista' && esGestion) this.cargarPaginaLista();
        if (['seguimiento_coord', 'calificaciones_adm'].includes(this.activeTab()) && esGestion) this.cargarPaginaSeguimiento();
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  loadFavoritosYAuditoria(): void {
    const user = this.authService.currentUser();
    if (!user) return;

    if (user.rol === 'ESTUDIANTE') {
      this.favoritoService.getMisFavoritos().subscribe({
        next: ids => this.favoritasList.set(ids),
        error: () => this.favoritasList.set([])
      });
      this.documentoService.getMisDocumentosDetalle('PRACTICAS').subscribe({
        next: documentos => this.aplicarDocumentos(documentos),
        error: () => {}
      });
    }

    if (user.rol === 'ADMIN') {
      this.loadAuditLogs();
    }
    if (user.rol === 'ADMIN' || user.rol === 'COORDINADOR') {
      this.loadDocumentosRevision();
    }
  }

  loadAuditLogs(): void {
    if (this.authService.currentUser()?.rol !== 'ADMIN') {
      this.auditLogs.set([]);
      return;
    }
    this.auditoriaService.getByTabla('POSTULACIONES_MERITOCRATICAS').subscribe({
      next: logs => this.auditLogs.set(logs),
      error: () => this.auditLogs.set([])
    });
  }

  loadDocumentosRevision(): void {
    this.cargandoDocumentosRevision.set(true);
    this.documentoService.getRevision({
      proceso: this.filtroDocProceso,
      estado: this.filtroDocEstado,
      carrera: this.filtroDocCarrera
    }).pipe(finalize(() => this.cargandoDocumentosRevision.set(false))).subscribe({
      next: docs => this.documentosRevision.set(docs),
      error: () => this.documentosRevision.set([])
    });
  }

  // Filtered Vacantes based on coordinator or student career
  get vacantesPorCategoria() {
    const list = this.getFilteredVacantes();
    const grouped = list.reduce((acc, vac) => {
      const cat = vac.area || 'Otras Áreas';
      if (!acc[cat]) acc[cat] = [];
      acc[cat].push(vac);
      return acc;
    }, {} as Record<string, any[]>);
    return Object.keys(grouped).map(key => ({ categoria: key, vacantes: grouped[key] }));
  }

  getFilteredVacantes(): Vacante[] {
    const user = this.authService.currentUser();
    if (!user) return [];

    let list = this.vacantes();

    if (user.rol === 'COORDINADOR') {
      const assigned = this.authService.carrerasAsignadas();
      list = list.filter(v => assigned.includes(v.carrera));
    } else if (user.rol === 'ESTUDIANTE') {
      const currentStudent = this.estudiantes().find(e => e.usuario?.email === user.email);
      if (currentStudent) {
        // Etapa academica real derivada del expediente (no simulada)
        const etapa = this.etapaEstudiante();
        if (etapa === 'COMPLETADO') return [];

        // Apply strict criteria filters: career AND modality matches
        list = list.filter(v => v.carrera === currentStudent.carrera && v.modalidadAcademica === etapa);
      } else {
        return [];
      }
    }

    // Apply Smart Search Input (Company, Area, City, Keywords)
    const q = this.searchText().toLowerCase().trim();
    if (q) {
      list = list.filter(v =>
        v.nombre.toLowerCase().includes(q) ||
        (v.empresa?.nombre || '').toLowerCase().includes(q) ||
        v.area.toLowerCase().includes(q) ||
        v.ciudad.toLowerCase().includes(q) ||
        v.descripcion.toLowerCase().includes(q)
      );
    }

    // Apply Advanced Filters
    if (this.filtroModalidadTrabajo() !== 'TODOS') {
      list = list.filter(v => v.modalidadTrabajo === this.filtroModalidadTrabajo());
    }

    if (this.filtroTipoEmpresa() !== 'TODOS') {
      list = list.filter(v => v.tipoEmpresa === this.filtroTipoEmpresa());
    }

    if (this.filtroSoloCupos()) {
      list = list.filter(v => Number(v.cupos) > 0);
    }

    if (this.filtroVacanteEmpresa() !== 'TODOS') {
      list = list.filter(v => (v.empresa?.id || 0).toString() === this.filtroVacanteEmpresa());
    }

    if (this.filtroVacanteCarrera() !== 'TODOS') {
      list = list.filter(v => v.carrera === this.filtroVacanteCarrera());
    }

    if (this.filtroVacanteModalidad() !== 'TODOS') {
      list = list.filter(v => v.modalidadAcademica === this.filtroVacanteModalidad());
    }

    return list;
  }

  // Filtered Meritocracy Postulations based on coordinator career.
  // Solo estados accionables (Pendiente/Procesado): una vez Aprobada o
  // Rechazada, la postulacion queda resuelta y no debe seguir ocupando
  // espacio en la bandeja de trabajo de meritocracia.
  getFilteredPostulaciones(): PostulacionMeritocratica[] {
    const assigned = this.authService.carrerasAsignadas();
    return this.postulaciones().filter(p =>
      assigned.includes(p.estudiante?.carrera)
      && (p.estado === 'Pendiente' || p.estado === 'Procesado'));
  }

  seguimientoFiltrado(): SeguimientoPractica[] {
    return this.seguimiento();
  }

  totalSeguimientoRiesgo(): number {
    return this.seguimiento().filter(item => item.riesgo).length;
  }

  totalBitacorasPendientes(): number {
    return this.seguimiento().reduce((acc, item) => acc + item.bitacorasPendientes, 0);
  }

  totalNotasPendientes(): number {
    return this.seguimiento().reduce((acc, item) => acc + item.notasTutorPendientes + item.notasCoordPendientes, 0);
  }

  totalEncuestasPendientes(): number {
    return this.seguimiento().reduce((acc, item) => acc + item.encuestasPendientes, 0);
  }

  // Helpers de presentacion para plantilla
  vacLabel(v?: Vacante | null): string {
    return v ? `${v.nombre} (${v.empresa?.nombre || 'Sin empresa'})` : 'Ninguna';
  }

  postNombre(p?: PostulacionMeritocratica | null): string {
    const u = p?.estudiante?.usuario;
    return u ? `${u.nombre} ${u.apellido}` : '—';
  }

  private estudianteActual(): Estudiante | undefined {
    const user = this.authService.currentUser();
    return user ? this.estudiantes().find(e => e.usuario?.email === user.email) : undefined;
  }

  // Etapa academica REAL del estudiante, derivada de su expediente
  etapaEstudiante(): EtapaAcademica {
    return this.expediente.etapaDe(this.practicas(), this.misVinculaciones());
  }

  // Codigo de etapa ('PRACTICA_1'/'PRACTICA_2') usado para distinguir la
  // carta/carta_aceptacion vigente de la de una etapa anterior ya cerrada.
  etapaEstudianteCodigo(): 'PRACTICA_1' | 'PRACTICA_2' | null {
    return this.expediente.etapaComoCodigo(this.etapaEstudiante());
  }

  // Regla de exclusividad: no se puede postular con un proceso activo
  actividadActiva(): boolean {
    return this.expediente.hayActividadActiva(this.practicas(), this.misVinculaciones());
  }

  documentosCompletos(): boolean {
    return this.cvCargado() && this.cartaCargada() && this.cedulaCargada();
  }

  miPostulacionActiva(): PostulacionMeritocratica | undefined {
    const est = this.estudianteActual();
    if (!est?.id) return undefined;
    return this.postulaciones()
      .filter(p => p.estudiante?.id === est.id)
      .filter(p => postulacionPracticaActiva(p.estado))
      .sort((a, b) => (b.id || 0) - (a.id || 0))[0];
  }

  // Tutor: sus expedientes ordenados por estudiante y con buscador,
  // para ubicar rápido a cada tutorado y sus notas por parcial
  filtroTutorados = '';
  vistaTutorados = signal<'ACTIVOS' | 'HISTORIAL'>('ACTIVOS');
  filtroCarreraTutor = signal('TODAS');
  comentarioPractica = signal<Practica | null>(null);

  // Un tutor puede estar vinculado a varias carreras de una misma empresa;
  // el filtro solo se muestra si de verdad tiene tutorados de mas de una.
  carrerasTutor(): string[] {
    const carreras = this.practicas()
      .map(prac => prac.estudiante?.carrera)
      .filter((c): c is string => !!c);
    return [...new Set(carreras)].sort((a, b) => a.localeCompare(b, 'es'));
  }

  // Módulo del tutor ordenado por periodo académico: por defecto el periodo
  // institucional activo; los anteriores quedan como historial consultable
  periodoTutor = signal('TODOS');
  readonly SIN_PERIODO = SIN_PERIODO;

  periodosTutor(): string[] {
    return periodosDisponibles(this.practicas().map(practica => practica.periodoAcademico));
  }

  private inicializarPeriodoTutor(): void {
    const activo = this.expediente.periodoActual();
    this.periodoTutor.set(this.periodosTutor().includes(activo) ? activo : 'TODOS');
  }

  practicasDelPeriodo(): Practica[] {
    return this.practicas().filter(practica =>
      coincidePeriodo(this.periodoTutor(), practica.periodoAcademico));
  }

  avance(completadas?: number | null, requeridas?: number | null): number {
    return porcentajeAvance(completadas, requeridas);
  }

  tutoradosOrdenados(): Practica[] {
    const texto = this.filtroTutorados.trim().toLowerCase();
    const esActiva = (prac: Practica) =>
      ['pendiente', 'en_curso'].includes(String(prac.estado || '').toLowerCase());
    return this.practicasDelPeriodo()
      .filter(prac => this.vistaTutorados() === 'ACTIVOS'
        ? !this.expedienteTerminal(prac.estado)
        : this.expedienteTerminal(prac.estado))
      .filter(prac => this.filtroCarreraTutor() === 'TODAS'
        || prac.estudiante?.carrera === this.filtroCarreraTutor())
      .filter(prac => {
        if (!texto) return true;
        const u = prac.estudiante?.usuario;
        return [u?.nombre, u?.apellido, prac.estudiante?.matricula, prac.empresa?.nombre, prac.estudiante?.carrera]
          .filter(Boolean).join(' ').toLowerCase().includes(texto);
      })
      .sort((a, b) => {
        const porEstado = (esActiva(a) ? 0 : 1) - (esActiva(b) ? 0 : 1);
        if (porEstado !== 0) return porEstado;
        const nombreA = `${a.estudiante?.usuario?.apellido || ''} ${a.estudiante?.usuario?.nombre || ''}`;
        const nombreB = `${b.estudiante?.usuario?.apellido || ''} ${b.estudiante?.usuario?.nombre || ''}`;
        return nombreA.localeCompare(nombreB, 'es');
      });
  }

  expedienteTerminal(estado?: string | null): boolean {
    return ['completado', 'reprobado', 'retirado']
      .includes(String(estado || '').trim().toLowerCase());
  }

  abrirComunicaciones(practica: Practica): void {
    if (practica.id) this.comentarioPractica.set(practica);
  }

  cerrarComunicaciones(): void {
    this.comentarioPractica.set(null);
  }

  enEtapaPracticas(): boolean {
    const etapa = this.etapaEstudiante();
    return etapa === 'Práctica I' || etapa === 'Práctica II';
  }

  puedeIniciarPostulacion(): boolean {
    return this.documentosCompletos()
      && this.postulacionHabilitada()
      && !this.actividadActiva()
      && !this.miPostulacionActiva()
      && this.enEtapaPracticas();
  }

  vacanteSeleccionada(vacanteId: number | null): Vacante | undefined {
    if (vacanteId == null) return undefined;
    return this.vacantes().find(v => v.id === Number(vacanteId));
  }

  preferenciasSeleccionadas(): Vacante[] {
    return [this.pref1Id, this.pref2Id, this.pref3Id]
      .map(id => this.vacanteSeleccionada(id))
      .filter((vac): vac is Vacante => !!vac);
  }

  preferenciasRepetidas(): boolean {
    const ids = [this.pref1Id, this.pref2Id, this.pref3Id]
      .filter((id): id is number => id != null)
      .map(id => Number(id));
    return new Set(ids).size !== ids.length;
  }

  opcionPreferenciaBloqueada(vac: Vacante, valorActual: number | null): boolean {
    if (vac.id == null) return true;
    const id = Number(vac.id);
    const actual = valorActual == null ? null : Number(valorActual);
    return id !== actual && [this.pref1Id, this.pref2Id, this.pref3Id]
      .filter(selected => selected != null)
      .map(selected => Number(selected))
      .includes(id);
  }

  seleccionarVacanteParaPreferencias(vac: Vacante): void {
    if (!vac.id) return;
    const id = Number(vac.id);
    if ([this.pref1Id, this.pref2Id, this.pref3Id].map(v => v == null ? null : Number(v)).includes(id)) {
      this.activeTab.set('preferencias_merit');
      return;
    }
    if (!this.pref1Id) {
      this.pref1Id = id;
    } else if (!this.pref2Id) {
      this.pref2Id = id;
    } else if (!this.pref3Id) {
      this.pref3Id = id;
    }
    this.activeTab.set('preferencias_merit');
  }

  quitarPreferencia(orden: 1 | 2 | 3): void {
    if (orden === 1) this.pref1Id = null;
    if (orden === 2) this.pref2Id = null;
    if (orden === 3) this.pref3Id = null;
  }

  limpiarPreferencias(): void {
    this.pref1Id = null;
    this.pref2Id = null;
    this.pref3Id = null;
  }

  // Practicas del estudiante ordenadas con la activa primero, para que no
  // se confunda con el historial de una practica ya completada (I vs II).
  misPracticasOrdenadas(): Practica[] {
    return [...this.practicas()].sort((a, b) => {
      const activaA = a.estado === 'en_curso' || a.estado === 'pendiente' ? 0 : 1;
      const activaB = b.estado === 'en_curso' || b.estado === 'pendiente' ? 0 : 1;
      return activaA - activaB;
    });
  }

  // Solo las bitacoras de la practica ACTIVA: un estudiante puede tener
  // varias practicas (I y II) y cada una lleva su propio historial.
  misBitacoras(): Bitacora[] {
    const est = this.estudianteActual();
    if (!est) return [];
    const activa = this.practicas().find(p =>
      p.estudiante.id === est.id && (p.estado === 'en_curso' || p.estado === 'pendiente'));
    if (!activa) return [];
    return this.bitacoras().filter(b => b.estudiante?.id === est.id && b.practica?.id === activa.id);
  }

  // Bitacoras de los estudiantes visibles para el rol actual
  // (para TUTOR, practicas() ya viene filtrado a sus asignados)
  bitacorasDeTutorados(): Bitacora[] {
    const practicasPeriodo = this.practicasDelPeriodo();
    const practicaIds = new Set(practicasPeriodo.map(p => p.id));
    const estudianteIds = new Set(practicasPeriodo.map(p => p.estudiante.id));
    // Respaldo por estudiante para bitácoras antiguas sin referencia al expediente
    return this.bitacoras().filter(b => b.practica?.id != null
      ? practicaIds.has(b.practica.id)
      : (!b.vinculacion && estudianteIds.has(b.estudiante?.id)));
  }

  carrerasBaseVacante(): string[] {
    return carrerasBaseParaVacante(
      this.userRole(),
      this.carrerasCatalogo(),
      this.authService.carrerasAsignadas()
    );
  }

  carrerasDisponiblesVacante(empresa: Empresa | null | undefined = this.newVacante.empresa): string[] {
    const carreras = this.carrerasBaseVacante();
    if (!empresa?.id) return carreras;
    return carreras.filter(carrera =>
      this.expediente.motivoConvenioInvalido(
        this.convenios(), 'EMPRESA', empresa.id, carrera
      ) === null && this.cuposDisponiblesEmpresaCarrera(empresa, carrera) > 0
    );
  }

  empresaHabilitadaParaVacante(empresa: Empresa): boolean {
    return this.carrerasDisponiblesVacante(empresa).length > 0;
  }

  motivoEmpresaVacante(empresa: Empresa): string | null {
    const carreras = this.carrerasBaseVacante();
    const conConvenio = carreras.filter(carrera => this.expediente.motivoConvenioInvalido(
      this.convenios(), 'EMPRESA', empresa.id, carrera
    ) === null);
    if (conConvenio.length === 0) return 'sin convenio vigente para tus carreras';
    const oferta = this.ofertaDeEmpresa(empresa);
    if (!oferta || !oferta.activo) return `sin oferta de cupos para ${this.expediente.periodoActual()}`;
    if (!conConvenio.some(carrera => this.cuposDisponiblesEmpresaCarrera(empresa, carrera) > 0)) {
      return 'sin cupos libres para tus carreras';
    }
    return null;
  }

  cuposDisponiblesEmpresaCarrera(
    empresa: Empresa | null | undefined = this.newVacante.empresa,
    carrera = this.newVacante.carrera
  ): number {
    const oferta = this.ofertaDeEmpresa(empresa);
    return cuposDisponiblesOferta(oferta, carrera);
  }

  onCarreraVacanteChange(carrera: string): void {
    this.newVacante.carrera = carrera;
    const disponibles = this.cuposDisponiblesEmpresaCarrera();
    if (disponibles > 0 && this.newVacante.cupos > disponibles) {
      this.newVacante.cupos = disponibles;
    }
  }

  // --- Fase 43: Paginación ---
  cargarPaginaLista(): void {
    if (!['ADMIN', 'COORDINADOR'].includes(this.userRole())) return;
    this.cargandoLista.set(true);
    const filtros: any = {};
    if (this.searchText()) filtros.texto = this.searchText();
    if (this.filtroEstado() !== 'TODOS') filtros.estado = this.filtroEstado();
    
    this.practicaService.getPaginado(this.paginaLista(), this.tamanoPaginaLista(), filtros).subscribe({
      next: pag => {
        this.practicas.set(pag.contenido);
        this.cargarTutoresParaPracticas(pag.contenido);
        this.totalElementosLista.set(pag.totalElementos);
        this.totalPaginasLista.set(pag.totalPaginas);
        this.cargandoLista.set(false);
      },
      error: () => this.cargandoLista.set(false)
    });
  }

  cambioPaginaLista(pag: number): void {
    this.paginaLista.set(pag);
    this.cargarPaginaLista();
  }

  cargarPaginaSeguimiento(): void {
    if (!['ADMIN', 'COORDINADOR'].includes(this.userRole())) return;
    this.cargandoSeguimiento.set(true);
    const filtros: any = {};
    if (this.searchText()) filtros.texto = this.searchText();
    if (this.filtroEstado() !== 'TODOS') filtros.estado = this.filtroEstado();
    if (this.filtroSeguimientoPeriodo().trim()) filtros.periodoAcademico = this.filtroSeguimientoPeriodo().trim();
    if (this.filtroSeguimientoCarrera() !== 'TODOS') filtros.carrera = this.filtroSeguimientoCarrera();
    if (this.filtroSeguimientoTutor() !== 'TODOS') filtros.tutorId = this.filtroSeguimientoTutor();
    // Aún no hay filtro nativo de DB para RIESGO, así que se trae por paginación estándar.

    this.practicaService.getSeguimientoPaginado(this.paginaSeguimiento(), this.tamanoPaginaSeguimiento(), filtros).subscribe({
      next: pag => {
        this.seguimiento.set(pag.contenido);
        this.totalElementosSeguimiento.set(pag.totalElementos);
        this.totalPaginasSeguimiento.set(pag.totalPaginas);

        if (pag.contenido.length > 0 && this.activeTab() === 'calificaciones_adm') {
          const obs = pag.contenido.map(s => this.evaluacionService.getByPractica(s.practicaId));
          forkJoin(obs).subscribe(evals => {
            this.evaluaciones.set(evals.flat());
            this.cargandoSeguimiento.set(false);
          });
        } else {
          this.cargandoSeguimiento.set(false);
        }
      },
      error: () => this.cargandoSeguimiento.set(false)
    });
  }

  cambioPaginaSeguimiento(pag: number): void {
    this.paginaSeguimiento.set(pag);
    this.cargarPaginaSeguimiento();
  }

  private filtroSeguimientoDebounce?: ReturnType<typeof setTimeout>;

  filtrarSeguimiento(): void {
    if (this.filtroSeguimientoDebounce) clearTimeout(this.filtroSeguimientoDebounce);
    this.filtroSeguimientoDebounce = setTimeout(() => {
      this.paginaSeguimiento.set(0);
      this.cargarPaginaSeguimiento();
    }, 400);
  }

  // El seguimiento paginado es un DTO plano (solo trae practicaId); para el
  // modal de calificación o seguimiento se resuelve la práctica completa.
  abriendoExpedienteId = signal<number | null>(null);

  calificarDesdeSeguimiento(seg: SeguimientoPractica, parcial: number): void {
    if (this.abriendoExpedienteId() !== null) return;
    this.abriendoExpedienteId.set(seg.practicaId);
    this.practicaService.getById(seg.practicaId).pipe(
      finalize(() => this.abriendoExpedienteId.set(null))
    ).subscribe(prac => this.openGradingModal(prac, parcial));
  }

  verSeguimientoDe(seg: SeguimientoPractica): void {
    if (this.abriendoExpedienteId() !== null) return;
    this.abriendoExpedienteId.set(seg.practicaId);
    this.practicaService.getById(seg.practicaId).pipe(
      finalize(() => this.abriendoExpedienteId.set(null))
    ).subscribe(prac => this.openSeguimientoModal(prac));
  }

  busquedaLista(event?: Event): void {
    event?.preventDefault();
    this.paginaLista.set(0);
    this.cargarPaginaLista();
    
    this.paginaSeguimiento.set(0);
    this.cargarPaginaSeguimiento();
  }

  // Escape hatch de un clic: por defecto solo se ven expedientes EN_CURSO del
  // periodo actual (evita que se acumule historial en la bandeja diaria).
  verHistorialCompleto(): void {
    this.filtroEstado.set('TODOS');
    this.filtroSeguimientoPeriodo.set('');
    this.busquedaLista();
  }

  private ofertaDeEmpresa(empresa: Empresa | null | undefined): OfertaCuposEmpresa | undefined {
    if (!empresa?.id) return undefined;
    return this.ofertasCupos().find(oferta => oferta.empresa?.id === empresa.id);
  }

  onEmpresaVacanteChange(empresa: Empresa | null): void {
    this.newVacante.empresa = empresa as Empresa;
    const carreras = this.carrerasDisponiblesVacante(empresa);
    if (!carreras.includes(this.newVacante.carrera)) {
      this.newVacante.carrera = carreras[0] || '';
    }
    const disponibles = this.cuposDisponiblesEmpresaCarrera(empresa, this.newVacante.carrera);
    if (disponibles > 0 && this.newVacante.cupos > disponibles) {
      this.newVacante.cupos = disponibles;
    }
  }

  // Create vacancy
  openVacanteModal(): void {
    const carreras = this.carrerasBaseVacante();
    this.vacanteEnEdicionId = null;
    this.newVacante = {
      ...this.emptyVacante(),
      cupos: 3,
      carrera: carreras[0] || ''
    };
    this.showVacanteModal.set(true);
  }

  // Fase 42: edición de una vacante publicada (empresa y periodo inmutables)
  vacanteEnEdicionId: number | null = null;
  guardandoVacante = signal(false);
  enviandoPostulacion = signal(false);
  pausandoVacanteId = signal<number | null>(null);

  openEditVacanteModal(vac: Vacante): void {
    this.vacanteEnEdicionId = vac.id ?? null;
    this.newVacante = { ...vac, empresa: { ...vac.empresa } };
    this.showVacanteModal.set(true);
  }

  // Fase 42: duplica descripción y condiciones de la vacante, nunca sus cupos
  duplicarVacante(vac: Vacante): void {
    this.vacanteEnEdicionId = null;
    this.newVacante = {
      ...vac,
      id: undefined,
      empresa: { ...vac.empresa },
      nombre: `${vac.nombre} (copia)`,
      cupos: 1,
      activa: true,
      fechaLimite: this.fechaLimiteDefault(),
      periodoAcademico: this.expediente.periodoActual()
    };
    this.showVacanteModal.set(true);
  }

  // Fase 42: pausar oculta la vacante a estudiantes sin devolver cupos
  async togglePausaVacante(vac: Vacante): Promise<void> {
    if (!vac.id || this.pausandoVacanteId() !== null) return;
    const pausar = vac.activa !== false;
    const mensaje = pausar
      ? `¿Pausar la vacante "${vac.nombre}"? Los estudiantes dejarán de verla y no se admitirán postulaciones ni asignaciones; los cupos reservados se conservan.`
      : `¿Reactivar la vacante "${vac.nombre}"? Volverá a aceptar postulaciones.`;
    if (!(await this.confirmService.confirm(mensaje))) return;
    this.pausandoVacanteId.set(vac.id);
    const request = pausar
      ? this.vacanteService.pausar(vac.id)
      : this.vacanteService.reactivar(vac.id);
    request.subscribe({
      next: (actualizada) => {
        this.pausandoVacanteId.set(null);
        this.vacantes.set(this.vacantes().map(v =>
          v.id === actualizada.id ? { ...v, activa: actualizada.activa } : v));
        this.toastService.success(pausar ? 'Vacante pausada.' : 'Vacante reactivada.');
      },
      error: (err) => {
        this.pausandoVacanteId.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo cambiar el estado de la vacante.');
      }
    });
  }

  // Máximo de cupos: al editar se reintegra la reserva actual de la vacante
  maxCuposVacante(): number {
    const disponibles = this.cuposDisponiblesEmpresaCarrera();
    if (this.vacanteEnEdicionId == null) return disponibles;
    const original = this.vacantes().find(v => v.id === this.vacanteEnEdicionId);
    return disponibles + Number(original?.cupos || 0);
  }

  saveVacante(): void {
    if (this.guardandoVacante()) return;
    if (!this.newVacante.nombre || !this.newVacante.empresa || !this.newVacante.carrera
      || !this.newVacante.area || !this.newVacante.descripcion || !this.newVacante.perfilRequerido
      || !this.newVacante.fechaLimite) {
      this.toastService.warning('Completa empresa, carrera, campo profesional, actividades, perfil y fecha límite.');
      return;
    }
    if (Number(this.newVacante.cupos) <= 0) {
      this.toastService.warning('La vacante debe publicar al menos un cupo.');
      return;
    }
    if (Number(this.newVacante.horas) <= 0) {
      this.toastService.warning('Las horas requeridas de la vacante deben ser mayores a cero.');
      return;
    }
    const editando = this.vacanteEnEdicionId != null;
    if (!editando && !this.carrerasDisponiblesVacante().includes(this.newVacante.carrera)) {
      this.toastService.warning('La empresa no tiene convenio y cupos libres para la carrera seleccionada.');
      return;
    }
    const maximo = this.maxCuposVacante();
    if (Number(this.newVacante.cupos) > maximo) {
      this.toastService.warning(`Solo quedan ${maximo} cupo(s) libres en la oferta de la empresa.`);
      return;
    }
    if (!editando) {
      this.newVacante.periodoAcademico = this.expediente.periodoActual();
    }
    this.guardandoVacante.set(true);
    const request = editando
      ? this.vacanteService.update(this.vacanteEnEdicionId!, this.newVacante)
      : this.vacanteService.create(this.newVacante);
    request.subscribe({
      next: (guardada) => {
        // El backend devuelve la empresa solo con id; conservar el objeto completo
        const completa = { ...guardada, empresa: this.newVacante.empresa };
        this.vacantes.set(editando
          ? this.vacantes().map(v => v.id === completa.id ? completa : v)
          : [...this.vacantes(), completa]);
        const finalizarGuardado = (): void => {
          this.guardandoVacante.set(false);
          this.showVacanteModal.set(false);
          this.vacanteEnEdicionId = null;
          this.toastService.success(editando
            ? 'Vacante actualizada correctamente.'
            : 'Vacante publicada correctamente.');
        };
        this.ofertaCuposService.clearCache();
        this.ofertaCuposService.getAll(this.expediente.periodoActual()).subscribe({
          next: ofertas => {
            this.ofertasCupos.set(ofertas);
            finalizarGuardado();
          },
          error: () => {
            finalizarGuardado();
            this.toastService.warning(
              'La vacante se guardó, pero no se pudo actualizar el resumen de cupos. Usa Actualizar antes de crear otra.');
          }
        });
      },
      error: (err) => {
        this.guardandoVacante.set(false);
        this.toastService.error(err?.error?.error
          || (editando ? 'No se pudo actualizar la vacante.' : 'No se pudo publicar la vacante.'));
      }
    });
  }

  // Favorites management (persistido en FAVORITOS_VACANTES)
  toggleFavorita(id?: number): void {
    if (id == null) return;
    this.favoritoService.toggle(id).subscribe({
      next: ids => this.favoritasList.set(ids),
      error: () => this.toastService.error('No se pudo actualizar la vacante favorita.')
    });
  }

  isFavorita(id?: number): boolean {
    return id != null && this.favoritasList().includes(id);
  }

  // View vacancy details modal
  verDetalleVacante(vac: Vacante): void {
    this.selectedDetailVacante.set(vac);
    this.showDetailModal.set(true);
  }

  private aplicarDocumentos(documentos: DocEstudiante[]): void {
    this.documentosDetalle.set(documentos);
    const aprobado = (tipo: TipoDocumento) => this.documentoVigente(tipo)?.estado === 'aprobado';
    this.cvCargado.set(aprobado('cv'));
    this.cartaCargada.set(aprobado('carta'));
    this.cedulaCargada.set(aprobado('cedula'));
    this.cartaAceptacionCargada.set(aprobado('carta_aceptacion'));
  }

  // La carta de solicitud y la carta de aceptacion se guardan bajo el mismo
  // tipoDocumento en cada practica (I y II); si el documento encontrado
  // quedo etiquetado con una etapa distinta a la actual, se trata como no
  // subido para esta etapa (el estudiante debe volver a cargarlo).
  private documentoVigente(type: TipoDocumento): DocEstudiante | undefined {
    const doc = this.documentosDetalle().find(item => item.tipoDocumento === type);
    if (!doc) return undefined;
    const requiereEtapaVigente = type === 'carta' || type === 'carta_aceptacion';
    if (requiereEtapaVigente && doc.etapa && doc.etapa !== this.etapaEstudianteCodigo()) {
      return undefined;
    }
    return doc;
  }

  documentoTieneArchivo(type: TipoDocumento): boolean {
    return !!this.documentoVigente(type)?.urlArchivo;
  }

  estadoDocumento(type: TipoDocumento): string {
    return this.documentoVigente(type)?.estado || 'pendiente';
  }

  observacionDocumento(type: TipoDocumento): string {
    return this.documentoVigente(type)?.observacion || '';
  }

  estadoDocumentoTexto(type: TipoDocumento): string {
    const estado = this.estadoDocumento(type);
    const labels: Record<string, string> = {
      pendiente: 'Pendiente de archivo',
      cargado: 'Cargado, pendiente de revisión',
      en_revision: 'En revisión',
      aprobado: 'Aprobado',
      rechazado: 'Rechazado',
      requiere_correccion: 'Requiere corrección'
    };
    return labels[estado] || estado;
  }

  estadoDocumentoClase(type: TipoDocumento): string {
    const estado = this.estadoDocumento(type);
    return this.estadoRevisionClase(estado);
  }

  estadoRevisionClase(estado?: string): string {
    if (estado === 'aprobado') return 'text-green-700 bg-green-50 border-green-200';
    if (estado === 'requiere_correccion' || estado === 'rechazado') return 'text-rose-700 bg-rose-50 border-rose-200';
    return 'text-amber-700 bg-amber-50 border-amber-200';
  }

  verDocumento(type: TipoDocumento): void {
    const doc = this.documentoVigente(type);
    if (!doc?.id || !doc.urlArchivo) {
      this.toastService.warning('Primero sube el archivo del documento.');
      return;
    }
    this.documentoService.getMiArchivo(doc.id).subscribe({
      next: respuesta => window.open(respuesta.url, '_blank', 'noopener'),
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo abrir el archivo.')
    });
  }

  // Student Document Upload (archivo real en Supabase Storage privado)
  uploadDoc(type: TipoDocumento, event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!this.enEtapaPracticas()) {
      input.value = '';
      this.activeTab.set('mi_progreso');
      this.toastService.warning('Tu proceso académico actual es Vinculación. Solo puedes cargar documentos en ese módulo.');
      return;
    }
    const file = input.files?.[0];
    if (!file) return;

    if (this.subiendoDocumento()) {
      input.value = '';
      this.toastService.info('Espera a que termine la carga del archivo actual.');
      return;
    }

    this.subiendoDocumento.set(type);
    this.toastService.info(`Subiendo ${file.name}. Por favor espera...`);
    this.documentoService.subir(type, 'PRACTICAS').pipe(
      switchMap(() => this.documentoService.getMisDocumentosDetalle('PRACTICAS')),
      switchMap(documentos => {
        const doc = documentos.find(item => item.tipoDocumento === type);
        if (!doc?.id) throw new Error('No se pudo preparar el documento para subir el archivo.');
        return this.documentoService.subirArchivo(doc.id, file).pipe(
          map(guardado => ({ documentos, guardado }))
        );
      }),
      finalize(() => {
        input.value = '';
        this.subiendoDocumento.set(null);
      })
    ).subscribe({
      next: ({ documentos, guardado }) => {
        const actualizados = documentos.map(item => item.id === guardado.id ? guardado : item);
        this.aplicarDocumentos(actualizados);
        this.toastService.success('Archivo cargado correctamente. Queda pendiente de revisión.');

        if (this.cvCargado() && this.cartaCargada() && this.cedulaCargada()) {
          if (this.postulacionHabilitada()) {
            this.activeTab.set('ofertas');
            this.toastService.success('Documentos enviados correctamente. Las vacantes disponibles ya están habilitadas.');
          } else {
            this.activeTab.set('mi_progreso');
            this.toastService.info(`Expediente documental enviado exitosamente. Las vacantes se habilitaran el ${this.fechaInicioPostulacion}. Revisa tu panel de avance para mas informacion.`);
          }
        }
      },
      error: (err) => this.toastService.error(
        err?.error?.error || err?.message || 'No se pudo subir el archivo.'
      )
    });
  }

  carrerasParaRevision(): string[] {
    return [...new Set(this.carrerasCatalogo().map(c => c.nombre).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b));
  }

  nombreEstudianteDocumento(doc: DocEstudiante): string {
    const usuario = doc.estudiante?.usuario;
    const nombre = `${usuario?.nombre || ''} ${usuario?.apellido || ''}`.trim();
    return nombre || usuario?.email || 'Estudiante';
  }

  aprobarDocumento(doc: DocEstudiante): void {
    if (!doc.id) return;
    this.accionDocumentoRevision.set({ id: doc.id, tipo: 'aprobar' });
    this.documentoService.revisar(doc.id, 'aprobado').pipe(
      finalize(() => this.accionDocumentoRevision.set(null))
    ).subscribe({
      next: () => {
        this.toastService.success('Documento aprobado.');
        this.loadDocumentosRevision();
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo aprobar el documento.')
    });
  }

  solicitarCorreccionDocumento(doc: DocEstudiante): void {
    if (!doc.id) return;
    const observacion = (this.observacionRevision[doc.id] || '').trim();
    if (!observacion) {
      this.toastService.warning('Ingresa una observación para solicitar corrección.');
      return;
    }
    this.accionDocumentoRevision.set({ id: doc.id, tipo: 'corregir' });
    this.documentoService.revisar(doc.id, 'requiere_correccion', observacion).pipe(
      finalize(() => this.accionDocumentoRevision.set(null))
    ).subscribe({
      next: () => {
        this.toastService.success('Corrección solicitada al estudiante.');
        this.observacionRevision[doc.id!] = '';
        this.loadDocumentosRevision();
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo solicitar la corrección.')
    });
  }

  abrirDocumentoRevision(doc: DocEstudiante): void {
    if (!doc.id) return;
    this.accionDocumentoRevision.set({ id: doc.id, tipo: 'ver' });
    this.documentoService.getArchivoRevision(doc.id).pipe(
      finalize(() => this.accionDocumentoRevision.set(null))
    ).subscribe({
      next: respuesta => window.open(respuesta.url, '_blank', 'noopener'),
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo abrir el archivo.')
    });
  }

  // Student postulation with Priority 1, 2, 3
  postularPreferencias(): void {
    if (this.enviandoPostulacion()) return;
    if (!this.documentosCompletos()) {
      this.toastService.error('Debes subir toda la documentación obligatoria (CV, Carta de solicitud, Copia de cédula) antes de postularte.');
      return;
    }

    if (!this.pref1Id || !this.pref2Id) {
      this.toastService.error('Debes seleccionar obligatoriamente al menos dos opciones de preferencia (Opción 1 y Opción 2) según las políticas de meritocracia.');
      return;
    }

    if (this.preferenciasRepetidas()) {
      this.toastService.error('No puedes repetir vacantes en tus preferencias.');
      return;
    }

    const user = this.authService.currentUser();
    if (!user) return;
    const est = this.estudiantes().find(e => e.usuario?.email === user.email);
    if (!est || !est.id) return;

    // Regla de exclusividad: sin procesos activos simultaneos
    if (this.actividadActiva()) {
      this.toastService.error('Ya posees una práctica o vinculación activa. No puedes cursar múltiples modalidades simultáneamente.');
      return;
    }

    // Sin postulaciones duplicadas en proceso
    if (this.miPostulacionActiva()) {
      this.toastService.error('Ya tienes una postulación en proceso. Espera a que el coordinador la procese antes de postular nuevamente.');
      return;
    }

    const p1 = this.vacantes().find(v => v.id === Number(this.pref1Id));
    const p2 = this.vacantes().find(v => v.id === Number(this.pref2Id));
    const p3 = this.pref3Id ? this.vacantes().find(v => v.id === Number(this.pref3Id)) : undefined;

    if (!p1 || !p2) return;

    const newPost: PostulacionMeritocratica = {
      estudiante: est,
      pref1: p1,
      pref2: p2,
      pref3: p3,
      estado: 'Pendiente',
      ajustadoManualmente: false
    };

    this.enviandoPostulacion.set(true);
    this.postulacionService.create(newPost).subscribe({
      next: (creada) => {
        this.enviandoPostulacion.set(false);
        this.postulaciones.set([...this.postulaciones(), creada]);
        this.pref1Id = null;
        this.pref2Id = null;
        this.pref3Id = null;
        this.activeTab.set('mi_progreso');
        this.toastService.success('Postulación meritocrática por orden de prioridades registrada con éxito.');
      },
      error: (err) => {
        this.enviandoPostulacion.set(false);
        this.toastService.error(this.errorMsg(err, 'No se pudo registrar la postulación.'));
      }
    });
  }

  // Coordinator: Calculate meritocracy auto assignment algorithm
  calculandoAsignacion = signal(false);

  calcularAsignacionAutomatica(): void {
    if (this.calculandoAsignacion()) return;
    this.calculandoAsignacion.set(true);
    this.postulacionService.procesarMeritocracia().subscribe({
      next: (procesadas) => {
        this.calculandoAsignacion.set(false);
        this.postulaciones.set(this.postulaciones().map(post =>
          procesadas.find(p => p.id === post.id) ?? post
        ));
        this.cargarTutoresMeritocracia();
        this.loadData();
        this.toastService.success('Algoritmo de meritocracia ejecutado. Propuesta de pre-asignación lista.');
      },
      error: (err) => {
        this.calculandoAsignacion.set(false);
        this.toastService.error(this.errorMsg(err, 'Error al procesar las postulaciones.'));
      }
    });
  }

  // Coordinator: Open manual adjustment modal
  openAjusteModal(post: PostulacionMeritocratica): void {
    this.selectedPostForAjuste.set(post);
    this.ajusteEmpresaId = null;
    this.ajusteJustificacion = '';
    this.showAjusteModal.set(true);
  }

  guardandoAjuste = signal(false);

  saveManualAdjustment(): void {
    if (this.guardandoAjuste()) return;
    const post = this.selectedPostForAjuste();
    const vacId = this.ajusteEmpresaId;
    const just = this.ajusteJustificacion;

    if (!post || !vacId || !just) {
      this.toastService.warning('Ingresa la empresa y la justificación requerida.');
      return;
    }

    const targetVac = this.vacantes().find(v => v.id === Number(vacId));
    if (!targetVac || !post.id) return;

    const ajustada: PostulacionMeritocratica = {
      ...post,
      estado: 'Procesado',
      ajustadoManualmente: true,
      asignadoEmpresa: targetVac.empresa,
      asignadoVacante: targetVac,
      justificacionAjuste: just
    };

    this.guardandoAjuste.set(true);
    this.postulacionService.update(post.id, ajustada).subscribe({
      next: (actualizada) => {
        this.guardandoAjuste.set(false);
        this.postulaciones.set(this.postulaciones().map(p => p.id === post.id ? actualizada : p));
        this.cargarTutoresMeritocracia();
        this.loadAuditLogs();
        this.showAjusteModal.set(false);
        this.toastService.success('Ajuste manual guardado y registrado en la bitácora de auditoría.');
      },
      error: () => {
        this.guardandoAjuste.set(false);
        this.toastService.error('No se pudo guardar el ajuste manual.');
      }
    });
  }

  // Coordinator: Consolidate / Approve final assignments
  aprobandoDefinitivas = signal(false);

  async aprobarAsignacionesDefinitivas(): Promise<void> {
    if (this.aprobandoDefinitivas()) return;
    const list = this.getFilteredPostulaciones().filter(p => p.estado === 'Procesado');
    if (list.length === 0) {
      this.toastService.warning('No hay propuestas de asignación procesadas por consolidar.');
      return;
    }

    if (list.some(post => !this.postulacionTieneConvenioVigente(post))) {
      this.toastService.warning('Hay propuestas cuya empresa no tiene un convenio vigente para la carrera del estudiante.');
      return;
    }

    const sinTutor = list.find(post => !this.tutorMeritocraciaSeleccionado()[post.id!]);
    if (sinTutor) {
      this.toastService.warning(`Selecciona un tutor externo compatible para ${this.postNombre(sinTutor)}.`);
      return;
    }

    const mensaje = `¿Confirmar la asignación definitiva de ${list.length} postulación(es)? Se descontarán los cupos y se crearán las prácticas oficiales. Esta acción no se puede deshacer: revisa los "Ajuste Manual" antes de continuar si el algoritmo propuso algo incorrecto.`;
    if (!(await this.confirmService.confirm(mensaje))) return;

    this.aprobandoDefinitivas.set(true);
    const consolidaciones = list
      .map(post => ({ post, vacante: this.vacanteAsignada(post) }))
      .filter(item => item.vacante)
      .map(({ post, vacante }) => {
        const vinculo = this.tutoresMeritocracia()[post.id!]
          ?.find(item => item.tutorId === Number(this.tutorMeritocraciaSeleccionado()[post.id!]));
        const tutor = vinculo ? this.usuarioDesdeVinculo(vinculo) : undefined;
        return tutor ? this.postulacionService.consolidarPractica(post.id!, {
          estudiante: post.estudiante,
          empresa: vacante!.empresa,
          vacante: vacante!,
          tutor,
          estado: 'en_curso',
          horasRequeridas: vacante!.horas,
          periodoAcademico: this.expediente.periodoActual()
        } as Practica) : null;
      })
      .filter(item => item !== null);

    if (consolidaciones.length !== list.length) {
      this.aprobandoDefinitivas.set(false);
      this.toastService.error('Hay postulaciones sin vacante asignada exacta. Revisa las asignaciones antes de consolidar.');
      return;
    }

    forkJoin(consolidaciones).subscribe({
      next: () => {
        this.aprobandoDefinitivas.set(false);
        this.practicaService.clearCache();
        this.loadData();
        if (this.activeTab() === 'lista') this.cargarPaginaLista();
        this.toastService.success('Proceso de meritocracia consolidado. Se han generado los expedientes oficiales.');
      },
      error: () => {
        this.aprobandoDefinitivas.set(false);
        this.toastService.error('Error al consolidar las asignaciones definitivas.');
      }
    });
  }

  // Direct Assignment
  prepararContinuidad(practica: Practica): void {
    if (!practica.id || !this.puedeContinuar(practica)) return;
    const empresaId = practica.empresa?.id;
    const carrera = String(practica.estudiante?.carrera || '').trim().toLowerCase();
    const periodo = this.expediente.periodoActual();
    const vacante = this.vacantes().find(item => item.activa !== false
      && Number(item.cupos || 0) > 0
      && item.empresa?.id === empresaId
      && String(item.carrera || '').trim().toLowerCase() === carrera
      && item.modalidadAcademica === 'Práctica II'
      && item.periodoAcademico === periodo);
    if (!vacante) {
      this.toastService.warning('Primero registra una vacante activa de Práctica II para la misma empresa, carrera y periodo.');
      return;
    }
    this.continuidadOrigenId.set(practica.id);
    this.selectedEstudianteId = practica.estudiante.id || null;
    this.selectedEstudianteObj = practica.estudiante;
    this.selectedVacanteId = vacante.id || null;
    this.selectedVacanteObj = vacante;
    this.cargarTutoresDirecta(practica.tutor?.id);
    this.activeTab.set('directa');
    this.toastService.success('Continuidad preparada. Revisa la vacante y confirma el tutor antes de registrar Práctica II.');
  }

  cancelarContinuidad(): void {
    this.continuidadOrigenId.set(null);
  }

  seleccionarEstudianteDirecto(estudiante: Estudiante | null): void {
    this.selectedEstudianteObj = estudiante;
    this.cargarTutoresDirecta();
  }

  seleccionarVacanteDirecta(vacante: Vacante | null): void {
    this.selectedVacanteObj = vacante;
    this.cargarTutoresDirecta();
  }

  puedeContinuar(practica: Practica): boolean {
    return String(practica.estado || '').toLowerCase() === 'completado'
      && practica.vacante?.modalidadAcademica === 'Práctica I';
  }

  saveDirectAssignment(): void {
    if (!this.selectedEstudianteId || !this.selectedVacanteId || !this.selectedTutorId()) {
      this.toastService.warning('Por favor selecciona estudiante, vacante y tutor académico.');
      return;
    }
    const est = this.selectedEstudianteObj || this.estudiantes().find(e => e.id === Number(this.selectedEstudianteId));
    const vac = this.selectedVacanteObj || this.vacantes().find(v => v.id === Number(this.selectedVacanteId));
    const vinculoTutor = this.tutoresCompatiblesDirecta()
      .find(item => item.tutorId === Number(this.selectedTutorId()));
    const tutor = vinculoTutor ? this.usuarioDesdeVinculo(vinculoTutor) : undefined;

    if (!est || !vac || !tutor) return;
    if (!this.vacanteTieneConvenioVigente(vac, est.carrera)) {
      this.toastService.warning('La empresa seleccionada no tiene un convenio vigente para la carrera del estudiante.');
      return;
    }

    const newPrac: Practica = {
      estudiante: est,
      empresa: vac.empresa,
      vacante: vac,
      tutor: tutor,
      estado: 'en_curso',
      horasRequeridas: vac.horas,
      horasCompletadas: 0,
      periodoAcademico: this.expediente.periodoActual(),
      tipoAsignacion: this.continuidadOrigenId() ? 'CONTINUIDAD' : 'DIRECTA',
      practicaOrigenId: this.continuidadOrigenId()
    };

    this.practicaService.create(newPrac).subscribe({
      next: () => {
        this.loadData();
        this.selectedEstudianteId = null;
        this.selectedVacanteId = null;
        this.selectedTutorId.set(null);
        this.tutoresCompatiblesDirecta.set([]);
        const continuidad = this.continuidadOrigenId() !== null;
        this.continuidadOrigenId.set(null);
        this.toastService.success(continuidad
          ? 'Continuidad en Práctica II registrada con trazabilidad.'
          : 'Asignación directa registrada con éxito.');
      },
      error: (err) => this.toastService.error(this.errorMsg(err, 'No se pudo registrar la asignación directa.'))
    });
  }

  convenioSeleccionadoVigente(): boolean {
    const vacante = this.selectedVacanteObj || this.vacantes().find(v => v.id === Number(this.selectedVacanteId));
    const estudiante = this.selectedEstudianteObj || this.estudiantes().find(e => e.id === Number(this.selectedEstudianteId));
    if (!vacante) return true;
    return this.vacanteTieneConvenioVigente(vacante, estudiante?.carrera);
  }

  vacanteDirectaSeleccionada(): Vacante | undefined {
    return this.selectedVacanteObj || this.vacantes().find(vacante => vacante.id === Number(this.selectedVacanteId));
  }

  postulacionTieneConvenioVigente(post: PostulacionMeritocratica): boolean {
    return this.motivoConvenioPostulacion(post) === null;
  }

  // Motivo preciso para la insignia de meritocracia. Usa la empresa asignada
  // (persistida) y no exige resolver la vacante transitoria tras un refresco.
  motivoConvenioPostulacion(post: PostulacionMeritocratica): string | null {
    const empresaId = post.asignadoEmpresa?.id ?? this.vacanteAsignada(post)?.empresa?.id;
    return this.expediente.motivoConvenioInvalido(
      this.convenios(), 'EMPRESA', empresaId, post.estudiante?.carrera);
  }

  private vacanteTieneConvenioVigente(vacante: Vacante, carrera?: string): boolean {
    return this.expediente.motivoConvenioInvalido(
      this.convenios(), 'EMPRESA', vacante.empresa?.id, carrera) === null;
  }

  private vacanteAsignada(post: PostulacionMeritocratica): Vacante | undefined {
    if (post.asignadoVacante?.id) {
      return post.asignadoVacante;
    }
    const empresaId = post.asignadoEmpresa?.id;
    if (!empresaId) return undefined;
    const candidatas = [post.pref1, post.pref2, post.pref3]
      .filter((vac): vac is Vacante => !!vac && vac.empresa?.id === empresaId);
    return candidatas.length === 1 ? candidatas[0] : undefined;
  }

  private cargarTutoresDirecta(tutorPreferidoId?: number): void {
    const estudiante = this.selectedEstudianteObj
      || this.estudiantes().find(item => item.id === Number(this.selectedEstudianteId));
    const vacante = this.selectedVacanteObj
      || this.vacantes().find(item => item.id === Number(this.selectedVacanteId));
    const empresaId = vacante?.empresa?.id;
    const carrera = estudiante?.carrera || vacante?.carrera;

    this.selectedTutorId.set(null);
    this.tutoresCompatiblesDirecta.set([]);
    if (!empresaId || !carrera) return;

    this.cargandoTutoresDirecta.set(true);
    this.tutorEmpresaService.getCompatibles(empresaId, carrera).pipe(
      finalize(() => this.cargandoTutoresDirecta.set(false))
    ).subscribe({
      next: tutores => {
        this.tutoresCompatiblesDirecta.set(tutores);
        if (tutorPreferidoId && tutores.some(item => item.tutorId === tutorPreferidoId)) {
          this.selectedTutorId.set(tutorPreferidoId);
        }
      },
      error: () => this.toastService.error('No se pudieron consultar los tutores compatibles con la empresa y la carrera.')
    });
  }

  private cargarTutoresMeritocracia(): void {
    const procesadas = this.getFilteredPostulaciones()
      .filter(post => post.id && post.estado === 'Procesado')
      .map(post => ({ post, vacante: this.vacanteAsignada(post) }))
      .filter(item => item.vacante?.empresa?.id && item.post.estudiante?.carrera);

    if (procesadas.length === 0) {
      this.tutoresMeritocracia.set({});
      this.tutorMeritocraciaSeleccionado.set({});
      return;
    }

    forkJoin(procesadas.map(({ post, vacante }) =>
      this.tutorEmpresaService.getCompatibles(vacante!.empresa.id!, post.estudiante.carrera).pipe(
        map(tutores => ({ postulacionId: post.id!, tutores })),
        catchError(() => of({ postulacionId: post.id!, tutores: [] as TutorEmpresa[] }))
      )
    )).subscribe(resultados => {
      const anteriores = this.tutorMeritocraciaSeleccionado();
      const porPostulacion: Record<number, TutorEmpresa[]> = {};
      const seleccionados: Record<number, number | null> = {};
      resultados.forEach(resultado => {
        porPostulacion[resultado.postulacionId] = resultado.tutores;
        const anterior = anteriores[resultado.postulacionId];
        seleccionados[resultado.postulacionId] = resultado.tutores.some(item => item.tutorId === anterior)
          ? anterior
          : null;
      });
      this.tutoresMeritocracia.set(porPostulacion);
      this.tutorMeritocraciaSeleccionado.set(seleccionados);
    });
  }

  seleccionarTutorMeritocracia(postulacionId: number, tutorId: number | null): void {
    this.tutorMeritocraciaSeleccionado.update(actual => ({
      ...actual,
      [postulacionId]: tutorId == null ? null : Number(tutorId)
    }));
  }

  private cargarTutoresParaPracticas(practicas: Practica[]): void {
    const configurables = practicas.filter(practica =>
      practica.id && practica.empresa?.id && practica.estudiante?.carrera);
    if (configurables.length === 0) {
      this.tutoresCompatiblesPractica.set({});
      return;
    }

    forkJoin(configurables.map(practica =>
      this.tutorEmpresaService.getCompatibles(practica.empresa.id!, practica.estudiante.carrera).pipe(
        map(tutores => ({ practicaId: practica.id!, tutores })),
        catchError(() => of({ practicaId: practica.id!, tutores: [] as TutorEmpresa[] }))
      )
    )).subscribe(resultados => {
      this.tutoresCompatiblesPractica.set(Object.fromEntries(
        resultados.map(resultado => [resultado.practicaId, resultado.tutores])
      ));
    });
  }

  tutorCompatibleConPractica(practica: Practica): TutorEmpresa[] {
    return practica.id ? this.tutoresCompatiblesPractica()[practica.id] || [] : [];
  }

  private usuarioDesdeVinculo(vinculo: TutorEmpresa): Usuario | undefined {
    if (!vinculo.tutorId) return undefined;
    return {
      id: vinculo.tutorId,
      nombre: vinculo.nombre,
      apellido: vinculo.apellido,
      email: vinculo.email || ''
    };
  }

  // Tutor: Asistencia — filtro del historial (solo consulta)
  asistenciaPracticaId = signal<number | null>(null);

  seleccionarAsistenciaPractica(valor: string | number | null): void {
    this.asistenciaPracticaId.set(valor ? Number(valor) : null);
  }

  asistenciasHistorialTutor(): Asistencia[] {
    const id = this.asistenciaPracticaId();
    const todas = this.asistenciasDeTutorados();
    return id == null ? todas : todas.filter(asistencia => asistencia.practica?.id === id);
  }

  // Pase de lista: fecha y horario compartidos; cada estudiante parte como
  // "Presente" y el tutor solo ajusta los casos especiales
  paseCabecera = { fecha: fechaLocalISO(), horaIngreso: '08:00', horaSalida: '12:00' };
  guardandoPase = signal(false);
  private filasPase = new Map<number, FilaPase>();

  filaPase(practicaId: number): FilaPase {
    let fila = this.filasPase.get(practicaId);
    if (!fila) {
      fila = nuevaFilaPase();
      this.filasPase.set(practicaId, fila);
    }
    return fila;
  }

  asistenciaExistentePase(practicaId: number): Asistencia | undefined {
    return asistenciaEnFecha(this.asistencias(), this.paseCabecera.fecha, { practicaId });
  }

  filasPorGuardar(): Practica[] {
    return this.practicasActivasTutor().filter(practica => practica.id != null
      && !this.asistenciaExistentePase(practica.id)
      && this.filaPase(practica.id).estado !== 'OMITIR');
  }

  guardarPaseLista(): void {
    if (!this.paseCabecera.fecha) {
      this.toastService.warning('Ingresa la fecha del pase de lista.');
      return;
    }
    const porGuardar = this.filasPorGuardar();
    if (porGuardar.length === 0) {
      this.toastService.warning('No hay estudiantes por registrar en esa fecha.');
      return;
    }
    this.guardandoPase.set(true);
    const peticiones = porGuardar.map(practica => {
      const fila = this.filaPase(practica.id!);
      return this.asistenciaService.create(asistenciaParaPractica(practica.id!, {
        fecha: this.paseCabecera.fecha,
        horaIngreso: this.paseCabecera.horaIngreso,
        horaSalida: this.paseCabecera.horaSalida,
        estado: fila.estado as Asistencia['estado'],
        observaciones: fila.observaciones
      })).pipe(
        map(creada => ({ creada, error: null as string | null })),
        catchError(err => of({ creada: null as Asistencia | null, error: err?.error?.error || 'No se pudo guardar.' }))
      );
    });
    forkJoin(peticiones).subscribe(resultados => {
      this.guardandoPase.set(false);
      const creadas = resultados.filter(r => r.creada).map(r => r.creada!);
      if (creadas.length > 0) this.asistencias.set([...this.asistencias(), ...creadas]);
      this.filasPase.clear();
      const errores = resultados.filter(r => r.error);
      if (errores.length === 0) {
        this.toastService.success(`Pase de lista guardado: ${creadas.length} registro(s).`);
      } else {
        this.toastService.warning(
          `Se guardaron ${creadas.length} registro(s); fallaron ${errores.length}. ${errores[0].error}`);
      }
    });
  }

  // Student Bitacora Submit
  saveBitacora(): void {
    const user = this.authService.currentUser();
    if (!user) return;
    const est = this.estudiantes().find(e => e.usuario?.email === user.email);
    if (!est || !est.id) return;

    if (!this.newBitacora.fecha || !this.newBitacora.actividad || !this.newBitacora.horas) {
      this.toastService.warning('Completa los campos obligatorios de la bitácora.');
      return;
    }

    const correccion = this.bitacoraEnCorreccion();
    if (correccion?.id) {
      this.guardandoBitacora.set(true);
      this.bitacoraService.reenviar(correccion.id, {
        fecha: this.newBitacora.fecha,
        actividad: this.newBitacora.actividad,
        horas: this.newBitacora.horas,
        observaciones: this.newBitacora.observaciones
      }).pipe(
        finalize(() => this.guardandoBitacora.set(false))
      ).subscribe({
        next: actualizada => {
          this.bitacoras.set(this.bitacoras().map(item =>
            item.id === actualizada.id ? actualizada : item));
          this.cancelarCorreccionBitacora();
          this.toastService.success('Bitácora corregida y reenviada al tutor.');
        },
        error: err => this.toastService.error(this.errorMsg(err, 'No se pudo reenviar la bitácora.'))
      });
      return;
    }

    const newB: Bitacora = {
      estudiante: est,
      practica: this.practicas().find(p => p.estudiante.id === est.id && (p.estado === 'en_curso' || p.estado === 'pendiente')),
      parcial: this.newBitacora.parcial,
      fecha: this.newBitacora.fecha,
      actividad: this.newBitacora.actividad,
      horas: this.newBitacora.horas,
      horasExtra: this.newBitacora.horasExtra || 0,
      observaciones: this.newBitacora.observaciones,
      estado: 'pendiente'
    };

    this.guardandoBitacora.set(true);
    this.bitacoraService.create(newB).pipe(
      finalize(() => this.guardandoBitacora.set(false))
    ).subscribe({
      next: (creada) => {
        this.bitacoras.set([...this.bitacoras(), creada]);
        this.newBitacora = { fecha: '', actividad: '', horas: 4, horasExtra: 0, parcial: 1, observaciones: '' };
        this.toastService.success('Bitácora enviada para aprobación del tutor.');
      },
      error: (err) => this.toastService.error(this.errorMsg(err, 'No se pudo registrar la bitácora.'))
    });
  }

  corregirBitacora(bitacora: Bitacora): void {
    if (!this.correccionPendiente(bitacora)) return;
    this.bitacoraEnCorreccion.set(bitacora);
    this.newBitacora = {
      fecha: bitacora.fecha,
      actividad: bitacora.actividad,
      horas: bitacora.horas,
      horasExtra: bitacora.horasExtra || 0,
      parcial: bitacora.parcial,
      observaciones: bitacora.observaciones || ''
    };
  }

  correccionPendiente(bitacora: Bitacora): boolean {
    return correccionBitacoraPendiente(bitacora, this.misBitacoras());
  }

  cancelarCorreccionBitacora(): void {
    this.bitacoraEnCorreccion.set(null);
    this.newBitacora = { fecha: '', actividad: '', horas: 4, horasExtra: 0, parcial: 1, observaciones: '' };
  }

  // Aviso informativo: la bitácora se registró en una fecha distinta a la de la actividad.
  bitacoraFueraDeFecha(bitacora: Bitacora): boolean {
    return !!bitacora.fechaRegistro && bitacora.fechaRegistro !== bitacora.fecha;
  }

  // Student Pertinencia Likert Survey
  openSurvey(parcial: number): void {
    this.surveyTargetParcial.set(parcial);
    this.surveyAnswers = { tutorScore: 5, companyScore: 5, relevanceScore: 5, clarityScore: 5, comment: '' };
    this.showSurveyModal.set(true);
  }

  guardandoEncuesta = signal(false);

  saveSurvey(): void {
    if (this.guardandoEncuesta()) return;
    const est = this.estudianteActual();
    if (!est || !est.id) return;

    // La encuesta se registra sobre la practica activa del estudiante
    const prac = this.practicas().find(p =>
      p.estudiante.id === est.id && (p.estado === 'en_curso' || p.estado === 'pendiente'))
      || this.practicas().find(p => p.estudiante.id === est.id);
    if (!prac || !prac.id) {
      this.toastService.warning('No tienes una práctica registrada para asociar la encuesta.');
      return;
    }

    this.guardandoEncuesta.set(true);
    this.evaluacionService.marcarEncuesta(prac.id, this.surveyTargetParcial(), {
      satisfaccionTutor: this.surveyAnswers.tutorScore,
      satisfaccionEmpresaProyecto: this.surveyAnswers.companyScore,
      relacionCarrera: this.surveyAnswers.relevanceScore,
      claridadInstrucciones: this.surveyAnswers.clarityScore,
      comentario: this.surveyAnswers.comment
    }).pipe(finalize(() => this.guardandoEncuesta.set(false))).subscribe({
      next: (guardada) => {
        this.mergeEvaluacion(guardada);
        this.showSurveyModal.set(false);
        this.toastService.success('Encuesta de pertinencia guardada. Gracias por tus comentarios.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo guardar la encuesta.')
    });
  }

  // Tutor: Approve bitacora
  processBitacora(b: Bitacora, status: 'aprobada' | 'rechazada' | 'requiere_correccion', observacion?: string): void {
    if (!b.id) return;
    this.procesandoBitacoraId.set(b.id);
    this.bitacoraService.update(b.id, {
      estado: status,
      ...((status === 'rechazada' || status === 'requiere_correccion') ? { observacionesTutor: observacion?.trim() } : {})
    }).pipe(
      finalize(() => this.procesandoBitacoraId.set(null))
    ).subscribe({
      next: (actualizada) => {
        this.bitacoras.set(this.bitacoras().map(item =>
          item.id === b.id ? actualizada : item));
        this.practicaService.clearCache();
        this.loadData();
        this.toastService.success(
          status === 'aprobada' ? 'Bitácora aprobada.' :
          status === 'requiere_correccion' ? 'Se solicitó corrección al estudiante.' :
          'Bitácora rechazada.');
      },
      error: () => undefined
    });
  }

  // Evaluacion de (practica, parcial) ya cargada desde la API
  private evaluacionDe(pracId: number | undefined, parcial: number): EvaluacionDetalle | undefined {
    return this.evaluaciones().find(e => e.practica?.id === pracId && e.parcial === parcial);
  }

  // Reemplaza en memoria la evaluacion guardada/actualizada
  private mergeEvaluacion(guardada: EvaluacionDetalle): void {
    const resto = this.evaluaciones().filter(e =>
      !(e.practica?.id === guardada.practica?.id && e.parcial === guardada.parcial));
    this.evaluaciones.set([...resto, guardada]);
  }

  // Mensaje de error del backend (reglas de negocio) o texto por defecto
  private errorMsg(err: any, fallback: string): string {
    return err?.error?.error || fallback;
  }

  // Tutor & Coord Rubric Scoring
  openGradingModal(prac: Practica, parcial: number): void {
    // Regla de secuencia: el parcial anterior debe tener ambas notas
    if (parcial > 1) {
      const previa = this.getGrades(prac.id!);
      const completo = parcial === 2
        ? (previa.t1 > 0 && previa.c1 > 0)
        : (previa.t2 > 0 && previa.c2 > 0);
      if (!completo) {
        this.toastService.warning(`Primero deben registrarse las notas de tutor y coordinador del Parcial ${parcial - 1}.`);
        return;
      }
    }

    const deadlineStr = parcial === 1 ? this.gradeDeadlines().parcial1 : (parcial === 2 ? this.gradeDeadlines().parcial2 : this.gradeDeadlines().parcial3);

    // Solo se bloquea si hay una fecha limite configurada y ya expiro
    if (deadlineStr) {
      const deadline = new Date(deadlineStr);
      deadline.setHours(23, 59, 59, 999);
      if (new Date() > deadline) {
        this.toastService.error(`El periodo de evaluación académica para el Parcial ${parcial} expiró el ${deadlineStr}. El formulario de rúbrica ha sido bloqueado.`);
        return;
      }
    }

    this.selectedPracticaForGrading.set(prac);
    this.selectedParcial.set(parcial);

    const ev = this.evaluacionDe(prac.id, parcial);
    this.tGrade = ev?.notaTutor || 0;
    this.cGrade = ev?.notaCoord || 0;
  }

  saveGradeDeadlines(): void {
    if (this.userRole() !== 'ADMIN') {
      this.toastService.warning('Solo Administración puede modificar los plazos de calificación.');
      return;
    }
    const d = this.gradeDeadlines();
    const periodo = this.expediente.periodoActual();
    const cambios = [
      { parcial: 1, fechaLimite: d.parcial1 },
      { parcial: 2, fechaLimite: d.parcial2 },
      { parcial: 3, fechaLimite: d.parcial3 }
    ].filter(x => !!x.fechaLimite)
     .map(x => this.configuracionService.saveFechaLimite({ periodoAcademico: periodo, ...x }));

    if (cambios.length === 0) {
      this.toastService.warning('Ingresa al menos una fecha límite.');
      return;
    }

    forkJoin(cambios).subscribe({
      next: () => this.toastService.success('Fechas límite de calificaciones guardadas con éxito en el sistema.'),
      error: () => this.toastService.error('No se pudieron guardar las fechas límite.')
    });
  }

  guardandoCalificacion = signal(false);

  saveRubricGrade(): void {
    if (this.guardandoCalificacion()) return;
    const prac = this.selectedPracticaForGrading();
    if (!prac || !prac.id) return;

    const payload: EvaluacionDetalle = {
      practica: { id: prac.id } as Practica,
      parcial: this.selectedParcial()
    };
    if (this.userRole() === 'TUTOR') {
      payload.notaTutor = this.tGrade;
    } else {
      payload.notaCoord = this.cGrade;
    }

    this.guardandoCalificacion.set(true);
    this.evaluacionService.guardar(payload).subscribe({
      next: (guardada) => {
        this.guardandoCalificacion.set(false);
        this.mergeEvaluacion(guardada);
        this.selectedPracticaForGrading.set(null);
        this.toastService.success('Calificación guardada y promediada con éxito.');
      },
      error: (err) => {
        this.guardandoCalificacion.set(false);
        this.toastService.error(this.errorMsg(err, 'No se pudo guardar la calificación.'));
      }
    });
  }

  getGrades(pracId: number) {
    const nota = (parcial: number) => {
      const ev = this.evaluacionDe(pracId, parcial);
      return { t: ev?.notaTutor || 0, c: ev?.notaCoord || 0 };
    };
    const n1 = nota(1), n2 = nota(2), n3 = nota(3);

    const p1 = n1.t > 0 || n1.c > 0 ? (n1.t * 0.5 + n1.c * 0.5) : 0;
    const p2 = n2.t > 0 || n2.c > 0 ? (n2.t * 0.5 + n2.c * 0.5) : 0;
    const p3 = n3.t > 0 || n3.c > 0 ? (n3.t * 0.5 + n3.c * 0.5) : 0;
    const total = p1 + p2 + p3;

    return {
      t1: n1.t, c1: n1.c, p1,
      t2: n2.t, c2: n2.c, p2,
      t3: n3.t, c3: n3.c, p3,
      total
    };
  }

  // Recibe el id de la practica (no del estudiante): un estudiante puede
  // tener varias practicas (I y II) y cada una tiene su propia encuesta.
  hasCompletedSurvey(practicaId: number | undefined, parcial: number): boolean {
    return !!this.evaluacionDe(practicaId, parcial)?.encuestaCompletada;
  }

  encuestaHabilitada(prac: Practica, parcial: number): boolean {
    const ev = this.evaluacionDe(prac.id, parcial);
    return !!ev && (ev.notaTutor || 0) > 0 && (ev.notaCoord || 0) > 0;
  }

  filteredPracticas() {
    return this.practicas();
  }

  // Coordinador: asigna o cambia el tutor de una practica existente
  // (la via correcta tras consolidar la meritocracia; no crea otra practica)
  asignarTutor(prac: Practica, tutorId: number | null): void {
    const vinculo = this.tutorCompatibleConPractica(prac)
      .find(item => item.tutorId === Number(tutorId));
    const tutor = vinculo ? this.usuarioDesdeVinculo(vinculo) : undefined;
    if (!prac.id || !tutor) return;
    this.practicaService.update(prac.id, { ...prac, tutor }).subscribe({
      next: () => {
        this.loadData();
        this.toastService.success(`Tutor ${tutor.nombre} ${tutor.apellido} asignado al expediente.`);
      },
      error: (err) => this.toastService.error(this.errorMsg(err, 'No se pudo asignar el tutor.'))
    });
  }

  cerrarPractica(practicaId: number): void {
    if (this.cerrandoPracticaId() !== null) return;
    this.cerrandoPracticaId.set(practicaId);
    this.practicaService.cerrar(practicaId).pipe(
      finalize(() => this.cerrandoPracticaId.set(null))
    ).subscribe({
      next: () => {
        this.loadData();
        this.toastService.success('Práctica cerrada formalmente.');
      },
      error: (err) => this.toastService.error(this.errorMsg(err, 'No se pudo cerrar la práctica.'))
    });
  }

  // Acta en modal propio: window.open tras la respuesta HTTP era bloqueado
  // por el navegador (no es un gesto directo del usuario).
  actaAbierta = signal<{ titulo: string; acta: ActaCierre } | null>(null);

  abrirActaPractica(practicaId: number): void {
    if (this.cargandoActaPracticaId() !== null) return;
    this.cargandoActaPracticaId.set(practicaId);
    this.practicaService.getActa(practicaId).pipe(
      finalize(() => this.cargandoActaPracticaId.set(null))
    ).subscribe({
      next: acta => this.actaAbierta.set({ titulo: 'Acta de cierre de práctica', acta: acta as ActaCierre }),
      error: (err) => this.toastService.error(this.errorMsg(err, 'No se pudo abrir el acta de cierre.'))
    });
  }

  abrirLiberarCupo(prac: Practica): void {
    this.motivoLiberacion = '';
    this.liberandoPractica.set(prac);
  }

  puedeLiberarCupo(prac: Practica): boolean {
    return (this.userRole() === 'ADMIN' || this.userRole() === 'COORDINADOR')
      && (prac.estado || '').toLowerCase() === 'retirado'
      && !prac.cupoLiberado;
  }

  confirmarLiberarCupo(): void {
    const prac = this.liberandoPractica();
    if (!prac?.id || this.liberandoCupo()) return;
    const motivo = this.motivoLiberacion.trim();
    if (motivo.length < 10) {
      this.toastService.warning('El motivo de liberación debe tener al menos 10 caracteres.');
      return;
    }
    this.liberandoCupo.set(true);
    this.practicaService.liberarCupo(prac.id, motivo).subscribe({
      next: () => {
        this.liberandoCupo.set(false);
        this.liberandoPractica.set(null);
        this.loadData();
        this.toastService.success('Cupo liberado: la empresa vuelve a tener un cupo disponible para reemplazo.');
      },
      error: (err) => {
        this.liberandoCupo.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo liberar el cupo.');
      }
    });
  }

  openSeguimientoModal(prac: Practica): void {
    this.encuestasSeguimiento.set([]);
    this.comentariosCoordinacionSeguimiento.set([]);
    const consultaGestion = ['ADMIN', 'COORDINADOR'].includes(this.userRole());
    forkJoin({
      encuestas: this.evaluacionService.getEncuestasByPractica(prac.id!),
      notas: this.notaCoordinacionService.getByEstudiante(prac.estudiante.id!),
      bits: consultaGestion ? this.bitacoraService.getByEstudiante(prac.estudiante.id!) : of([] as Bitacora[]),
      asis: consultaGestion ? this.asistenciaService.getByPractica(prac.id!) : of([] as Asistencia[])
    }).subscribe({
      next: ({ encuestas, notas, bits, asis }) => {
        this.encuestasSeguimiento.set(encuestas);
        this.comentariosCoordinacionSeguimiento.set(
          notas.filter(n => n.practica?.id === prac.id));
        if (consultaGestion) {
          // Only keep bitacoras that belong to this practica
          const bitsPractica = bits.filter(b => b.practica?.id === prac.id);

          // Merge with existing caches to avoid duplicates
          const existingBits = this.bitacoras().filter(b => b.practica?.id !== prac.id);
          const existingAsis = this.asistencias().filter(a => a.practica?.id !== prac.id);

          this.bitacoras.set([...existingBits, ...bitsPractica]);
          this.asistencias.set([...existingAsis, ...asis]);
        }
        this.selectedPracForSeguimiento.set(prac);
        this.showSeguimientoModal.set(true);
      },
      error: (err) => {
        this.toastService.error(err?.error?.error || 'No se pudo cargar el seguimiento del expediente.');
      }
    });
  }

  closeSeguimientoModal(): void {
    this.showSeguimientoModal.set(false);
    this.selectedPracForSeguimiento.set(null);
    this.encuestasSeguimiento.set([]);
    this.comentariosCoordinacionSeguimiento.set([]);
  }

  encuestaDe(parcial: number): EncuestaSatisfaccion | undefined {
    return this.encuestasSeguimiento().find(e => e.parcial === parcial);
  }

  abrirHistorialPersonal(prac: Practica): void {
    this.historialPersonal.set(prac);
    // Se recarga siempre (no se reusa el cache en memoria): el historial puede
    // haber cambiado por una accion reciente (bitacora revisada, nota, etc.)
    // dentro de la misma sesion.
    this.historialPersonalSeleccionado.set(null);
    this.lineaTiempoPersonalCargando.set(true);
    this.lineaTiempoService.getPracticaMe().subscribe({
      next: historiales => {
        this.lineaTiempoPersonal.set(historiales);
        this.lineaTiempoPersonalCargando.set(false);
        this.seleccionarHistorialPersonal(prac);
      },
      error: err => {
        this.lineaTiempoPersonalCargando.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo cargar tu historial de prácticas.');
      }
    });
  }

  private seleccionarHistorialPersonal(prac: Practica): void {
    const historial = this.lineaTiempoPersonal().find(item => item.expedienteId === prac.id);
    this.historialPersonalSeleccionado.set(historial || null);
  }

  cerrarHistorialPersonal(): void {
    this.historialPersonal.set(null);
    this.historialPersonalSeleccionado.set(null);
  }

  estadoTexto(estado?: string): string {
    return estadoExpedienteTexto(estado);
  }

  getBitacorasForStudent(studentId: number) {
    return this.bitacoras().filter(b => b.estudiante?.id === studentId);
  }

  practicasActivasTutor(): Practica[] {
    return this.practicas().filter(practica =>
      ['pendiente', 'en_curso'].includes(String(practica.estado || '').toLowerCase()));
  }

  asistenciasDeTutorados(): Asistencia[] {
    const practicaIds = new Set(this.practicasDelPeriodo().map(practica => practica.id));
    return this.asistencias().filter(asistencia =>
      asistencia.practica?.id != null && practicaIds.has(asistencia.practica.id));
  }

  getAsistenciasForPractica(practicaId: number) {
    return this.asistencias().filter(a => a.practica?.id === practicaId);
  }
}
