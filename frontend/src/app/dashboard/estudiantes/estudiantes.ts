import { Component, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EstudianteService, Estudiante } from '../../core/services/estudiante.service';
import { UsuarioService, Usuario } from '../../core/services/usuario.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotaAcademicaService, NotaAcademica } from '../../core/services/nota-academica.service';
import { DocumentoService, DocumentoRequerido } from '../../core/services/documento.service';
import { Carrera, CarreraService } from '../../core/services/carrera.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import { TAMANO_PAGINA_DEFECTO } from '../../core/services/paginacion';
import { PaginadorComponent } from '../shared/paginador/paginador';

@Component({
  selector: 'app-estudiantes',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginadorComponent],
  templateUrl: './estudiantes.html',
  styleUrl: './estudiantes.css'
})
export class EstudiantesComponent implements OnDestroy {
  private readonly estudianteService = inject(EstudianteService);
  private readonly usuarioService = inject(UsuarioService);
  readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly notaAcademicaService = inject(NotaAcademicaService);
  private readonly documentoService = inject(DocumentoService);
  private readonly carreraService = inject(CarreraService);
  private readonly expedienteService = inject(ExpedienteService);

  // El alta, la baja y el cambio de matrícula/carrera/usuario son de ADMIN;
  // el coordinador solo actualiza semestre y periodo dentro de su alcance.
  readonly esAdmin = this.authService.getRole() === 'ADMIN';

  estudiantes = signal<Estudiante[]>([]);
  carrerasCatalogo = signal<Carrera[]>([]);
  candidatos = signal<Usuario[]>([]);
  notasAcademicas = signal<NotaAcademica[]>([]);
  documentosRequeridos = signal<DocumentoRequerido[]>([]);
  loading = signal(true);
  isRefreshing = signal(false);
  showModal = signal(false);
  filtroNotas = signal('PENDIENTE');
  filtroNotasPeriodo = signal('TODOS');
  filtroNotasTexto = '';
  currentDocumentoRequerido: DocumentoRequerido = this.emptyDocumentoRequerido();

