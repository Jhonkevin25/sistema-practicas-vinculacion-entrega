import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { InactividadService } from '../../core/services/inactividad.service';

@Component({
  selector: 'app-inactividad-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (inactividadService.mostrarAviso()) {
      <div class="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm overflow-y-auto">
        <div class="relative w-full max-w-sm rounded-xl bg-white shadow-2xl overflow-hidden">
          <div class="p-6">
            <h3 class="text-lg font-bold text-unibe-blue mb-2">Tu sesión está inactiva</h3>
            <p class="text-sm text-slate-600 mb-6">
              Se cerrará automáticamente en <strong>{{ inactividadService.segundosRestantes() }}</strong> segundos por falta de actividad.
            </p>

            <div class="flex justify-end gap-3">
              <button
                type="button"
                (click)="inactividadService.cerrarSesion()"
                class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                Cerrar sesión
              </button>
              <button
                type="button"
                (click)="inactividadService.continuar()"
                class="rounded-lg px-4 py-2 text-sm font-semibold text-white bg-unibe-blue hover:bg-unibe-blue-hover transition-colors"
              >
                Seguir conectado
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `
})
export class InactividadModalComponent {
  inactividadService = inject(InactividadService);
}
