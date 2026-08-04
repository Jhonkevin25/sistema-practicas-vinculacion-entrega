import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { VinculacionService, Vinculacion, PostulacionVinculacion, SeguimientoVinculacion } from '../../core/services/vinculacion.service';
import { Estudiante, EstudianteService } from '../../core/services/estudiante.service';
import { Usuario } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { finalize, forkJoin, of, switchMap } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ProyectoService, Proyecto, modalidadProyectoTexto } from '../../core/services/proyecto.service';
import { ConfiguracionService, FechaLimiteCalificacion } from '../../core/services/configuracion.service';
import { DocumentoService, TipoDocumento, DocEstudiante } from '../../core/services/documento.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import { Convenio, ConvenioService } from '../../core/services/convenio.service';
import { BitacoraService, Bitacora, correccionBitacoraPendiente } from '../../core/services/bitacora.service';
import { AsistenciaService, Asistencia } from '../../core/services/asistencia.service';
import { EvaluacionService, EvaluacionDetalle, EncuestaSatisfaccion } from '../../core/services/evaluacion.service';
import { NotaCoordinacion, NotaCoordinacionService } from '../../core/services/nota-coordinacion.service';
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
import { LineaTiempoExpedienteComponent } from '../shared/linea-tiempo-expediente/linea-tiempo-expediente';
import { LineaTiempoExpediente, LineaTiempoService, estadoExpedienteTexto } from '../../core/services/linea-tiempo.service';
import { ProyectoFormModalComponent } from '../shared/proyecto-form-modal/proyecto-form-modal';
import { PaginadorComponent } from '../shared/paginador/paginador';
import { TAMANO_PAGINA_DEFECTO } from '../../core/services/paginacion';
import { AutocompleteComponent } from '../shared/autocomplete/autocomplete.component';
import { CarreraService } from '../../core/services/carrera.service';
import { ComentariosSeguimientoModalComponent } from '../shared/comentarios-seguimiento-modal/comentarios-seguimiento-modal';
import { TutorFundacion, TutorFundacionService } from '../../core/services/tutor-fundacion.service';

export function tieneVinculacionActiva(vinculaciones: Pick<Vinculacion, 'estado'>[]): boolean {
  return vinculaciones.some(vinculacion =>
    ['pendiente', 'en_curso'].includes(String(vinculacion.estado || '').trim().toLowerCase()));
}

export function asistenciaParaVinculacion(
  vinculacionId: number,
  datos: Pick<Asistencia, 'fecha' | 'horaIngreso' | 'horaSalida' | 'estado' | 'observaciones'>
): Asistencia {
  return { vinculacion: { id: vinculacionId }, ...datos };
}

@Component({
  selector: 'app-vinculacion',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, BandejaTabsComponent, DocumentosRevisionPanelComponent,
    SeguimientoAcademicoPanelComponent, BitacorasRevisionPanelComponent, AsistenciasHistorialPanelComponent, CargaDocumentosPanelComponent,
    CalificacionModalComponent, ActaCierreModalComponent, LineaTiempoExpedienteComponent,
    ProyectoFormModalComponent, ComentariosSeguimientoModalComponent, PaginadorComponent, AutocompleteComponent],
  templateUrl: './vinculacion.html',
  styleUrl: './vinculacion.css'
})
export class VinculacionComponent {
  private readonly vinculacionService = inject(VinculacionService);
  readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly proyectoService = inject(ProyectoService);
  private readonly configuracionService = inject(ConfiguracionService);
  private readonly documentoService = inject(DocumentoService);
  private readonly expediente = inject(ExpedienteService);
  private readonly estudianteService = inject(EstudianteService);
  private readonly convenioService = inject(ConvenioService);
  private readonly bitacoraService = inject(BitacoraService);
  private readonly asistenciaService = inject(AsistenciaService);
  private readonly evaluacionService = inject(EvaluacionService);
  private readonly notaCoordinacionService = inject(NotaCoordinacionService);
  private readonly lineaTiempoService = inject(LineaTiempoService);
  private readonly carreraService = inject(CarreraService);
  private readonly tutorFundacionService = inject(TutorFundacionService);

  loading = signal(true);
  isRefreshing = signal(false);
  userRole = signal('');
  activeTab = signal('lista');

  tabsVisibles(): BandejaTab[] {
    if (this.userRole() === 'ADMIN' || this.userRole() === 'COORDINADOR') {
      return [
        { id: 'lista', label: 'Asignaciones' }, { id: 'seguimiento_coord', label: 'Seguimiento' },
        { id: 'postulaciones', label: 'Postulaciones' }, { id: 'proyectos', label: 'Proyectos' },
        { id: 'calificaciones_adm', label: 'Notas' },
        { id: 'directa', label: 'Asignación directa' }, { id: 'documentos_revision', label: 'Documentos' }
      ];
    }
    if (this.userRole() === 'TUTOR') {
      return [{ id: 'tutorados', label: 'Estudiantes vinculados' }, { id: 'asistencia_tutor', label: 'Asistencia' },
        { id: 'bitacoras_tutor', label: 'Revisar bitácoras' }];
    }
    const tabs: BandejaTab[] = [{ id: 'mi_progreso', label: 'Mi avance' }];
    if (tieneVinculacionActiva(this.vinculaciones())) tabs.push({ id: 'bitacora_estudiante', label: 'Registrar bitácora' });
    if (!this.vinculacionCompletada()) {
      if (!this.cvCargado() || !this.cartaCargada() || !this.cedulaCargada()) tabs.push({ id: 'cargar_docs', label: '1. Documentos' });
      if (this.cvCargado() && this.cartaCargada() && this.cedulaCargada()
          && !tieneVinculacionActiva(this.vinculaciones()) && !this.miPostulacionPendiente()) {
        tabs.push(this.postulacionHabilitada()
          ? { id: 'ofertas', label: '2. Proyectos disponibles' }
          : { id: 'espera_postulacion', label: 'En espera', emphasis: 'warning' });
      }
    }
    return tabs;
  }

