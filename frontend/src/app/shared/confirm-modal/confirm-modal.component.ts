import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConfirmService } from '../../core/services/confirm.service';

@Component({
  selector: 'app-confirm-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (confirmService.isOpen()) {
      <div class="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm overflow-y-auto">
        <div class="relative w-full max-w-sm rounded-xl bg-white shadow-2xl overflow-hidden" (click)="$event.stopPropagation()">
          <div class="p-6">
            <h3 class="text-lg font-bold text-unibe-blue mb-2">
              {{ confirmService.config()?.title }}
            </h3>
            <p class="text-sm text-slate-600 mb-6">
              {{ confirmService.config()?.message }}
            </p>
            
            <div class="flex justify-end gap-3">
              <button
                type="button"
                (click)="confirmService.onCancel()"
                class="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
              >
                {{ confirmService.config()?.cancelText || 'Cancelar' }}
              </button>
              <button
                type="button"
                (click)="confirmService.onConfirm()"
                class="rounded-lg px-4 py-2 text-sm font-semibold text-white transition-colors"
                [ngClass]="confirmService.config()?.danger ? 'bg-red-600 hover:bg-red-700' : 'bg-unibe-blue hover:bg-unibe-blue-hover'"
              >
                {{ confirmService.config()?.confirmText || 'Confirmar' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `
})
export class ConfirmModalComponent {
  confirmService = inject(ConfirmService);
}
