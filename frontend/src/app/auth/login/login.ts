import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  email = '';
  password = '';
  errorMessage = signal<string | null>(null);
  loading = signal(false);
  verPassword = signal(false);

  toggleVerPassword(): void {
    this.verPassword.update(v => !v);
  }

  onSubmit(): void {
    if (!this.email || !this.password) {
      this.errorMessage.set('Por favor, ingresa tu correo y contraseña.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: res => {
        this.loading.set(false);
        this.router.navigate([res.primerLogin ? '/cambiar-password' : '/dashboard']);
      },
      error: err => {
        this.loading.set(false);
        this.errorMessage.set(
          err.error?.error || 'Error al iniciar sesión. Verifica tus credenciales.'
        );
      }
    });
  }
}