  cambiarTab(tab: string): void {
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

  vinculaciones = signal<Vinculacion[]>([]);
  lineaTiempoPersonal = signal<LineaTiempoExpediente[]>([]);
  lineaTiempoPersonalCargando = signal(false);
  historialPersonal = signal<Vinculacion | null>(null);
  historialPersonalSeleccionado = signal<LineaTiempoExpediente | null>(null);
  postulaciones = signal<PostulacionVinculacion[]>([]);
  estudiantes = signal<Estudiante[]>([]);
  tutores = signal<Usuario[]>([]);
  // Fase 55/paridad Empresas: el selector de tutor de la asignación directa y
  // de edición del listado se filtra por fundación + carrera del estudiante,
  // igual que en Prácticas con TutorEmpresaService (no se usa la lista plana
  // de "tutores" para estas dos acciones puntuales).
  tutoresCompatiblesDirecta = signal<TutorFundacion[]>([]);
  cargandoTutoresDirecta = signal(false);
  tutoresCompatiblesVinculacion = signal<Record<number, TutorFundacion[]>>({});
  tutoresCompatiblesPostulacion = signal<Record<number, TutorFundacion[]>>({});
  bitacoras = signal<Bitacora[]>([]);
  procesandoBitacoraId = signal<number | null>(null);
  bitacoraEnCorreccion = signal<Bitacora | null>(null);
  guardandoBitacora = signal(false);
  asistencias = signal<Asistencia[]>([]);
  evaluaciones = signal<EvaluacionDetalle[]>([]);
  seguimiento = signal<SeguimientoVinculacion[]>([]);
  filtroSeguimiento = signal('TODOS');
  searchText = signal('');
  // Default "EN_CURSO" (no "TODOS"): con cada periodo se acumulan expedientes
  // cerrados y sin esto la bandeja diaria del coordinador se llena de historial.
  filtroEstado = signal('EN_CURSO');
  filtroCiudad = signal('TODOS');
  filtroModalidad = signal('TODOS');

  buscarEstudiante = (texto: string) => this.estudianteService.getPaginado(0, 10, { texto }).pipe(map(res => res.contenido));
  displayEstudiante = (est: Estudiante) => `${est.usuario?.nombre || ''} ${est.usuario?.apellido || ''} (Carrera: ${est.carrera || 'N/A'})`;
  resolverEstudiante = (id: number) => this.estudianteService.getById(id);

  buscarProyecto = (texto: string) => of(this.proyectos().filter(p => 
    p.nombre.toLowerCase().includes(texto.toLowerCase()) || 
    p.fundacion?.nombre.toLowerCase().includes(texto.toLowerCase())
  ).slice(0, 10));
  displayProyecto = (p: Proyecto) => `${p.nombre} - ${p.fundacion?.nombre || 'Interno'} (${p.cuposDisponibles} cupos)`;
  resolverProyecto = (id: number) => of(this.proyectos().find(p => p.id === id)!);

  paginaLista = signal(0);
  tamanoPaginaLista = signal(TAMANO_PAGINA_DEFECTO);
  totalElementosLista = signal(0);
  totalPaginasLista = signal(0);
  cargandoLista = signal(false);

  // Filtros del seguimiento/calificaciones (se envían al backend)
  filtroSeguimientoPeriodo = signal(this.expediente.periodoActual());
  filtroSeguimientoCarrera = signal('TODOS');
  filtroSeguimientoTutor = signal('TODOS');

  paginaSeguimiento = signal(0);
  tamanoPaginaSeguimiento = signal(TAMANO_PAGINA_DEFECTO);
  totalElementosSeguimiento = signal(0);
  totalPaginasSeguimiento = signal(0);
  cargandoSeguimiento = signal(false);

  // Catálogo de carreras para los filtros de gestión (los estudiantes ya no
  // se cargan completos: se buscan por autocompletado asíncrono).
  carrerasCatalogo = signal<string[]>([]);

  carrerasUnicas() {
    return this.carrerasCatalogo();
  }

  proyectos = signal<Proyecto[]>([]);
  proyectosGestion = signal<Proyecto[]>([]);
  convenios = signal<Convenio[]>([]);
  cargandoProyectos = signal(false);

  showProyectoModal = signal(false);
  proyectoEnEdicion: Proyecto | null = null;
  proyectoDuplicando = false;
  pausandoProyectoId = signal<number | null>(null);

  liberandoVinculacion = signal<Vinculacion | null>(null);
  motivoLiberacion = '';
  liberandoCupo = signal(false);

  convocatoriaInicio = signal('');
  convocatoriaFin = signal('');
  fechaLimiteDocumentos = signal('');
  fechaInicioPostulacion = signal('');
  fechaActual = signal(new Date().toISOString().split('T')[0]);

  showSeguimientoModal = signal(false);
  selectedVinculacion = signal<Vinculacion | null>(null);
  encuestasSeguimiento = signal<EncuestaSatisfaccion[]>([]);
  comentariosCoordinacionSeguimiento = signal<NotaCoordinacion[]>([]);
  documentosSeguimiento = signal<DocEstudiante[]>([]);
  abriendoDocumentoSeguimiento = signal<TipoDocumento | null>(null);
  readonly documentosObligatoriosSeguimiento: { tipo: TipoDocumento; label: string }[] = [
    { tipo: 'cv', label: 'Hoja de Vida (CV)' },
    { tipo: 'carta', label: 'Carta de Solicitud' },
    { tipo: 'cedula', label: 'Copia de Cédula' }
  ];
  selectedVinculacionForGrading = signal<Vinculacion | null>(null);
  selectedParcial = signal(1);
  tGrade: number = 0;
  cGrade: number = 0;
  gradeDeadlines = signal({ parcial1: '', parcial2: '', parcial3: '' });
  showSurveyModal = signal(false);
  surveyTargetParcial = signal(1);
  surveyAnswers = { tutorScore: 5, projectScore: 5, relevanceScore: 5, clarityScore: 5, comment: '' };
  newBitacora = { fecha: '', actividad: '', horas: 4, horasExtra: 0, parcial: 1 as 1 | 2 | 3, observaciones: '' };

  postulacionHabilitada(): boolean {
    const inicio = this.fechaInicioPostulacion();
    return !!inicio && this.fechaActual() >= inicio;
  }

  vinculacionCompletada(): boolean {
    return this.vinculaciones().some(v => (v.estado || '').toLowerCase() === 'completado');
  }

  asignarTutor(vinc: Vinculacion, tutorId: number | null): void {
    const vinculo = this.tutorCompatibleConVinculacion(vinc)
      .find(item => item.tutorId === Number(tutorId));
    const tutor = vinculo ? this.usuarioDesdeVinculoFundacion(vinculo) : undefined;
    if (!vinc.id || !tutor) return;
    this.vinculacionService.update(vinc.id, { ...vinc, tutor }).subscribe({
      next: () => {
        this.loadData();
        this.toastService.success(`Tutor ${tutor.nombre} ${tutor.apellido} asignado al expediente.`);
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo asignar el tutor.')
    });
  }

  // Tutores externos compatibles con la fundación y la carrera de esta
  // vinculación (para el selector de edición del listado "Asignaciones")
  tutorCompatibleConVinculacion(vinc: Vinculacion): TutorFundacion[] {
    return vinc.id ? this.tutoresCompatiblesVinculacion()[vinc.id] || [] : [];
  }

  private cargarTutoresParaVinculaciones(vinculaciones: Vinculacion[]): void {
    const configurables = vinculaciones.filter(vinculacion =>
      vinculacion.id && vinculacion.fundacion?.id && vinculacion.estudiante?.carrera);
    if (configurables.length === 0) {
      this.tutoresCompatiblesVinculacion.set({});
      return;
    }

    forkJoin(configurables.map(vinculacion =>
      this.tutorFundacionService.getCompatibles(vinculacion.fundacion.id!, vinculacion.estudiante.carrera).pipe(
        map(tutores => ({ vinculacionId: vinculacion.id!, tutores })),
        catchError(() => of({ vinculacionId: vinculacion.id!, tutores: [] as TutorFundacion[] }))
      )
    )).subscribe(resultados => {
      this.tutoresCompatiblesVinculacion.set(Object.fromEntries(
        resultados.map(resultado => [resultado.vinculacionId, resultado.tutores])
      ));
    });
  }

  private usuarioDesdeVinculoFundacion(vinculo: TutorFundacion): Usuario | undefined {
    if (!vinculo.tutorId) return undefined;
    return {
      id: vinculo.tutorId,
      nombre: vinculo.nombre,
      apellido: vinculo.apellido,
      email: vinculo.email || ''
    };
  }

  filtroTutorados = '';
  vistaTutorados = signal<'ACTIVOS' | 'HISTORIAL'>('ACTIVOS');
  filtroCarreraTutor = signal('TODAS');
  comentarioVinculacion = signal<Vinculacion | null>(null);

  // Un tutor puede estar vinculado a varias carreras de una misma fundacion;
  // el filtro solo se muestra si de verdad tiene tutorados de mas de una.
  carrerasTutor(): string[] {
    const carreras = this.vinculaciones()
      .map(v => v.estudiante?.carrera)
      .filter((c): c is string => !!c);
    return [...new Set(carreras)].sort((a, b) => a.localeCompare(b, 'es'));
  }

  tutoradosOrdenados(): Vinculacion[] {
    const texto = this.filtroTutorados.trim().toLowerCase();
    const esActiva = (v: Vinculacion) =>
      ['pendiente', 'en_curso'].includes(String(v.estado || '').toLowerCase());
    return this.vinculacionesDelPeriodo()
      .filter(v => this.vistaTutorados() === 'ACTIVOS'
        ? !this.expedienteTerminal(v.estado)
        : this.expedienteTerminal(v.estado))
      .filter(v => this.filtroCarreraTutor() === 'TODAS'
        || v.estudiante?.carrera === this.filtroCarreraTutor())
      .filter(v => {
        if (!texto) return true;
        const u = v.estudiante?.usuario;
        return [u?.nombre, u?.apellido, v.estudiante?.matricula, v.proyecto?.nombre, v.estudiante?.carrera]
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

  abrirComunicaciones(vinculacion: Vinculacion): void {
    if (vinculacion.id) this.comentarioVinculacion.set(vinculacion);
  }

  cerrarComunicaciones(): void {
    this.comentarioVinculacion.set(null);
  }

  cvCargado = signal(false);
  cartaCargada = signal(false);
  cedulaCargada = signal(false);
  cartaAceptacionCargada = signal(false);
  documentosDetalle = signal<DocEstudiante[]>([]);
  subiendoDocumento = signal<TipoDocumento | null>(null);
  documentosRevision = signal<DocEstudiante[]>([]);
  cargandoDocumentosRevision = signal(false);
  accionDocumentoRevision = signal<AccionDocumentoRevision | null>(null);
  filtroDocProceso: 'TODOS' | 'GENERAL' | 'PRACTICAS' | 'VINCULACION' = 'VINCULACION';
  filtroDocEstado: 'TODOS' | 'pendiente' | 'cargado' | 'en_revision' | 'aprobado' | 'rechazado' | 'requiere_correccion' = 'cargado';
  filtroDocCarrera = 'TODOS';
  observacionRevision: Record<number, string> = {};

  selectedEstudianteId: number | null = null;
  selectedEstudianteObj: Estudiante | null = null;
  selectedProyectoId: number | null = null;
  selectedProyectoObj: Proyecto | null = null;
  selectedTutorId: number | null = null;
  tutorPorPostulacion: Record<number, number | null> = {};
  observacionRechazo: Record<number, string> = {};

  postulandoProyectoId = signal<number | null>(null);
  procesandoPostulacionId = signal<number | null>(null);
  asignandoDirecta = signal(false);
  cerrandoVinculacionId = signal<number | null>(null);
  cargandoActaVinculacionId = signal<number | null>(null);
  inspeccionandoVinculacionId = signal<number | null>(null);

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
    this.loadData(true);
    this.loadDocumentos();
    if (user && (user.rol === 'ADMIN' || user.rol === 'COORDINADOR')) {
      this.carreraService.getAll().subscribe({
        next: carreras => this.carrerasCatalogo.set(
          carreras.map(c => c.nombre).filter(Boolean).sort((a, b) => a.localeCompare(b))),
        error: () => {}
      });
    }
  }

  loadData(isInitial = false): void {
    if (isInitial) this.loading.set(true); else this.isRefreshing.set(true);
    const user = this.authService.currentUser();
    if (!user) return;
    const esGestion = user.rol === 'ADMIN' || user.rol === 'COORDINADOR';
    const esTutor = user.rol === 'TUTOR';

    forkJoin({
      estudiantes: esTutor ? of([] as Estudiante[]) : this.expediente.estudiantesSegunRol(user.rol),
      usuarios: esGestion ? this.expediente.usuariosSegunRol(user.rol) : of([] as Usuario[]),
      // ADMIN/COORDINADOR obtienen su listado con cargarPaginaLista() (paginado
      // en BD); pedir aquí vinculacionesSegunRol() para ellos traía TODAS las
      // vinculaciones (getAll sin paginar) solo para descartar el resultado abajo.
      vinculaciones: esGestion ? of([] as Vinculacion[]) : this.expediente.vinculacionesSegunRol(user.rol),
      postulaciones: this.expediente.postulacionesVinculacionSegunRol(user.rol),
      configuraciones: esTutor ? of([]) : this.configuracionService.getByTipo('VINCULACION'),
      bitacoras: this.expediente.bitacorasSegunRol(user.rol),
      asistencias: this.expediente.asistenciasSegunRol(user.rol),
      evaluaciones: esGestion ? of([] as EvaluacionDetalle[]) : this.evaluacionService.getAll(),
      fechasLimite: this.configuracionService.getFechasLimite(),
      seguimiento: of([] as SeguimientoVinculacion[]),
      convenios: esGestion
        ? this.convenioService.getAll()
        : of([] as Convenio[])
    }).subscribe({
      next: (res: { estudiantes: Estudiante[], usuarios: Usuario[], vinculaciones: Vinculacion[], postulaciones: PostulacionVinculacion[], configuraciones: any[], bitacoras: Bitacora[], asistencias: Asistencia[], evaluaciones: EvaluacionDetalle[], fechasLimite: FechaLimiteCalificacion[], seguimiento: SeguimientoVinculacion[], convenios: Convenio[] }) => {
        const assigned = this.authService.carrerasAsignadas();

        this.convenios.set(res.convenios);
        this.bitacoras.set(res.bitacoras);
        this.asistencias.set(res.asistencias);
        this.evaluaciones.set(res.evaluaciones);
        this.seguimiento.set(res.seguimiento);

        if (!esTutor) {
          this.cargandoProyectos.set(true);
          this.proyectoService.getAll().subscribe({
            next: (proyectos) => {
              this.proyectos.set(proyectos.filter(p => p.estado !== false));
              this.proyectosGestion.set(esGestion ? proyectos : []);
              this.cargandoProyectos.set(false);
            },
            error: () => this.cargandoProyectos.set(false)
          });
        }

        const limites = [...res.fechasLimite]
          .sort((a, b) => String(b.periodoAcademico).localeCompare(String(a.periodoAcademico)));
        const limiteDe = (p: number) => limites.find(l => l.parcial === p)?.fechaLimite || '';
        this.gradeDeadlines.set({
          parcial1: limiteDe(1),
          parcial2: limiteDe(2),
          parcial3: limiteDe(3)
        });

        const config = this.expediente.configVigente(res.configuraciones, 'VINCULACION');
        if (config) {
          this.convocatoriaInicio.set(config.convocatoriaInicio);
          this.convocatoriaFin.set(config.convocatoriaFin);
          this.fechaLimiteDocumentos.set(config.fechaLimiteDocumentos);
          this.fechaInicioPostulacion.set(config.fechaInicioPostulacion);
        }

        if (user.rol === 'COORDINADOR') {
          this.estudiantes.set(res.estudiantes.filter((e: Estudiante) => assigned.includes(e.carrera)));
        } else {
          this.estudiantes.set(res.estudiantes);
        }

        this.tutores.set(this.expediente.soloTutores(res.usuarios, 'VINCULACION'));

        if (user.rol === 'ESTUDIANTE' || user.rol === 'TUTOR') {
          this.vinculaciones.set(res.vinculaciones);
          if (user.rol === 'TUTOR') this.inicializarPeriodoTutor();
        } else if (user.rol === 'COORDINADOR') {
          this.vinculaciones.set([]);
        } else {
          this.vinculaciones.set([]);
        }
        if (user.rol === 'COORDINADOR') {
          this.postulaciones.set(res.postulaciones.filter((p: PostulacionVinculacion) => assigned.includes(p.estudiante?.carrera)));
        } else {
          this.postulaciones.set(res.postulaciones);
        }
        if (esGestion) {
          this.cargarTutoresPostulaciones();
        }
        if (user.rol === 'ADMIN' || user.rol === 'COORDINADOR') {
          this.loadDocumentosRevision();
        }
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
        if (this.activeTab() === 'lista' && esGestion) this.cargarPaginaLista();
        if (['seguimiento_coord', 'calificaciones_adm'].includes(this.activeTab()) && esGestion) this.cargarPaginaSeguimiento();
      },
      error: () => {
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      }
    });
  }

  postulacionesPendientes(): PostulacionVinculacion[] {
    return this.postulaciones().filter(p => p.estado === 'Pendiente');
  }

  // El backend exige que el tutor asignado al aprobar una postulacion este
  // vinculado a la fundacion del proyecto y cubra la carrera del estudiante
  // (mismo patron que cargarTutoresMeritocracia() en practicas.ts)
  private cargarTutoresPostulaciones(): void {
    const configurables = this.postulacionesPendientes()
      .filter(post => post.id && post.proyecto?.fundacion?.id && post.estudiante?.carrera);
    if (configurables.length === 0) {
      this.tutoresCompatiblesPostulacion.set({});
      return;
    }

    forkJoin(configurables.map(post =>
      this.tutorFundacionService.getCompatibles(post.proyecto.fundacion.id!, post.estudiante.carrera).pipe(
        map(tutores => ({ postulacionId: post.id!, tutores })),
        catchError(() => of({ postulacionId: post.id!, tutores: [] as TutorFundacion[] }))
      )
    )).subscribe(resultados => {
      this.tutoresCompatiblesPostulacion.set(Object.fromEntries(
        resultados.map(resultado => [resultado.postulacionId, resultado.tutores])
      ));
    });
  }

  tutoresCompatiblesConPostulacion(post: PostulacionVinculacion): TutorFundacion[] {
    return post.id ? this.tutoresCompatiblesPostulacion()[post.id] || [] : [];
  }

  miPostulacionPendiente(): PostulacionVinculacion | undefined {
    return this.postulaciones().find(p => p.estado === 'Pendiente');
  }

  loadDocumentos(): void {
    if (this.authService.currentUser()?.rol !== 'ESTUDIANTE') return;
    this.documentoService.getMisDocumentosDetalle('VINCULACION').subscribe({
      next: documentos => this.aplicarDocumentos(documentos),
      error: () => {}
    });
  }

  private aplicarDocumentos(documentos: DocEstudiante[]): void {
    this.documentosDetalle.set(documentos);
    const tipos = new Set(documentos
      .filter(doc => !!doc.urlArchivo && doc.estado === 'aprobado')
      .map(doc => doc.tipoDocumento));
    this.cvCargado.set(tipos.has('cv'));
    this.cartaCargada.set(tipos.has('carta'));
    this.cedulaCargada.set(tipos.has('cedula'));
    this.cartaAceptacionCargada.set(tipos.has('carta_aceptacion'));
  }

  private estudianteActual(): Estudiante | undefined {
    const user = this.authService.currentUser();
    return user ? this.estudiantes().find(e => e.usuario?.email === user.email) : undefined;
  }

  misBitacoras(): Bitacora[] {
    const est = this.estudianteActual();
    return est ? this.bitacoras().filter(b => b.estudiante?.id === est.id && !!b.vinculacion) : [];
  }

  bitacorasDeTutorados(): Bitacora[] {
    const vinculacionIds = new Set(this.vinculacionesDelPeriodo().map(v => v.id));
    return this.bitacoras().filter(b => b.vinculacion?.id != null && vinculacionIds.has(b.vinculacion.id));
  }

  seguimientoFiltrado(): SeguimientoVinculacion[] {
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

  vinculacionesActivasTutor(): Vinculacion[] {
    return this.vinculaciones().filter(vinculacion =>
      ['pendiente', 'en_curso'].includes(String(vinculacion.estado || '').toLowerCase()));
  }

  // Módulo del tutor ordenado por periodo académico: por defecto el periodo
  // institucional activo; los anteriores quedan como historial consultable
  periodoTutor = signal('TODOS');
  readonly SIN_PERIODO = SIN_PERIODO;

  periodosTutor(): string[] {
    return periodosDisponibles(this.vinculaciones().map(vinculacion => vinculacion.periodoAcademico));
  }

  private inicializarPeriodoTutor(): void {
    const activo = this.expediente.periodoActual();
    this.periodoTutor.set(this.periodosTutor().includes(activo) ? activo : 'TODOS');
  }

  vinculacionesDelPeriodo(): Vinculacion[] {
    return this.vinculaciones().filter(vinculacion =>
      coincidePeriodo(this.periodoTutor(), vinculacion.periodoAcademico));
  }

  avance(completadas?: number | null, requeridas?: number | null): number {
    return porcentajeAvance(completadas, requeridas);
  }

  // Filtro del historial de asistencias (solo consulta)
  asistenciaVinculacionId = signal<number | null>(null);

  seleccionarAsistenciaVinculacion(valor: string | number | null): void {
    this.asistenciaVinculacionId.set(valor ? Number(valor) : null);
  }

  asistenciasHistorialTutor(): Asistencia[] {
    const id = this.asistenciaVinculacionId();
    const todas = this.asistenciasDeTutorados();
    return id == null ? todas : todas.filter(asistencia => asistencia.vinculacion?.id === id);
  }

  // Pase de lista: fecha y horario compartidos; cada estudiante parte como
  // "Presente" y el tutor solo ajusta los casos especiales
  paseCabecera = { fecha: fechaLocalISO(), horaIngreso: '08:00', horaSalida: '12:00' };
  guardandoPase = signal(false);
  private filasPase = new Map<number, FilaPase>();

  filaPase(vinculacionId: number): FilaPase {
    let fila = this.filasPase.get(vinculacionId);
    if (!fila) {
      fila = nuevaFilaPase();
      this.filasPase.set(vinculacionId, fila);
    }
    return fila;
  }

  asistenciaExistentePase(vinculacionId: number): Asistencia | undefined {
    return asistenciaEnFecha(this.asistencias(), this.paseCabecera.fecha, { vinculacionId });
  }

  filasPorGuardar(): Vinculacion[] {
    return this.vinculacionesActivasTutor().filter(vinculacion => vinculacion.id != null
      && !this.asistenciaExistentePase(vinculacion.id)
      && this.filaPase(vinculacion.id).estado !== 'OMITIR');
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
    const peticiones = porGuardar.map(vinculacion => {
      const fila = this.filaPase(vinculacion.id!);
      return this.asistenciaService.create(asistenciaParaVinculacion(vinculacion.id!, {
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

  saveBitacora(): void {
    const est = this.estudianteActual();
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
        error: err => this.toastService.error(err?.error?.error || 'No se pudo reenviar la bitácora.')
      });
      return;
    }
    const vinculacion = this.vinculaciones().find(v =>
      v.estudiante.id === est.id && (v.estado === 'en_curso' || v.estado === 'pendiente'))
      || this.vinculaciones().find(v => v.estudiante.id === est.id);
    if (!vinculacion) {
      this.toastService.warning('No tienes una vinculación activa para asociar la bitácora.');
      return;
    }

    const nueva: Bitacora = {
      estudiante: est,
      vinculacion,
      parcial: this.newBitacora.parcial,
      fecha: this.newBitacora.fecha,
      actividad: this.newBitacora.actividad,
      horas: this.newBitacora.horas,
      horasExtra: this.newBitacora.horasExtra || 0,
      observaciones: this.newBitacora.observaciones,
      estado: 'pendiente'
    };

    this.guardandoBitacora.set(true);
    this.bitacoraService.create(nueva).pipe(
      finalize(() => this.guardandoBitacora.set(false))
    ).subscribe({
      next: creada => {
        this.bitacoras.set([...this.bitacoras(), creada]);
        this.newBitacora = { fecha: '', actividad: '', horas: 4, horasExtra: 0, parcial: 1, observaciones: '' };
        this.toastService.success('Bitácora de vinculación enviada para revisión.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo registrar la bitácora.')
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

  processBitacora(b: Bitacora, status: 'aprobada' | 'rechazada' | 'requiere_correccion', observacion?: string): void {
    if (!b.id) return;
    this.procesandoBitacoraId.set(b.id);
    this.bitacoraService.update(b.id, {
      estado: status,
      ...((status === 'rechazada' || status === 'requiere_correccion') ? { observacionesTutor: observacion?.trim() } : {})
    }).pipe(
      finalize(() => this.procesandoBitacoraId.set(null))
    ).subscribe({
      next: actualizada => {
        this.bitacoras.set(this.bitacoras().map(item => item.id === b.id ? actualizada : item));
        this.vinculacionService.clearCache();
        this.loadData();
        this.toastService.success(
          status === 'aprobada' ? 'Bitácora aprobada.' :
          status === 'requiere_correccion' ? 'Se solicitó corrección al estudiante.' :
          'Bitácora rechazada.');
      },
      error: () => undefined
    });
  }

  private evaluacionDe(vinculacionId: number | undefined, parcial: number): EvaluacionDetalle | undefined {
    return this.evaluaciones().find(e => e.vinculacion?.id === vinculacionId && e.parcial === parcial);
  }

  private mergeEvaluacion(guardada: EvaluacionDetalle): void {
    const resto = this.evaluaciones().filter(e =>
      !(e.vinculacion?.id === guardada.vinculacion?.id && e.parcial === guardada.parcial));
    this.evaluaciones.set([...resto, guardada]);
  }

  openGradingModal(vinc: Vinculacion, parcial: number): void {
    if (parcial > 1) {
      const previa = this.getGrades(vinc.id!);
      const completo = parcial === 2
        ? (previa.t1 > 0 && previa.c1 > 0)
        : (previa.t2 > 0 && previa.c2 > 0);
      if (!completo) {
        this.toastService.warning(`Primero deben registrarse las notas de tutor y coordinador del Parcial ${parcial - 1}.`);
        return;
      }
    }

    const deadlineStr = parcial === 1 ? this.gradeDeadlines().parcial1 : (parcial === 2 ? this.gradeDeadlines().parcial2 : this.gradeDeadlines().parcial3);
    if (deadlineStr) {
      const deadline = new Date(deadlineStr);
      deadline.setHours(23, 59, 59, 999);
      if (new Date() > deadline) {
        this.toastService.error(`El periodo de evaluación académica para el Parcial ${parcial} expiró el ${deadlineStr}.`);
        return;
      }
    }

    this.selectedVinculacionForGrading.set(vinc);
    this.selectedParcial.set(parcial);
    const ev = this.evaluacionDe(vinc.id, parcial);
    this.tGrade = ev?.notaTutor || 0;
    this.cGrade = ev?.notaCoord || 0;
  }

  guardandoCalificacion = signal(false);

  saveRubricGrade(): void {
    if (this.guardandoCalificacion()) return;
    const vinculacion = this.selectedVinculacionForGrading();
    if (!vinculacion || !vinculacion.id) return;

    const payload: EvaluacionDetalle = {
      vinculacion: { id: vinculacion.id } as Vinculacion,
      parcial: this.selectedParcial()
    };
    if (this.userRole() === 'TUTOR') {
      payload.notaTutor = this.tGrade;
    } else {
      payload.notaCoord = this.cGrade;
    }

    this.guardandoCalificacion.set(true);
    this.evaluacionService.guardar(payload).subscribe({
      next: guardada => {
        this.guardandoCalificacion.set(false);
        this.mergeEvaluacion(guardada);
        this.selectedVinculacionForGrading.set(null);
        this.toastService.success('Calificación de vinculación guardada.');
      },
      error: (err) => {
        this.guardandoCalificacion.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo guardar la calificación.');
      }
    });
  }

  getGrades(vinculacionId: number) {
    const nota = (parcial: number) => {
      const ev = this.evaluacionDe(vinculacionId, parcial);
      return { t: ev?.notaTutor || 0, c: ev?.notaCoord || 0 };
    };
    const n1 = nota(1), n2 = nota(2), n3 = nota(3);
    const p1 = n1.t > 0 || n1.c > 0 ? (n1.t * 0.5 + n1.c * 0.5) : 0;
    const p2 = n2.t > 0 || n2.c > 0 ? (n2.t * 0.5 + n2.c * 0.5) : 0;
    const p3 = n3.t > 0 || n3.c > 0 ? (n3.t * 0.5 + n3.c * 0.5) : 0;
    return { t1: n1.t, c1: n1.c, p1, t2: n2.t, c2: n2.c, p2, t3: n3.t, c3: n3.c, p3, total: p1 + p2 + p3 };
  }

  encuestaHabilitada(vinc: Vinculacion, parcial: number): boolean {
    const ev = this.evaluacionDe(vinc.id, parcial);
    return !!ev && (ev.notaTutor || 0) > 0 && (ev.notaCoord || 0) > 0;
  }

  hasCompletedSurvey(vinculacionId: number | undefined, parcial: number): boolean {
    return !!this.evaluacionDe(vinculacionId, parcial)?.encuestaCompletada;
  }

  notaFinalParcial(vinculacionId: number | undefined, parcial: number): number {
    return this.evaluacionDe(vinculacionId, parcial)?.notaFinal || 0;
  }

  openSurvey(parcial: number): void {
    this.surveyTargetParcial.set(parcial);
    this.surveyAnswers = { tutorScore: 5, projectScore: 5, relevanceScore: 5, clarityScore: 5, comment: '' };
    this.showSurveyModal.set(true);
  }

  guardandoEncuesta = signal(false);

  saveSurvey(): void {
    if (this.guardandoEncuesta()) return;
    const est = this.estudianteActual();
    if (!est || !est.id) return;
    const vinculacion = this.vinculaciones().find(v =>
      v.estudiante.id === est.id && (v.estado === 'en_curso' || v.estado === 'pendiente'))
      || this.vinculaciones().find(v => v.estudiante.id === est.id);
    if (!vinculacion || !vinculacion.id) {
      this.toastService.warning('No tienes una vinculación registrada para asociar la encuesta.');
      return;
    }

    this.guardandoEncuesta.set(true);
    this.evaluacionService.marcarEncuestaVinculacion(vinculacion.id, this.surveyTargetParcial(), {
      satisfaccionTutor: this.surveyAnswers.tutorScore,
      satisfaccionEmpresaProyecto: this.surveyAnswers.projectScore,
      relacionCarrera: this.surveyAnswers.relevanceScore,
      claridadInstrucciones: this.surveyAnswers.clarityScore,
      comentario: this.surveyAnswers.comment
    }).pipe(finalize(() => this.guardandoEncuesta.set(false))).subscribe({
      next: guardada => {
        this.mergeEvaluacion(guardada);
        this.showSurveyModal.set(false);
        this.toastService.success('Encuesta de vinculación guardada.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo guardar la encuesta.')
    });
  }

  seleccionarEstudianteDirecto(estudiante: Estudiante | null): void {
    this.selectedEstudianteObj = estudiante;
    this.cargarTutoresDirecta();
  }

  seleccionarProyectoDirecto(proyecto: Proyecto | null): void {
    this.selectedProyectoObj = proyecto;
    this.cargarTutoresDirecta();
  }

  private cargarTutoresDirecta(tutorPreferidoId?: number): void {
    const estudiante = this.selectedEstudianteObj
      || this.estudiantes().find(item => item.id === Number(this.selectedEstudianteId));
    const proyecto = this.selectedProyectoObj
      || this.proyectos().find(item => item.id === Number(this.selectedProyectoId));
    const fundacionId = proyecto?.fundacion?.id;
    const carrera = estudiante?.carrera;

    this.selectedTutorId = null;
    this.tutoresCompatiblesDirecta.set([]);
    if (!fundacionId || !carrera) return;

    this.cargandoTutoresDirecta.set(true);
    this.tutorFundacionService.getCompatibles(fundacionId, carrera).pipe(
      finalize(() => this.cargandoTutoresDirecta.set(false))
    ).subscribe({
      next: tutores => {
        this.tutoresCompatiblesDirecta.set(tutores);
        if (tutorPreferidoId && tutores.some(item => item.tutorId === tutorPreferidoId)) {
          this.selectedTutorId = tutorPreferidoId;
        }
      },
      error: () => this.toastService.error('No se pudieron consultar los tutores compatibles con la fundación y la carrera.')
    });
  }

  saveDirectAssignment(): void {
    if (this.asignandoDirecta()) return;
    if (!this.selectedEstudianteId || !this.selectedProyectoId || !this.selectedTutorId) {
      this.toastService.warning('Selecciona estudiante, proyecto y tutor.');
      return;
    }
    const est = this.selectedEstudianteObj || this.estudiantes().find(e => e.id === Number(this.selectedEstudianteId));
    const proj = this.selectedProyectoObj || this.proyectos().find(p => p.id === Number(this.selectedProyectoId));
    const vinculoTutor = this.tutoresCompatiblesDirecta()
      .find(item => item.tutorId === Number(this.selectedTutorId));
    const tutor = vinculoTutor ? this.usuarioDesdeVinculoFundacion(vinculoTutor) : undefined;

    if (!est || !proj || !tutor) return;
    if (!this.proyectoTieneConvenioVigente(proj, est.carrera)) {
      this.toastService.warning('La fundación seleccionada no tiene un convenio vigente para la carrera del estudiante.');
      return;
    }

    const newV: Vinculacion = {
      estudiante: est,
      fundacion: proj.fundacion,
      proyecto: proj,
      tutor: tutor,
      estado: 'en_curso',
      horasCompletadas: 0,
      fechaInicio: new Date().toISOString().split('T')[0],
      periodoAcademico: this.expediente.periodoActual()
    };

    this.asignandoDirecta.set(true);
    this.vinculacionService.create(newV).subscribe({
      next: () => {
        this.asignandoDirecta.set(false);
        this.loadData();
        this.selectedEstudianteId = null;
        this.selectedProyectoId = null;
        this.selectedTutorId = null;
        this.tutoresCompatiblesDirecta.set([]);
        this.toastService.success('Asignación de Vinculación directa registrada con éxito.');
      },
      error: (err) => {
        this.asignandoDirecta.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo registrar la asignación de vinculación.');
      }
    });
  }

  proyectoSeleccionadoDirecta(): Proyecto | undefined {
    return this.proyectos().find(p => p.id === Number(this.selectedProyectoId));
  }

  documentoTieneArchivo(type: TipoDocumento): boolean {
    return this.documentosDetalle().some(doc => doc.tipoDocumento === type && !!doc.urlArchivo);
  }

  convenioSeleccionadoVigente(): boolean {
    const proyecto = this.selectedProyectoObj || this.proyectos().find(p => p.id === Number(this.selectedProyectoId));
    const estudiante = this.selectedEstudianteObj || this.estudiantes().find(e => e.id === Number(this.selectedEstudianteId));
    if (!proyecto) return true;
    return this.proyectoTieneConvenioVigente(proyecto, estudiante?.carrera);
  }

  postulacionTieneConvenioVigente(post: PostulacionVinculacion): boolean {
    return this.motivoConvenioPostulacion(post) === null;
  }

  // Motivo preciso para la insignia de la bandeja de postulaciones
  motivoConvenioPostulacion(post: PostulacionVinculacion): string | null {
    return this.expediente.motivoConvenioInvalido(
      this.convenios(), 'FUNDACION', post.proyecto?.fundacion?.id, post.estudiante?.carrera);
  }

  private proyectoTieneConvenioVigente(proyecto: Proyecto, carrera?: string): boolean {
    return this.expediente.motivoConvenioInvalido(
      this.convenios(), 'FUNDACION', proyecto.fundacion?.id, carrera) === null;
  }

  estadoDocumento(type: TipoDocumento): string {
    return this.documentosDetalle().find(doc => doc.tipoDocumento === type)?.estado || 'pendiente';
  }

  observacionDocumento(type: TipoDocumento): string {
    return this.documentosDetalle().find(doc => doc.tipoDocumento === type)?.observacion || '';
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
    return this.estadoRevisionClase(this.estadoDocumento(type));
  }

  estadoRevisionClase(estado?: string): string {
    if (estado === 'aprobado') return 'text-green-700 bg-green-50 border-green-200';
    if (estado === 'requiere_correccion' || estado === 'rechazado') return 'text-rose-700 bg-rose-50 border-rose-200';
    return 'text-amber-700 bg-amber-50 border-amber-200';
  }

  verDocumento(type: TipoDocumento): void {
    const doc = this.documentosDetalle().find(item => item.tipoDocumento === type && !!item.urlArchivo);
    if (!doc?.id) {
      this.toastService.warning('Primero sube el archivo del documento.');
      return;
    }
    this.documentoService.getMiArchivo(doc.id).subscribe({
      next: respuesta => window.open(respuesta.url, '_blank', 'noopener'),
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo abrir el archivo.')
    });
  }

  uploadDoc(type: TipoDocumento, event: Event): void {
    const input = event.target as HTMLInputElement;
    if (this.vinculacionCompletada()) {
      input.value = '';
      this.activeTab.set('mi_progreso');
      this.toastService.warning('Tu proceso de Vinculación ya está completado. Continúa en el módulo de Prácticas.');
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
    this.documentoService.subir(type, 'VINCULACION').pipe(
      switchMap(() => this.documentoService.getMisDocumentosDetalle('VINCULACION')),
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
          this.toastService.success(`Expediente documental enviado exitosamente. Los proyectos se habilitaran el ${this.fechaInicioPostulacion()}.`);
          this.activeTab.set('mi_progreso');
        }
      },
      error: (err) => this.toastService.error(
        err?.error?.error || err?.message || 'No se pudo subir el archivo.'
      )
    });
  }

  asistenciasDeTutorados(): Asistencia[] {
    const vinculacionIds = new Set(this.vinculacionesDelPeriodo().map(vinculacion => vinculacion.id));
    return this.asistencias().filter(asistencia =>
      asistencia.vinculacion?.id != null && vinculacionIds.has(asistencia.vinculacion.id));
  }

  getBitacorasForStudent(studentId: number) {
    return this.bitacoras().filter(b => b.estudiante?.id === studentId);
  }

  getAsistenciasForVinculacion(vinculacionId: number) {
    return this.asistencias().filter(a => a.vinculacion?.id === vinculacionId);
  }

  encuestaDe(parcial: number): EncuestaSatisfaccion | undefined {
    return this.encuestasSeguimiento().find(e => e.parcial === parcial);
  }

  // Las asistencias del estudiante se consultan en el módulo "Mi Expediente"

  // --- Fase 43: Paginación ---
  cargarPaginaLista(): void {
    if (!['ADMIN', 'COORDINADOR'].includes(this.userRole())) return;
    this.cargandoLista.set(true);
    const filtros: any = {};
    if (this.searchText()) filtros.texto = this.searchText();
    if (this.filtroEstado() !== 'TODOS') filtros.estado = this.filtroEstado();
    
    this.vinculacionService.getPaginado(this.paginaLista(), this.tamanoPaginaLista(), filtros).subscribe({
      next: pag => {
        this.vinculaciones.set(pag.contenido);
        this.totalElementosLista.set(pag.totalElementos);
        this.totalPaginasLista.set(pag.totalPaginas);
        this.cargandoLista.set(false);
        this.cargarTutoresParaVinculaciones(pag.contenido);
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

    this.vinculacionService.getSeguimientoPaginado(this.paginaSeguimiento(), this.tamanoPaginaSeguimiento(), filtros).subscribe({
      next: pag => {
        this.seguimiento.set(pag.contenido);
        this.totalElementosSeguimiento.set(pag.totalElementos);
        this.totalPaginasSeguimiento.set(pag.totalPaginas);

        if (pag.contenido.length > 0 && this.activeTab() === 'calificaciones_adm') {
          const obs = pag.contenido.map(s => this.evaluacionService.getByVinculacion(s.vinculacionId));
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

  // El seguimiento paginado es un DTO plano (solo trae vinculacionId); para el
  // modal de calificación se resuelve la vinculación completa.
  abriendoExpedienteId = signal<number | null>(null);

  calificarDesdeSeguimiento(seg: SeguimientoVinculacion, parcial: number): void {
    if (this.abriendoExpedienteId() !== null) return;
    this.abriendoExpedienteId.set(seg.vinculacionId);
    this.vinculacionService.getById(seg.vinculacionId).pipe(
      finalize(() => this.abriendoExpedienteId.set(null))
    ).subscribe(vinc => this.openGradingModal(vinc, parcial));
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

  carrerasParaRevision(): string[] {
    return [...new Set(this.estudiantes().map(e => e.carrera).filter(Boolean))]
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

  // Coordinator Actions
  verSeguimiento(vinc: Vinculacion): void {
    if (!vinc.id || this.inspeccionandoVinculacionId() !== null) return;
    this.inspeccionandoVinculacionId.set(vinc.id);
    this.encuestasSeguimiento.set([]);
    this.comentariosCoordinacionSeguimiento.set([]);
    this.documentosSeguimiento.set([]);
    const consultaGestion = ['ADMIN', 'COORDINADOR'].includes(this.userRole());
    forkJoin({
      encuestas: this.evaluacionService.getEncuestasByVinculacion(vinc.id),
      notas: this.notaCoordinacionService.getByEstudiante(vinc.estudiante.id!),
      bits: consultaGestion ? this.bitacoraService.getByEstudiante(vinc.estudiante.id!) : of([] as Bitacora[]),
      asis: consultaGestion ? this.asistenciaService.getByVinculacion(vinc.id) : of([] as Asistencia[]),
      docs: consultaGestion
        ? this.documentoService.getRevision({ proceso: 'VINCULACION', estudianteId: vinc.estudiante.id })
        : of([] as DocEstudiante[])
    }).pipe(
      finalize(() => this.inspeccionandoVinculacionId.set(null))
    ).subscribe({
      next: ({ encuestas, notas, bits, asis, docs }) => {
        this.encuestasSeguimiento.set(encuestas);
        this.comentariosCoordinacionSeguimiento.set(
          notas.filter(n => n.vinculacion?.id === vinc.id));
        this.documentosSeguimiento.set(docs);
        if (consultaGestion) {
          const bitsVinc = bits.filter(b => b.vinculacion?.id === vinc.id);
          const existingBits = this.bitacoras().filter(b => b.vinculacion?.id !== vinc.id);
          const existingAsis = this.asistencias().filter(a => a.vinculacion?.id !== vinc.id);
          this.bitacoras.set([...existingBits, ...bitsVinc]);
          this.asistencias.set([...existingAsis, ...asis]);
        }
        this.selectedVinculacion.set(vinc);
        this.showSeguimientoModal.set(true);
      },
      error: (err) => {
        this.toastService.error(err?.error?.error || 'No se pudo cargar la inspección del expediente.');
      }
    });
  }

  docSeguimiento(tipo: TipoDocumento): DocEstudiante | undefined {
    return this.documentosSeguimiento().find(d => d.tipoDocumento === tipo);
  }

  verArchivoSeguimiento(tipo: TipoDocumento): void {
    const doc = this.docSeguimiento(tipo);
    if (!doc?.id || !doc.urlArchivo || this.abriendoDocumentoSeguimiento()) return;
    this.abriendoDocumentoSeguimiento.set(tipo);
    this.documentoService.getArchivoRevision(doc.id).subscribe({
      next: respuesta => {
        this.abriendoDocumentoSeguimiento.set(null);
        window.open(respuesta.url, '_blank', 'noopener');
      },
      error: (err) => {
        this.abriendoDocumentoSeguimiento.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo abrir el archivo.');
      }
    });
  }

  abrirHistorialPersonal(vinc: Vinculacion): void {
    this.historialPersonal.set(vinc);
    // Se recarga siempre (no se reusa el cache en memoria): el historial puede
    // haber cambiado por una accion reciente (bitacora revisada, nota, etc.)
    // dentro de la misma sesion.
    this.historialPersonalSeleccionado.set(null);
    this.lineaTiempoPersonalCargando.set(true);
    this.lineaTiempoService.getVinculacionMe().subscribe({
      next: historiales => {
        this.lineaTiempoPersonal.set(historiales);
        this.lineaTiempoPersonalCargando.set(false);
        this.seleccionarHistorialPersonal(vinc);
      },
      error: err => {
        this.lineaTiempoPersonalCargando.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo cargar tu historial de vinculación.');
      }
    });
  }

  private seleccionarHistorialPersonal(vinc: Vinculacion): void {
    const historial = this.lineaTiempoPersonal().find(item => item.expedienteId === vinc.id);
    this.historialPersonalSeleccionado.set(historial || null);
  }

  cerrarHistorialPersonal(): void {
    this.historialPersonal.set(null);
    this.historialPersonalSeleccionado.set(null);
  }

  estadoTexto(estado?: string): string {
    return estadoExpedienteTexto(estado);
  }

  // El backend solo permite editar tutor/horas de un expediente cerrado a un
  // ADMIN con justificación (CierreExpedienteComponent.validarModificacionPermitida);
  // el selector se deshabilita para COORDINADOR para no ofrecer una accion que
  // siempre va a fallar con 400.
  vinculacionCerrada(vinc: Vinculacion): boolean {
    const estado = (vinc.estado || '').toLowerCase();
    return ['completado', 'reprobado', 'retirado'].includes(estado);
  }

  tieneVinculacionActiva(vinculaciones: Pick<Vinculacion, 'estado'>[]): boolean {
    return tieneVinculacionActiva(vinculaciones);
  }

  postular(proj: Proyecto): void {
    if (!this.cvCargado() || !this.cartaCargada() || !this.cedulaCargada()) {
      this.toastService.error('Debes subir toda la documentación obligatoria (CV, Carta de solicitud, Copia de cédula) antes de postularte.');
      return;
    }
    if (this.miPostulacionPendiente()) {
      this.toastService.warning('Ya tienes una postulación de vinculación pendiente.');
      this.activeTab.set('mi_progreso');
      return;
    }
    if (!proj.id || this.postulandoProyectoId() !== null) return;
    this.postulandoProyectoId.set(proj.id);
    this.vinculacionService.postularProyecto(proj.id).subscribe({
      next: (creada) => {
        this.postulandoProyectoId.set(null);
        this.postulaciones.set([...this.postulaciones(), creada]);
        this.activeTab.set('mi_progreso');
        this.toastService.success('Postulación al proyecto de vinculación enviada con éxito.');
      },
      error: (err) => {
        this.postulandoProyectoId.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo registrar la postulación.');
      }
    });
  }

  aprobarPostulacion(post: PostulacionVinculacion): void {
    if (!post.id || this.procesandoPostulacionId() !== null) return;
    if (!this.postulacionTieneConvenioVigente(post)) {
      this.toastService.warning('La fundación no tiene un convenio vigente para la carrera del estudiante.');
      return;
    }
    const tutorId = this.tutorPorPostulacion[post.id];
    const tutor = this.tutores().find(t => t.id === Number(tutorId));
    if (!tutor) {
      this.toastService.warning('Selecciona un tutor académico antes de aprobar.');
      return;
    }
    // Las horas oficiales las toma el backend del proyecto; solo viaja el tutor
    this.procesandoPostulacionId.set(post.id);
    this.vinculacionService.aprobarPostulacion(post.id, { tutor }).subscribe({
      next: (actualizada) => {
        this.procesandoPostulacionId.set(null);
        this.postulaciones.set(this.postulaciones().map(p => p.id === actualizada.id ? actualizada : p));
        this.loadData();
        this.toastService.success('Postulación aprobada y vinculación oficial creada.');
      },
      error: (err) => {
        this.procesandoPostulacionId.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo aprobar la postulación.');
      }
    });
  }

  rechazarPostulacion(post: PostulacionVinculacion): void {
    if (!post.id || this.procesandoPostulacionId() !== null) return;
    const observacion = this.observacionRechazo[post.id] || '';
    this.procesandoPostulacionId.set(post.id);
    this.vinculacionService.rechazarPostulacion(post.id, observacion).subscribe({
      next: (actualizada) => {
        this.procesandoPostulacionId.set(null);
        this.postulaciones.set(this.postulaciones().map(p => p.id === actualizada.id ? actualizada : p));
        this.toastService.success('Postulación rechazada.');
      },
      error: (err) => {
        this.procesandoPostulacionId.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo rechazar la postulación.');
      }
    });
  }

  listaParaCierre(vinc: Vinculacion): boolean {
    const seguimiento = this.seguimiento().find(item => item.vinculacionId === vinc.id);
    return (vinc.estado || '').toLowerCase() === 'en_curso' && !!seguimiento?.listoParaCierre;
  }

  cerrarVinculacion(vinc: Vinculacion): void {
    if (!vinc.id || this.cerrandoVinculacionId() !== null) return;
    this.cerrandoVinculacionId.set(vinc.id);
    this.vinculacionService.cerrar(vinc.id).subscribe({
      next: () => {
        this.cerrandoVinculacionId.set(null);
        this.loadData();
        this.toastService.success('Vinculación cerrada formalmente.');
      },
      error: (err) => {
        this.cerrandoVinculacionId.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo cerrar la vinculación.');
      }
    });
  }

  cerrarVinculacionId(id: number): void {
    this.cerrarVinculacion({ id } as Vinculacion);
  }

  // Pestaña Proyectos (gestión): formulario compartido con Fundaciones & Proyectos
  openCreateProyecto(): void {
    this.proyectoEnEdicion = null;
    this.proyectoDuplicando = false;
    this.showProyectoModal.set(true);
  }

  openEditProyecto(proyecto: Proyecto): void {
    this.proyectoEnEdicion = proyecto;
    this.proyectoDuplicando = false;
    this.showProyectoModal.set(true);
  }

  // Fase 42: duplica descripción y condiciones del proyecto, nunca sus cupos
  duplicarProyecto(proyecto: Proyecto): void {
    this.proyectoEnEdicion = proyecto;
    this.proyectoDuplicando = true;
    this.showProyectoModal.set(true);
  }

  proyectoGuardado(): void {
    this.showProyectoModal.set(false);
    this.proyectoEnEdicion = null;
    this.proyectoDuplicando = false;
    this.loadData();
  }

  // Fase 42: pausar/reactivar un proyecto sin perder su reserva de cupos
  async togglePausaProyecto(proyecto: Proyecto): Promise<void> {
    if (!proyecto.id || this.pausandoProyectoId() !== null) return;
    const pausar = proyecto.estado !== false;
    const mensaje = pausar
      ? `¿Pausar el proyecto "${proyecto.nombre}"? Los estudiantes dejarán de verlo y no se admitirán nuevas postulaciones; los cupos reservados se conservan.`
      : `¿Reactivar el proyecto "${proyecto.nombre}"? Volverá a aceptar postulaciones con sus cupos disponibles.`;
    if (!(await this.confirmService.confirm(mensaje))) return;
    this.pausandoProyectoId.set(proyecto.id);
    this.proyectoService.update(proyecto.id, { ...proyecto, estado: !pausar }).subscribe({
      next: () => {
        this.pausandoProyectoId.set(null);
        this.loadData();
        this.toastService.success(pausar ? 'Proyecto pausado.' : 'Proyecto reactivado.');
      },
      error: (err) => {
        this.pausandoProyectoId.set(null);
        this.toastService.error(err?.error?.error || 'No se pudo cambiar el estado del proyecto.');
      }
    });
  }

  // Fase 42: liberación única y justificada del cupo de un retiro
  abrirLiberarCupo(vinc: Vinculacion): void {
    this.motivoLiberacion = '';
    this.liberandoVinculacion.set(vinc);
  }

  puedeLiberarCupo(vinc: Vinculacion): boolean {
    return (this.userRole() === 'ADMIN' || this.userRole() === 'COORDINADOR')
      && (vinc.estado || '').toLowerCase() === 'retirado'
      && !vinc.cupoLiberado;
  }

  confirmarLiberarCupo(): void {
    const vinc = this.liberandoVinculacion();
    if (!vinc?.id || this.liberandoCupo()) return;
    const motivo = this.motivoLiberacion.trim();
    if (motivo.length < 10) {
      this.toastService.warning('El motivo de liberación debe tener al menos 10 caracteres.');
      return;
    }
    this.liberandoCupo.set(true);
    this.vinculacionService.liberarCupo(vinc.id, motivo).subscribe({
      next: () => {
        this.liberandoCupo.set(false);
        this.liberandoVinculacion.set(null);
        this.loadData();
        this.toastService.success('Cupo liberado: el proyecto vuelve a tener un cupo disponible para reemplazo.');
      },
      error: (err) => {
        this.liberandoCupo.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo liberar el cupo.');
      }
    });
  }

  carrerasDeProyecto(proyecto: Proyecto): string {
    return (proyecto.carreras || []).map(item => item.carrera.nombre).join(', ');
  }

  modalidadTexto(modalidad?: string): string {
    return modalidadProyectoTexto(modalidad);
  }

  // Acta en modal propio: window.open tras la respuesta HTTP era bloqueado
  // por el navegador (no es un gesto directo del usuario).
  actaAbierta = signal<{ titulo: string; acta: ActaCierre } | null>(null);

  abrirActaVinculacion(id: number): void {
    if (this.cargandoActaVinculacionId() !== null) return;
    this.cargandoActaVinculacionId.set(id);
    this.vinculacionService.getActa(id).pipe(
      finalize(() => this.cargandoActaVinculacionId.set(null))
    ).subscribe({
      next: acta => this.actaAbierta.set({ titulo: 'Acta de cierre de vinculación', acta: acta as ActaCierre }),
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo abrir el acta de cierre.')
    });
  }
}
