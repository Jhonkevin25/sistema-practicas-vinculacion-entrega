import { Injectable, signal } from '@angular/core';

export interface ConfirmConfig {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean; // If true, the confirm button will be red
}

@Injectable({
  providedIn: 'root'
})
export class ConfirmService {
  private resolveFn: ((value: boolean) => void) | null = null;
  
  isOpen = signal(false);
  config = signal<ConfirmConfig | null>(null);

  /**
   * Muestra un modal de confirmación y devuelve una promesa que se resuelve con true o false.
   */
  confirm(config: ConfirmConfig | string): Promise<boolean> {
    const configObj = typeof config === 'string' ? { title: 'Confirmación', message: config, danger: true, confirmText: 'Confirmar', cancelText: 'Cancelar' } : config;
    this.config.set(configObj);
    this.isOpen.set(true);

    return new Promise<boolean>((resolve) => {
      this.resolveFn = resolve;
    });
  }

  onConfirm(): void {
    if (this.resolveFn) {
      this.resolveFn(true);
      this.resolveFn = null;
    }
    this.isOpen.set(false);
    this.config.set(null);
  }

  onCancel(): void {
    if (this.resolveFn) {
      this.resolveFn(false);
      this.resolveFn = null;
    }
    this.isOpen.set(false);
    this.config.set(null);
  }
}