  // Fase 43: paginación y filtros resueltos en el backend (incluido el
  // alcance del coordinador, que ya no se filtra en el cliente)
  readonly tamanoPagina = TAMANO_PAGINA_DEFECTO;
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);
  filtroTexto = '';
  filtroCarrera = 'TODOS';
  filtroSemestre = '';
  private timerBusqueda: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
    if (this.timerBusqueda) clearTimeout(this.timerBusqueda);
  }

  // Form Model
  currentEstudiante: Estudiante = this.emptyEstudiante();
  selectedUsuarioId: number | null = null;
  isEdit = false;

  constructor() {
    this.loadData(true);
    this.carreraService.getAll().subscribe({
      next: carreras => this.carrerasCatalogo.set(carreras.filter(c => c.activo !== false)),
      error: () => this.carrerasCatalogo.set([])
    });
  }

  carreraEnCatalogo(nombre: string): boolean {
    return this.carrerasCatalogo().some(c => c.nombre === nombre);
  }

  loadData(isInitial = false, pagina = this.pagina()): void {
    if (isInitial) this.loading.set(true); else this.isRefreshing.set(true);
    this.estudianteService.getPaginado(pagina, this.tamanoPagina, {
      texto: this.filtroTexto,
      carrera: this.filtroCarrera,
      semestre: this.filtroSemestre
    }).subscribe({
      next: (data) => {
        this.estudiantes.set(data.contenido);
        this.pagina.set(data.pagina);
        this.totalPaginas.set(data.totalPaginas);
        this.totalElementos.set(data.totalElementos);
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      },
      error: () => {
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      }
    });

    // Solo ADMIN llena el selector de "Nuevo estudiante"; el coordinador no
    // tiene acceso a /api/usuarios (evita el 403 al entrar a la pantalla).
    if (this.esAdmin) {
      this.usuarioService.getCandidatosEstudiante().subscribe({
        next: (data: Usuario[]) => this.candidatos.set(data),
        error: () => this.candidatos.set([])
      });
    }

    this.notaAcademicaService.getAll().subscribe({
      next: (data: NotaAcademica[]) => {
        const user = this.authService.currentUser();
        if (user && user.rol === 'COORDINADOR') {
          const assigned = this.authService.carrerasAsignadas();
          this.notasAcademicas.set(data.filter(nota => assigned.includes(nota.estudiante?.carrera)));
        } else {
          this.notasAcademicas.set(data);
        }
      },
      error: () => this.notasAcademicas.set([])
    });

    this.documentoService.getRequeridos().subscribe({
      next: docs => this.documentosRequeridos.set(docs),
      error: () => this.documentosRequeridos.set([])
    });
  }

  cambiarPagina(pagina: number): void {
    this.loadData(false, pagina);
  }

  // Búsqueda con pequeña espera para no disparar una petición por tecla
  buscarConRetardo(): void {
    if (this.timerBusqueda) clearTimeout(this.timerBusqueda);
    this.timerBusqueda = setTimeout(() => this.aplicarFiltros(), 350);
  }

  aplicarFiltros(): void {
    this.loadData(false, 0);
  }

  openCreateModal(): void {
    if (!this.esAdmin) return;
    this.currentEstudiante = this.emptyEstudiante();
    this.selectedUsuarioId = null;
    this.isEdit = false;
    this.showModal.set(true);
  }

  openEditModal(estudiante: Estudiante): void {
    this.currentEstudiante = { ...estudiante };
    this.selectedUsuarioId = estudiante.usuario.id || null;
    this.isEdit = true;
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  saveEstudiante(): void {
    // Coordinador: formulario reducido — solo semestre y periodo de un
    // expediente existente; la identidad institucional no viaja alterada.
    if (!this.esAdmin) {
      if (!this.isEdit || !this.currentEstudiante.id) return;
      if (!this.currentEstudiante.semestre || !this.currentEstudiante.periodoAcademico) {
        this.toastService.warning('Completa el semestre y el periodo académico.');
        return;
      }
      this.actualizarEstudiante();
      return;
    }

    if (!this.currentEstudiante.matricula || !this.currentEstudiante.carrera) {
      this.toastService.warning('Por favor, completa los campos requeridos.');
      return;
    }

    if (this.isEdit && this.currentEstudiante.id) {
      // Al editar se conserva la cuenta vinculada actual
      this.actualizarEstudiante();
      return;
    }

    if (!this.selectedUsuarioId) {
      this.toastService.warning('Selecciona la cuenta de usuario del estudiante.');
      return;
    }
    const usuario = this.candidatos().find((u: Usuario) => u.id === Number(this.selectedUsuarioId));
    if (!usuario) return;
    this.currentEstudiante.usuario = usuario;

    this.estudianteService.create(this.currentEstudiante).subscribe({
      next: () => {
        this.loadData();
        this.closeModal();
        this.toastService.success('Estudiante registrado correctamente.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo registrar el estudiante.')
    });
  }

  private actualizarEstudiante(): void {
    this.estudianteService.update(this.currentEstudiante.id!, this.currentEstudiante).subscribe({
      next: () => {
        this.loadData();
        this.closeModal();
        this.toastService.success('Estudiante actualizado correctamente.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo actualizar el estudiante.')
    });
  }

  async deleteEstudiante(id: number): Promise<void> {
    if (!this.esAdmin) return;
    if (await this.confirmService.confirm('¿Estás seguro de que deseas eliminar este estudiante?')) {
      this.estudianteService.delete(id).subscribe({
        next: () => {
          this.loadData();
          this.toastService.success('Estudiante eliminado.');
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo eliminar el estudiante.')
      });
    }
  }

  notasFiltradas(): NotaAcademica[] {
    const filtro = this.filtroNotas();
    const periodo = this.filtroNotasPeriodo();
    const texto = this.filtroNotasTexto.trim().toLowerCase();
    return this.notasAcademicas().filter(nota => {
      if (filtro !== 'TODOS' && nota.estado !== filtro) return false;
      if (periodo !== 'TODOS' && nota.periodoAcademico !== periodo) return false;
      if (texto) {
        const estudiante = nota.estudiante;
        const haystack = [
          estudiante?.usuario?.nombre, estudiante?.usuario?.apellido,
          estudiante?.usuario?.email, nota.emailInstitucional,
          estudiante?.matricula, estudiante?.carrera
        ].filter(Boolean).join(' ').toLowerCase();
        if (!haystack.includes(texto)) return false;
      }
      return true;
    });
  }

  periodosNotas(): string[] {
    const periodos = new Set<string>();
    for (const nota of this.notasAcademicas()) {
      if (nota.periodoAcademico) periodos.add(nota.periodoAcademico);
    }
    return [...periodos].sort((a, b) => b.localeCompare(a));
  }

  verificarNota(nota: NotaAcademica): void {
    if (!nota.id) return;
    this.notaAcademicaService.verificar(nota.id, 'VERIFICADO').subscribe({
      next: actualizada => {
        this.notasAcademicas.set(this.notasAcademicas().map(item =>
          item.id === actualizada.id ? actualizada : item));
        this.toastService.success('Nota académica verificada para meritocracia.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo verificar la nota académica.')
    });
  }

  rechazarNota(nota: NotaAcademica): void {
    if (!nota.id) return;
    const observaciones = nota.observaciones || 'Rechazada por coordinación académica.';
    this.notaAcademicaService.verificar(nota.id, 'RECHAZADO', observaciones).subscribe({
      next: actualizada => {
        this.notasAcademicas.set(this.notasAcademicas().map(item =>
          item.id === actualizada.id ? actualizada : item));
        this.toastService.success('Nota académica rechazada con observación.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo rechazar la nota académica.')
    });
  }

  saveDocumentoRequerido(): void {
    if (!this.currentDocumentoRequerido.proceso || !this.currentDocumentoRequerido.tipoDocumento || !this.currentDocumentoRequerido.nombre) {
      this.toastService.warning('Completa proceso, tipo y nombre del documento requerido.');
      return;
    }
    this.documentoService.saveRequerido(this.currentDocumentoRequerido).subscribe({
      next: guardado => {
        const resto = this.documentosRequeridos().filter(doc => doc.id !== guardado.id);
        this.documentosRequeridos.set([...resto, guardado]);
        this.currentDocumentoRequerido = this.emptyDocumentoRequerido();
        this.toastService.success('Documento requerido guardado.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo guardar el documento requerido.')
    });
  }

  editDocumentoRequerido(doc: DocumentoRequerido): void {
    this.currentDocumentoRequerido = { ...doc };
  }

  deleteDocumentoRequerido(doc: DocumentoRequerido): void {
    if (!doc.id) return;
    this.documentoService.deleteRequerido(doc.id).subscribe({
      next: () => {
        this.documentosRequeridos.set(this.documentosRequeridos().filter(item => item.id !== doc.id));
        this.toastService.success('Documento requerido desactivado.');
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudo desactivar el documento requerido.')
    });
  }

  private emptyEstudiante(): Estudiante {
    return {
      usuario: {} as Usuario,
      matricula: '',
      carrera: '',
      semestre: 1,
      // Periodo institucional activo (con respaldo calculado por fecha)
      periodoAcademico: this.expedienteService.periodoActual()
    };
  }

  private emptyDocumentoRequerido(): DocumentoRequerido {
    return {
      proceso: 'PRACTICAS',
      carrera: '',
      etapa: '',
      tipoDocumento: 'carta_aceptacion',
      nombre: 'Carta de aceptación del cupo',
      descripcion: '',
      obligatorio: false,
      momento: 'POST_ACEPTACION',
      activo: true
    };
  }
}
