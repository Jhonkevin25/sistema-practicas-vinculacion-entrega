import { CommonModule } from '@angular/common';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-restablecer-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './restablecer-password.html',
  styleUrl: './restablecer-password.css'
})
export class RestablecerPasswordComponent implements OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly toastService = inject(ToastService);

  token = this.route.snapshot.queryParamMap.get('token') || '';
  nuevaPassword = '';
  confirmarPassword = '';
  loading = signal(false);
  completado = signal(false);
  message = signal<string | null>(null);
  errorMessage = signal<string | null>(null);
  private redirectId: ReturnType<typeof setTimeout> | null = null;

  ngOnDestroy(): void {
    if (this.redirectId !== null) clearTimeout(this.redirectId);
  }

  onSubmit(): void {
    if (!this.token) {
      this.errorMessage.set('El enlace de recuperación no es válido.');
      return;
    }
    if (!this.nuevaPassword || !this.confirmarPassword) {
      this.errorMessage.set('Completa todos los campos.');
      return;
    }
    if (this.nuevaPassword !== this.confirmarPassword) {
      this.errorMessage.set('La confirmación no coincide con la nueva contraseña.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService.restablecerPassword(this.token, this.nuevaPassword).subscribe({
      next: res => {
        this.loading.set(false);
        this.completado.set(true);
        this.nuevaPassword = '';
        this.confirmarPassword = '';
        this.message.set(`${res.mensaje} Serás redirigido al inicio de sesión.`);
        this.toastService.success('Contraseña restablecida correctamente. Inicia sesión con tu nueva contraseña.');
        this.redirectId = setTimeout(() => this.authService.logout(), 2500);
      },
      error: err => {
        this.loading.set(false);
        this.errorMessage.set(err.error?.error || 'No se pudo restablecer la contraseña.');
      }
    });
  }
}
