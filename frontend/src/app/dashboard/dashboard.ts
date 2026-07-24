import { Component, OnDestroy, inject, signal } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../core/services/auth.service';
import { NotificacionService, Notificacion } from '../core/services/notificacion.service';
import { ToastService } from '../core/services/toast.service';
import { ExpedienteService } from '../core/services/expediente.service';
import { InactividadService } from '../core/services/inactividad.service';
import { ConfirmModalComponent } from '../shared/confirm-modal/confirm-modal.component';
import { InactividadModalComponent } from '../shared/inactividad-modal/inactividad-modal.component';

interface MenuItem {
  label: string;
  route: string;
  icon: string;
  roles: string[];
  grupo: string;
}

interface MenuGrupo {
  titulo: string;
  items: MenuItem[];
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, ConfirmModalComponent, InactividadModalComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnDestroy {
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificacionService = inject(NotificacionService);
  private readonly toastService = inject(ToastService);
  private readonly expedienteService = inject(ExpedienteService);
  private readonly inactividadService = inject(InactividadService);

  menuItems: MenuItem[] = [
    { label: 'Inicio', route: '/dashboard/overview', icon: 'home', roles: ['ADMIN', 'COORDINADOR', 'TUTOR', 'ESTUDIANTE'], grupo: '' },
    { label: 'Usuarios', route: '/dashboard/usuarios', icon: 'users', roles: ['ADMIN'], grupo: 'Gestión' },
    { label: 'Estudiantes', route: '/dashboard/estudiantes', icon: 'academic', roles: ['ADMIN', 'COORDINADOR'], grupo: 'Gestión' },
    { label: 'Importaciones', route: '/dashboard/importaciones', icon: 'upload', roles: ['ADMIN'], grupo: 'Gestión' },
    { label: 'Correos', route: '/dashboard/correos', icon: 'envelope', roles: ['ADMIN'], grupo: 'Gestión' },
    { label: 'Reportes', route: '/dashboard/reportes', icon: 'chart', roles: ['ADMIN', 'COORDINADOR'], grupo: 'Gestión' },
    { label: 'Convocatorias', route: '/dashboard/convocatorias', icon: 'calendar', roles: ['ADMIN', 'COORDINADOR'], grupo: 'Gestión' },
    { label: 'Empresas', route: '/dashboard/empresas', icon: 'office', roles: ['ADMIN', 'COORDINADOR'], grupo: 'Entidades receptoras' },
    { label: 'Fundaciones & Proyectos', route: '/dashboard/fundaciones-proyectos', icon: 'heart', roles: ['ADMIN', 'COORDINADOR'], grupo: 'Entidades receptoras' },
    { label: 'Seguimiento', route: '/dashboard/seguimiento', icon: 'clipboard', roles: ['ADMIN', 'COORDINADOR'], grupo: 'Expedientes' },
    { label: 'Prácticas', route: '/dashboard/practicas', icon: 'briefcase', roles: ['ADMIN', 'COORDINADOR', 'TUTOR', 'ESTUDIANTE'], grupo: 'Expedientes' },
    { label: 'Vinculación', route: '/dashboard/vinculacion', icon: 'link', roles: ['ADMIN', 'COORDINADOR', 'TUTOR', 'ESTUDIANTE'], grupo: 'Expedientes' },
    { label: 'Mi Expediente', route: '/dashboard/expediente-academico', icon: 'academic', roles: ['ESTUDIANTE'], grupo: 'Expedientes' }
  ];

  userName = signal('');
  userRole = signal('');
  sidebarOpen = signal(false);
  readonly periodoAcademico = this.expedienteService.periodoActual();

  // Notificaciones internas (fase 12)
  notificaciones = signal<Notificacion[]>([]);
  noLeidasCount = signal(0);
  showNotificaciones = signal(false);
  loadingNotificaciones = signal(false);

  private pollingId: ReturnType<typeof setInterval> | null = null;

  constructor() {
    const user = this.authService.currentUser();
    if (user) {
      this.userName.set(`${user.nombre} ${user.apellido}`);
      this.userRole.set(user.rol);
    }

    // Contador de no leídas: al cargar el dashboard + polling discreto cada 60s
    // (la petición usa SKIP_ERROR_TOAST, así que un fallo no molesta al usuario).
    this.refreshNoLeidasCount();
    this.pollingId = setInterval(() => this.refreshNoLeidasCount(), 60000);
    this.inactividadService.iniciar();
  }

  ngOnDestroy(): void {
    if (this.pollingId !== null) {
      clearInterval(this.pollingId);
      this.pollingId = null;
    }
    this.inactividadService.detener();
  }

  getFilteredMenuItems(): MenuItem[] {
    const user = this.authService.currentUser();
    if (!user) return [];

    return this.menuItems.filter(item => {
      // Check role access
      if (!item.roles.includes(user.rol)) return false;

      // Filter coordinator menus based on coordination type (Prácticas vs. Vinculación)
      if (user.rol === 'COORDINADOR') {
        const type = this.authService.coordinacionTipo();
        if (type === 'PRACTICAS' && (item.route === '/dashboard/vinculacion' || item.route === '/dashboard/fundaciones-proyectos')) return false;
        if (type === 'VINCULACION' && (item.route === '/dashboard/practicas' || item.route === '/dashboard/empresas')) return false;
      }

      // Filter tutor menus based on tutor type (Prácticas vs. Vinculación)
      if (user.rol === 'TUTOR') {
        const type = this.authService.tutorTipo();
        if (type === 'PRACTICAS' && item.route === '/dashboard/vinculacion') return false;
        if (type === 'VINCULACION' && item.route === '/dashboard/practicas') return false;
      }
      return true;
    });
  }

  // Menu agrupado por bloques (fase 30): grupos sin items visibles se ocultan
  getMenuGroups(): MenuGrupo[] {
    const visibles = this.getFilteredMenuItems();
    const orden = ['', 'Gestión', 'Entidades receptoras', 'Expedientes'];
    return orden
      .map(titulo => ({ titulo, items: visibles.filter(item => item.grupo === titulo) }))
      .filter(grupo => grupo.items.length > 0);
  }

  // --- Notificaciones ---

  refreshNoLeidasCount(): void {
    this.notificacionService.getNoLeidasCount().subscribe({
      next: (res) => this.noLeidasCount.set(res.count),
      error: () => { /* carga silenciosa: no interrumpir al usuario */ }
    });
  }

  toggleNotificaciones(): void {
    const abrir = !this.showNotificaciones();
    this.showNotificaciones.set(abrir);
    if (abrir) {
      this.loadNotificaciones();
    }
    this.refreshNoLeidasCount();
  }

  private loadNotificaciones(): void {
    this.loadingNotificaciones.set(true);
    this.notificacionService.getMisNotificaciones().subscribe({
      next: (data) => {
        this.notificaciones.set(data);
        this.loadingNotificaciones.set(false);
      },
      error: () => this.loadingNotificaciones.set(false)
    });
  }

  onNotificacionClick(notificacion: Notificacion): void {
    if (!notificacion.leida) {
      this.notificacionService.marcarLeida(notificacion.id).subscribe({
        next: () => {
          this.notificaciones.update(list =>
            list.map(n => n.id === notificacion.id ? { ...n, leida: true } : n)
          );
          this.noLeidasCount.update(c => Math.max(0, c - 1));
        },
        error: (err) => this.toastService.error(err?.error?.error || 'No se pudo marcar la notificación como leída.')
      });
    }
    if (notificacion.link) {
      this.showNotificaciones.set(false);
      this.router.navigateByUrl(notificacion.link);
    }
  }

  marcarTodasLeidas(): void {
    this.notificacionService.marcarTodasLeidas().subscribe({
      next: () => {
        this.notificaciones.update(list => list.map(n => ({ ...n, leida: true })));
        this.noLeidasCount.set(0);
      },
      error: (err) => this.toastService.error(err?.error?.error || 'No se pudieron marcar las notificaciones como leídas.')
    });
  }

  fechaNotificacion(fecha: string): string {
    const creada = new Date(fecha);
    if (isNaN(creada.getTime())) return '';
    const minutos = Math.floor((Date.now() - creada.getTime()) / 60000);
    if (minutos < 1) return 'Hace un momento';
    if (minutos < 60) return `Hace ${minutos} min`;
    const horas = Math.floor(minutos / 60);
    if (horas < 24) return `Hace ${horas} h`;
    const dias = Math.floor(horas / 24);
    if (dias === 1) return 'Ayer';
    if (dias < 7) return `Hace ${dias} días`;
    return creada.toLocaleDateString('es-EC', { day: '2-digit', month: 'short', year: 'numeric' });
  }

    getCoordinatorScope(): string {
    if (this.userRole() !== 'COORDINADOR') return '';
    const carreras = this.authService.carrerasAsignadas();
    if (!carreras || carreras.length === 0) return 'Sin carreras asignadas';
    return carreras.join(', ');
  }

  toggleSidebar(): void {
    this.sidebarOpen.update(abierto => !abierto);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  logout(): void {
    this.closeSidebar();
    this.authService.logout();
  }
}
