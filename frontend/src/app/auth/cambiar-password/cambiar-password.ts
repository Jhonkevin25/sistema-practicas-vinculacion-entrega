import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-cambiar-password',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cambiar-password.html',
  styleUrl: './cambiar-password.css'
})
export class CambiarPasswordComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toastService = inject(ToastService);

  passwordActual = '';
  nuevaPassword = '';
  confirmarPassword = '';
  loading = signal(false);
  errorMessage = signal<string | null>(null);

  onSubmit(): void {
    if (!this.passwordActual || !this.nuevaPassword || !this.confirmarPassword) {
      this.errorMessage.set('Completa todos los campos.');
      return;
    }
    if (this.nuevaPassword !== this.confirmarPassword) {
      this.errorMessage.set('La confirmación no coincide con la nueva contraseña.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService.cambiarPassword(this.passwordActual, this.nuevaPassword).subscribe({
      next: () => {
        this.loading.set(false);
        this.toastService.success('Contraseña actualizada correctamente. Inicia sesión nuevamente.');
        setTimeout(() => this.authService.logout(), 2500);
      },
      error: err => {
        this.loading.set(false);
        this.errorMessage.set(err.error?.error || 'No se pudo actualizar la contraseña.');
      }
    });
  }
}
