import { Component, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UsuarioService, Usuario } from '../../core/services/usuario.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { Carrera, CarreraService } from '../../core/services/carrera.service';
import { AlcanceCoordinadorAdmin, CoordinadorService } from '../../core/services/coordinador.service';
import { AuthService } from '../../core/services/auth.service';
import { TAMANO_PAGINA_DEFECTO } from '../../core/services/paginacion';
import { PaginadorComponent } from '../shared/paginador/paginador';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

export function esMismoUsuario(usuario: Usuario, emailActual?: string): boolean {
  return Boolean(emailActual && usuario.email.trim().toLowerCase() === emailActual.trim().toLowerCase());
}

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginadorComponent],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css'
})
export class UsuariosComponent implements OnDestroy {
  private readonly usuarioService = inject(UsuarioService);
  private readonly toastService = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly carreraService = inject(CarreraService);
  private readonly coordinadorService = inject(CoordinadorService);
  private readonly authService = inject(AuthService);

  usuarios = signal<Usuario[]>([]);
  carrerasCatalogo = signal<Carrera[]>([]);
  alcancesCoordinadores = signal<Record<number, AlcanceCoordinadorAdmin>>({});

  // Fase 43: paginación y filtros resueltos en el backend
  readonly tamanoPagina = TAMANO_PAGINA_DEFECTO;
  pagina = signal(0);
  totalPaginas = signal(0);
  totalElementos = signal(0);
  filtroTexto = '';
  filtroRol = 'TODOS';
  filtroActivo = 'TODOS';
  private timerBusqueda: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
    if (this.timerBusqueda) clearTimeout(this.timerBusqueda);
  }

  // Alcance del coordinador (ADMIN lo configura por usuario)
  showAlcanceModal = signal(false);
  alcanceUsuario = signal<Usuario | null>(null);
  guardandoAlcance = signal(false);
  alcance: AlcanceCoordinadorAdmin = { tipo: 'AMBOS', carreras: [] };
  loading = signal(true);
  isRefreshing = signal(false);
  showModal = signal(false);
  guardandoUsuario = signal(false);

  // Form Model
  currentUsuario: Usuario = this.emptyUsuario();
  isEdit = false;
  selectedRoleId: number | null = null;

  availableRoles = [
    { id: 1, codigo: 'ADMIN', nombre: 'Administrador' },
    { id: 2, codigo: 'COORDINADOR', nombre: 'Coordinador' },
    { id: 3, codigo: 'TUTOR', nombre: 'Tutor' },
    { id: 4, codigo: 'ESTUDIANTE', nombre: 'Estudiante' }
  ];

  constructor() {
    this.loadUsuarios(true);
    this.carreraService.getAll().subscribe({
      next: carreras => this.carrerasCatalogo.set(carreras.filter(c => c.activo !== false)),
      error: () => this.carrerasCatalogo.set([])
    });
  }

  rolPrincipal(usuario: Usuario): string {
    return usuario.roles && usuario.roles.length > 0 ? usuario.roles[0].codigo : '';
  }

  rolSeleccionadoCodigo(): string {
    const rol = this.availableRoles.find(r => r.id === Number(this.selectedRoleId));
    return rol?.codigo || '';
  }

  // --- Alcance del coordinador ---

  openAlcanceModal(usuario: Usuario): void {
    if (!usuario.id) return;
    this.alcanceUsuario.set(usuario);
    const guardado = this.alcancesCoordinadores()[usuario.id];
    this.alcance = guardado
      ? { tipo: guardado.tipo, carreras: [...guardado.carreras] }
      : { tipo: 'AMBOS', carreras: [] };
    this.showAlcanceModal.set(true);
    this.coordinadorService.getAlcance(usuario.id).subscribe({
      next: alcance => {
        this.alcance = {
          tipo: alcance.tipo || 'AMBOS',
          carreras: [...(alcance.carreras || [])]
        };
        this.guardarAlcanceLocal(usuario.id!, this.alcance);
      },
      // Sin filas todavía: se configura desde cero
      error: () => {}
    });
  }

  carreraAsignadaAlcance(nombre: string): boolean {
    return this.alcance.carreras.includes(nombre);
  }

  toggleCarreraAlcance(nombre: string): void {
    this.alcance.carreras = this.carreraAsignadaAlcance(nombre)
      ? this.alcance.carreras.filter(c => c !== nombre)
      : [...this.alcance.carreras, nombre];
  }

  saveAlcance(): void {
    const usuario = this.alcanceUsuario();
    if (!usuario?.id) return;
    if (this.alcance.carreras.length === 0) {
      this.toastService.warning('Asigna al menos una carrera: sin carreras el coordinador no verá ningún estudiante.');
      return;
    }
    this.guardandoAlcance.set(true);
    this.coordinadorService.setAlcance(usuario.id, this.alcance).subscribe({
      next: () => {
        this.guardandoAlcance.set(false);
        this.showAlcanceModal.set(false);
        this.guardarAlcanceLocal(usuario.id!, this.alcance);
        this.toastService.success(`Alcance de ${usuario.nombre} ${usuario.apellido} actualizado.`);
      },
      error: (err) => {
        this.guardandoAlcance.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo actualizar el alcance del coordinador.');
      }
    });
  }

  resumenAlcance(usuario: Usuario): string {
    if (!usuario.id) return 'Sin configurar';
    const alcance = this.alcancesCoordinadores()[usuario.id];
    if (!alcance) return 'Cargando alcance...';
    const tipo = alcance.tipo === 'PRACTICAS'
      ? 'Prácticas'
      : alcance.tipo === 'VINCULACION' ? 'Vinculación' : 'Ambos procesos';
    if (alcance.carreras.length === 0) return `${tipo} · Sin carreras`;
    const visibles = alcance.carreras.slice(0, 2).join(', ');
    const adicionales = alcance.carreras.length > 2 ? ` +${alcance.carreras.length - 2}` : '';
    return `${tipo} · ${visibles}${adicionales}`;
  }

  detalleAlcance(usuario: Usuario): string {
    if (!usuario.id) return '';
    return this.alcancesCoordinadores()[usuario.id]?.carreras.join(', ') || 'Sin carreras asignadas';
  }

  loadUsuarios(isInitial = false, pagina = this.pagina()): void {
    if (isInitial) this.loading.set(true); else this.isRefreshing.set(true);
    this.usuarioService.getPaginado(pagina, this.tamanoPagina, {
      texto: this.filtroTexto,
      rol: this.filtroRol,
      activo: this.filtroActivo
    }).subscribe({
      next: (data) => {
        this.usuarios.set(data.contenido);
        this.pagina.set(data.pagina);
        this.totalPaginas.set(data.totalPaginas);
        this.totalElementos.set(data.totalElementos);
        this.loadAlcancesCoordinadores(data.contenido);
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      },
      error: () => {
        if (isInitial) this.loading.set(false); else this.isRefreshing.set(false);
      }
    });
  }

  cambiarPagina(pagina: number): void {
    this.loadUsuarios(false, pagina);
  }

  // Búsqueda con pequeña espera para no disparar una petición por tecla
  buscarConRetardo(): void {
    if (this.timerBusqueda) clearTimeout(this.timerBusqueda);
    this.timerBusqueda = setTimeout(() => this.aplicarFiltros(), 350);
  }

  aplicarFiltros(): void {
    this.loadUsuarios(false, 0);
  }

  openCreateModal(): void {
    this.currentUsuario = this.emptyUsuario();
    this.selectedRoleId = null;
    this.isEdit = false;
    this.showModal.set(true);
  }

  openEditModal(usuario: Usuario): void {
    this.currentUsuario = { ...usuario };
    this.selectedRoleId = usuario.roles && usuario.roles.length > 0 ? usuario.roles[0].id : null;
    this.isEdit = true;
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
  }

  saveUsuario(): void {
    if (this.guardandoUsuario()) return;
    if (!this.currentUsuario.cedula || !this.currentUsuario.nombre || !this.currentUsuario.apellido || !this.currentUsuario.email) {
      this.toastService.warning('Por favor, completa los campos requeridos.');
      return;
    }
    this.currentUsuario.cedula = this.currentUsuario.cedula.trim();
    if (this.currentUsuario.cedula.length > 20) {
      this.toastService.warning('La cédula no puede superar 20 caracteres.');
      return;
    }

    if (this.selectedRoleId) {
      const roleObj = this.availableRoles.find(r => r.id === Number(this.selectedRoleId));
      if (roleObj) {
        this.currentUsuario.roles = [{
          id: roleObj.id,
          codigo: roleObj.codigo,
          nombre: roleObj.nombre,
          activo: true
        }];
      }
    } else {
      this.currentUsuario.roles = [];
    }

    // El área del tutor solo aplica al rol TUTOR (por defecto AMBOS)
    if (this.rolSeleccionadoCodigo() === 'TUTOR') {
      this.currentUsuario.tutorTipo = this.currentUsuario.tutorTipo || 'AMBOS';
    } else {
      this.currentUsuario.tutorTipo = null;
    }

    this.guardandoUsuario.set(true);
    if (this.isEdit && this.currentUsuario.id) {
      this.usuarioService.update(this.currentUsuario.id, this.currentUsuario).subscribe({
        next: () => {
          this.guardandoUsuario.set(false);
          this.loadUsuarios();
          this.closeModal();
          this.toastService.success('Usuario actualizado correctamente.');
        },
        error: (err) => {
          this.guardandoUsuario.set(false);
          this.toastService.error(err?.error?.error || 'No se pudo actualizar el usuario.');
        }
      });
    } else {
      this.usuarioService.create(this.currentUsuario).subscribe({
        next: () => {
          this.guardandoUsuario.set(false);
          this.loadUsuarios();
          this.closeModal();
          this.toastService.success('Usuario creado correctamente.');
        },
        error: (err) => {
          this.guardandoUsuario.set(false);
          this.toastService.error(err?.error?.error || 'No se pudo crear el usuario.');
        }
      });
    }
  }

  esUsuarioActual(usuario: Usuario): boolean {
    return esMismoUsuario(usuario, this.authService.currentUser()?.email);
  }

  async desactivarUsuario(usuario: Usuario): Promise<void> {
    if (!usuario.id) return;
    if (this.esUsuarioActual(usuario)) {
      this.toastService.warning('No puedes desactivar tu propia cuenta.');
      return;
    }
    if (await this.confirmService.confirm(`¿Desactivar la cuenta de ${usuario.nombre} ${usuario.apellido}? Su historial se conservará.`)) {
      this.usuarioService.desactivar(usuario.id).subscribe({
        next: () => {
          this.toastService.success('Usuario desactivado.');
          this.loadUsuarios();
        }
      });
    }
  }

  reactivarUsuario(usuario: Usuario): void {
    if (usuario.id && !usuario.activo) {
      const updated = { ...usuario, activo: true };
      this.usuarioService.update(usuario.id, updated).subscribe({
        next: () => {
          this.toastService.success('Usuario reactivado.');
          this.loadUsuarios();
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo reactivar el usuario.')
      });
    }
  }

  private loadAlcancesCoordinadores(usuarios: Usuario[]): void {
    const coordinadores = usuarios.filter(usuario => usuario.id && this.rolPrincipal(usuario) === 'COORDINADOR');
    if (coordinadores.length === 0) {
      this.alcancesCoordinadores.set({});
      return;
    }
    forkJoin(coordinadores.map(usuario => this.coordinadorService.getAlcance(usuario.id!).pipe(
      catchError(() => of({ tipo: 'AMBOS', carreras: [] } as AlcanceCoordinadorAdmin))
    ))).subscribe(alcances => {
      const porUsuario: Record<number, AlcanceCoordinadorAdmin> = {};
      coordinadores.forEach((usuario, index) => {
        porUsuario[usuario.id!] = alcances[index];
      });
      this.alcancesCoordinadores.set(porUsuario);
    });
  }

  private guardarAlcanceLocal(usuarioId: number, alcance: AlcanceCoordinadorAdmin): void {
    this.alcancesCoordinadores.update(actual => ({
      ...actual,
      [usuarioId]: { tipo: alcance.tipo, carreras: [...alcance.carreras] }
    }));
  }

  private emptyUsuario(): Usuario {
    return {
      cedula: '',
      nombre: '',
      apellido: '',
      email: '',
      passwordHash: 'Usuario123#', // Default password for new users
      activo: true,
      primerLogin: true,
      fuente: 'MANUAL',
      externalId: '',
      tutorTipo: null,
      roles: []
    };
  }
}
