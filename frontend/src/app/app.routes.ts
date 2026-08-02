import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./auth/login/login').then(m => m.LoginComponent) },
  { path: 'recuperar-password', loadComponent: () => import('./auth/recuperar-password/recuperar-password').then(m => m.RecuperarPasswordComponent) },
  { path: 'restablecer-password', loadComponent: () => import('./auth/restablecer-password/restablecer-password').then(m => m.RestablecerPasswordComponent) },
  { path: 'cambiar-password', loadComponent: () => import('./auth/cambiar-password/cambiar-password').then(m => m.CambiarPasswordComponent), canActivate: [authGuard] },
  {
    path: 'dashboard',
    loadComponent: () => import('./dashboard/dashboard').then(m => m.DashboardComponent),
    canActivate: [authGuard],
    children: [
      { path: 'overview', loadComponent: () => import('./dashboard/overview/overview').then(m => m.OverviewComponent) },
      { path: 'usuarios', loadComponent: () => import('./dashboard/usuarios/usuarios').then(m => m.UsuariosComponent) },
      { path: 'estudiantes', loadComponent: () => import('./dashboard/estudiantes/estudiantes').then(m => m.EstudiantesComponent) },
      { path: 'empresas', loadComponent: () => import('./dashboard/empresas/empresas').then(m => m.EmpresasComponent) },
      { path: 'fundaciones-proyectos', loadComponent: () => import('./dashboard/fundaciones-proyectos/fundaciones-proyectos').then(m => m.FundacionesProyectosComponent) },
      { path: 'convocatorias', loadComponent: () => import('./dashboard/convocatorias/convocatorias').then(m => m.ConvocatoriasComponent) },
      { path: 'practicas', loadComponent: () => import('./dashboard/practicas/practicas').then(m => m.PracticasComponent) },
      { path: 'vinculacion', loadComponent: () => import('./dashboard/vinculacion/vinculacion').then(m => m.VinculacionComponent) },
      { path: 'expediente-academico', loadComponent: () => import('./dashboard/expediente-academico/expediente-academico').then(m => m.ExpedienteAcademicoComponent) },
      { path: 'importaciones', loadComponent: () => import('./dashboard/importaciones/importaciones').then(m => m.ImportacionesComponent) },
      { path: 'correos', loadComponent: () => import('./dashboard/correos/correos').then(m => m.CorreosComponent) },
      { path: 'reportes', loadComponent: () => import('./dashboard/reportes/reportes').then(m => m.ReportesComponent) },
      { path: 'seguimiento', loadComponent: () => import('./dashboard/seguimiento/seguimiento').then(m => m.SeguimientoComponent) },
      { path: 'perfil', loadComponent: () => import('./dashboard/perfil/perfil').then(m => m.PerfilComponent) },
      { path: '', redirectTo: 'overview', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
