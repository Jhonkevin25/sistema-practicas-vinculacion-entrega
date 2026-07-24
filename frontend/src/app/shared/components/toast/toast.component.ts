import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="fixed top-5 right-5 z-[9999] flex flex-col gap-3 w-full max-w-sm pointer-events-none">
      <div 
        *ngFor="let toast of toasts()" 
        [class]="getToastClasses(toast.type)"
        class="p-4 border rounded-xl shadow-lg flex items-start gap-3 pointer-events-auto transition-all duration-300 transform translate-y-0 scale-100 animate-slide-in font-sans text-xs relative overflow-hidden bg-white/95 backdrop-blur-sm"
        role="alert"
      >
        <!-- Icon -->
        <span class="text-base select-none mt-0.5">{{ getToastIcon(toast.type) }}</span>
        
        <!-- Message -->
        <div class="flex-1 font-semibold leading-snug">
          {{ toast.message }}
        </div>

        <!-- Close Button -->
        <button 
          (click)="remove(toast.id)" 
          class="text-slate-400 hover:text-slate-700 font-bold ml-2 select-none"
        >
          ✕
        </button>

        <!-- Progress bottom bar -->
        <div 
          [class]="getProgressBarClasses(toast.type)"
          class="absolute bottom-0 left-0 h-1 transition-all duration-4000 animate-shrink-width"
        ></div>
      </div>
    </div>
  `,
  styles: [`
    @keyframes slideIn {
      from {
        opacity: 0;
        transform: translateY(-20px) scale(0.95);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }
    @keyframes shrink {
      from { width: 100%; }
      to { width: 0%; }
    }
    .animate-slide-in {
      animation: slideIn 0.25s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }
    .animate-shrink-width {
      animation: shrink 4s linear forwards;
    }
  `]
})
export class ToastComponent {
  readonly toastService = inject(ToastService);
  readonly toasts = this.toastService.toasts;

  remove(id: number): void {
    this.toastService.remove(id);
  }

  getToastIcon(type: string): string {
    switch (type) {
      case 'success': return '🟢';
      case 'error': return '🔴';
      case 'warning': return '⚠️';
      default: return 'ℹ️';
    }
  }

  getToastClasses(type: string): string {
    switch (type) {
      case 'success':
        return 'border-emerald-200 text-emerald-800 bg-emerald-50/95';
      case 'error':
        return 'border-rose-200 text-rose-800 bg-rose-50/95';
      case 'warning':
        return 'border-amber-200 text-amber-800 bg-amber-50/95';
      default:
        return 'border-blue-200 text-blue-800 bg-blue-50/95';
    }
  }

  getProgressBarClasses(type: string): string {
    switch (type) {
      case 'success': return 'bg-emerald-500';
      case 'error': return 'bg-rose-500';
      case 'warning': return 'bg-amber-500';
      default: return 'bg-blue-500';
    }
  }
}
