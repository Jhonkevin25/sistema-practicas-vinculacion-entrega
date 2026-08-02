import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, of, tap } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { PeriodoAcademicoService } from './periodo-academico.service';
import { CarreraService } from './carrera.service';
import { UsuarioService } from './usuario.service';
import { EstudianteService } from './estudiante.service';
import { PracticaService } from './practica.service';
import { VinculacionService } from './vinculacion.service';
import { BitacoraService } from './bitacora.service';
import { PostulacionService } from './postulacion.service';
import { AsistenciaService } from './asistencia.service';
import { contextoValidacionSesion } from '../interceptors/http-context-tokens';

export interface AlcanceCoordinador {
  tipo: 'PRACTICAS' | 'VINCULACION' | 'AMBOS';
  carreras: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  nombre: string;
  apellido: string;
  email: string;
  rol: string;
  carrera?: string;
  primerLogin?: boolean;
  // Solo para rol TUTOR: PRACTICAS | VINCULACION | AMBOS
  tutorTipo?: string;
}

export interface MensajeAuthResponse {
  mensaje: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly apiUrl = '/api/auth';
  private readonly periodoAcademicoService = inject(PeriodoAcademicoService);
  private readonly carreraService = inject(CarreraService);
  private readonly usuarioService = inject(UsuarioService);
  private readonly estudianteService = inject(EstudianteService);
  private readonly practicaService = inject(PracticaService);
  private readonly vinculacionService = inject(VinculacionService);
  private readonly bitacoraService = inject(BitacoraService);
  private readonly postulacionService = inject(PostulacionService);
  private readonly asistenciaService = inject(AsistenciaService);

  // Signals for auth state management
  readonly currentUser = signal<AuthResponse | null>(null);
  
  // Segmented Coordinator profile signals for restrictions
  readonly coordinacionTipo = signal<'PRACTICAS' | 'VINCULACION' | 'AMBOS'>('AMBOS');
  readonly carrerasAsignadas = signal<string[]>([]);
  
  // Tipo real del tutor (usuarios.tutor_tipo, fase 28): decide qué secciones
  // ve en el menú. Llega en la respuesta del login.
  readonly tutorTipo = signal<'PRACTICAS' | 'VINCULACION' | 'AMBOS'>('AMBOS');

  constructor() {
    this.loadSession();
    this.coordinacionTipo.set('AMBOS');
    this.carrerasAsignadas.set([]);
  }

  // Carga el alcance del coordinador desde COORDINADOR_CARRERAS. Lo invocan
  // el APP_INITIALIZER (F5 con sesión activa) y el login, para que los
  // componentes lean señales ya resueltas. Sin filas o ante error se conserva
  // un alcance vacio: el cliente tampoco debe ampliar permisos por defecto.
  cargarAlcance(): Observable<unknown> {
    const user = this.currentUser();
    if (!user || this.requiresPasswordChange() || user.rol !== 'COORDINADOR') {
      this.coordinacionTipo.set('AMBOS');
      this.carrerasAsignadas.set([]);
      return of(null);
    }
    return this.http.get<AlcanceCoordinador>('/api/coordinadores/alcance/me', {
      context: contextoValidacionSesion()
    }).pipe(
      tap(alcance => {
        this.coordinacionTipo.set(alcance.tipo || 'AMBOS');
        this.carrerasAsignadas.set(alcance.carreras || []);
      }),
      catchError(() => {
        this.coordinacionTipo.set('AMBOS');
        this.carrerasAsignadas.set([]);
        return of(null);
      })
    );
  }

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials).pipe(
      tap(res => {
        this.saveSession(res);
      }),
      // Resolver el alcance del coordinador antes de completar el login,
      // para que el dashboard cargue con las carreras correctas
      switchMap(res => res.primerLogin
        ? of(res)
        : this.cargarAlcance().pipe(
            switchMap(() => this.periodoAcademicoService.cargarActivo()),
            map(() => res)
          ))
    );
  }

  cambiarPassword(passwordActual: string, nuevaPassword: string): Observable<MensajeAuthResponse> {
    return this.http.post<MensajeAuthResponse>(`${this.apiUrl}/cambiar-password`, {
      passwordActual,
      nuevaPassword
    }).pipe(
      tap(() => {
        const user = this.currentUser();
        if (user) {
          this.saveSession({ ...user, primerLogin: false });
        }
      })
    );
  }

  recuperarPassword(email: string): Observable<MensajeAuthResponse> {
    return this.http.post<MensajeAuthResponse>(`${this.apiUrl}/recuperar`, { email });
  }

  restablecerPassword(token: string, nuevaPassword: string): Observable<MensajeAuthResponse> {
    return this.http.post<MensajeAuthResponse>(`${this.apiUrl}/restablecer`, { token, nuevaPassword });
  }

  requiresPasswordChange(): boolean {
    return this.currentUser()?.primerLogin === true;
  }

  marcarPrimerLoginPendiente(): void {
    const user = this.currentUser();
    if (user && !user.primerLogin) {
      this.saveSession({ ...user, primerLogin: true });
    }
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.currentUser.set(null);
    // No arrastrar el alcance ni las cachés del usuario anterior a la
    // siguiente sesion en la misma pestaña
    this.coordinacionTipo.set('AMBOS');
    this.carrerasAsignadas.set([]);
    this.carreraService.invalidar();
    this.periodoAcademicoService.reset();
    this.usuarioService.clearCache();
    this.estudianteService.clearCache();
    this.practicaService.clearCache();
    this.vinculacionService.clearCache();
    this.bitacoraService.clearCaches();
    this.postulacionService.clearCaches();
    this.asistenciaService.clearCaches();
    this.router.navigate(['/login']);
  }

  // El backend reemite el JWT al cambiar el propio correo (la autenticacion
  // busca al usuario por el email del token). Aquí se refresca la sesión
  // local con el token y correo nuevos sin forzar un logout/login.
  actualizarCorreoSesion(email: string, token: string): void {
    const user = this.currentUser();
    if (user) {
      this.saveSession({ ...user, email, token });
    }
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('token');
  }

  getRole(): string | null {
    const user = this.currentUser();
    return user ? user.rol : null;
  }

  private saveSession(res: AuthResponse): void {
    localStorage.setItem('token', res.token);
    localStorage.setItem('user', JSON.stringify(res));
    this.currentUser.set(res);
    this.aplicarTutorTipo(res);
  }

  private loadSession(): void {
    const userJson = localStorage.getItem('user');
    if (userJson) {
      try {
        const user: AuthResponse = JSON.parse(userJson);
        this.currentUser.set(user);
        this.aplicarTutorTipo(user);
      } catch {
        this.logout();
      }
    }
  }

  private aplicarTutorTipo(user: AuthResponse): void {
    const tipo = user.tutorTipo;
    this.tutorTipo.set(tipo === 'PRACTICAS' || tipo === 'VINCULACION' ? tipo : 'AMBOS');
  }
}
