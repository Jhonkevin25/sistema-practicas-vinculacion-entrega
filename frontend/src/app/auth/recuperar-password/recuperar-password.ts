import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-recuperar-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './recuperar-password.html',
  styleUrl: './recuperar-password.css'
})
export class RecuperarPasswordComponent {
  private readonly authService = inject(AuthService);

  email = '';
  loading = signal(false);
  message = signal<string | null>(null);
  errorMessage = signal<string | null>(null);

  onSubmit(): void {
    if (!this.email) {
      this.errorMessage.set('Ingresa tu correo institucional.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService.recuperarPassword(this.email).subscribe({
      next: res => {
        this.loading.set(false);
        this.message.set(res.mensaje);
      },
      error: err => {
        this.loading.set(false);
        this.errorMessage.set(err.error?.error || 'No se pudo procesar la solicitud.');
      }
    });
  }
}
