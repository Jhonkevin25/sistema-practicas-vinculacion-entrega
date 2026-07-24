import { Injectable, NgZone, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';
import { ToastService } from './toast.service';

const UMBRAL_INACTIVIDAD_MS = 60 * 60 * 1000; // 1 hora
const SEGUNDOS_CUENTA_REGRESIVA = 60;
const EVENTOS_ACTIVIDAD: (keyof DocumentEventMap)[] = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'];

// Cierra la sesion sola si el usuario deja la pestana abierta sin tocar nada
// durante una hora, avisando antes con una cuenta regresiva. Es independiente
// de la expiracion del JWT (8h fijas): no la refresca, solo decide cuando
// llamar al logout() que ya existe.
@Injectable({ providedIn: 'root' })
export class InactividadService {
  private readonly ngZone = inject(NgZone);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);

  mostrarAviso = signal(false);
  segundosRestantes = signal(SEGUNDOS_CUENTA_REGRESIVA);

  private timerInactividad: ReturnType<typeof setTimeout> | null = null;
  private timerCuentaRegresiva: ReturnType<typeof setInterval> | null = null;
  private listenerActivo: (() => void) | null = null;

  iniciar(): void {
    if (this.listenerActivo) return; // ya estaba corriendo
    this.ngZone.runOutsideAngular(() => {
      const alDetectarActividad = () => this.registrarActividad();
      EVENTOS_ACTIVIDAD.forEach(evento =>
        document.addEventListener(evento, alDetectarActividad, { passive: true }));
      this.listenerActivo = () => EVENTOS_ACTIVIDAD.forEach(evento =>
        document.removeEventListener(evento, alDetectarActividad));
      this.reiniciarTimerInactividad();
    });
  }

  detener(): void {
    if (this.timerInactividad) clearTimeout(this.timerInactividad);
    if (this.timerCuentaRegresiva) clearInterval(this.timerCuentaRegresiva);
    this.timerInactividad = null;
    this.timerCuentaRegresiva = null;
    if (this.listenerActivo) {
      this.listenerActivo();
      this.listenerActivo = null;
    }
    this.mostrarAviso.set(false);
  }

  continuar(): void {
    if (this.timerCuentaRegresiva) {
      clearInterval(this.timerCuentaRegresiva);
      this.timerCuentaRegresiva = null;
    }
    this.mostrarAviso.set(false);
    this.reiniciarTimerInactividad();
  }

  cerrarSesion(): void {
    this.detener();
    this.authService.logout();
  }

  private registrarActividad(): void {
    if (this.mostrarAviso()) return; // con el aviso abierto, solo deciden los botones
    this.reiniciarTimerInactividad();
  }

  private reiniciarTimerInactividad(): void {
    if (this.timerInactividad) clearTimeout(this.timerInactividad);
    this.timerInactividad = setTimeout(() => this.mostrarAvisoInactividad(), UMBRAL_INACTIVIDAD_MS);
  }

  private mostrarAvisoInactividad(): void {
    this.ngZone.run(() => {
      this.segundosRestantes.set(SEGUNDOS_CUENTA_REGRESIVA);
      this.mostrarAviso.set(true);
    });
    this.timerCuentaRegresiva = setInterval(() => {
      const restante = this.segundosRestantes() - 1;
      if (restante <= 0) {
        this.ngZone.run(() => this.cerrarPorInactividad());
        return;
      }
      this.ngZone.run(() => this.segundosRestantes.set(restante));
    }, 1000);
  }

  private cerrarPorInactividad(): void {
    this.detener();
    this.authService.logout();
    this.toastService.warning('Tu sesión se cerró por inactividad.');
  }
}
