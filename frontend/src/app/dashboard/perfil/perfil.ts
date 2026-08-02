import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { EstudianteService } from '../../core/services/estudiante.service';
import { ToastService } from '../../core/services/toast.service';
import { Usuario, UsuarioService } from '../../core/services/usuario.service';

const ETIQUETAS_ROL: Record<string, string> = {
  ADMIN: 'Administrador',
  COORDINADOR: 'Coordinador',
  TUTOR: 'Tutor',
  ESTUDIANTE: 'Estudiante'
};

const ETIQUETAS_TUTOR_TIPO: Record<string, string> = {
  PRACTICAS: 'Prácticas',
  VINCULACION: 'Vinculación',
  AMBOS: 'Prácticas y Vinculación'
};

// Mismo patron que valida el backend en UsuarioController.updateMe()
const PATRON_CORREO = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './perfil.html',
  styleUrl: './perfil.css'
})
export class PerfilComponent implements OnInit {
  private readonly usuarioService = inject(UsuarioService);
  private readonly estudianteService = inject(EstudianteService);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);

  loading = signal(true);
  guardando = signal(false);
  usuario = signal<Usuario | null>(null);
  carrera = signal<string | null>(null);
  nuevoCorreo = '';

  readonly rol = computed(() => this.authService.currentUser()?.rol ?? '');
  readonly etiquetaRol = computed(() => ETIQUETAS_ROL[this.rol()] ?? this.rol());
  readonly etiquetaTutorTipo = computed(() => {
    const tipo = this.usuario()?.tutorTipo;
    return tipo ? (ETIQUETAS_TUTOR_TIPO[tipo] ?? tipo) : '';
  });
  readonly carrerasCoordinador = computed(() => this.authService.carrerasAsignadas());

  // Metodo (no computed): nuevoCorreo es un campo plano atado con ngModel,
  // no una signal, asi que un computed() nunca detectaria sus cambios.
  correoModificado(): boolean {
    const actual = this.usuario()?.email ?? '';
    return this.nuevoCorreo.trim() !== '' && this.nuevoCorreo.trim() !== actual;
  }

  correoValido(): boolean {
    return PATRON_CORREO.test(this.nuevoCorreo.trim());
  }

  ngOnInit(): void {
    this.usuarioService.getMe().subscribe({
      next: usuario => {
        this.usuario.set(usuario);
        this.nuevoCorreo = usuario.email;
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toastService.error('No se pudo cargar tu perfil.');
      }
    });

    if (this.rol() === 'ESTUDIANTE') {
      this.estudianteService.getMe().subscribe({
        next: estudiante => this.carrera.set(estudiante.carrera),
        error: () => this.carrera.set(null)
      });
    }
  }

  guardarCorreo(): void {
    const email = this.nuevoCorreo.trim();
    if (!email || !this.correoModificado() || !this.correoValido()) {
      return;
    }
    this.guardando.set(true);
    this.usuarioService.updateMiCorreo(email).subscribe({
      next: ({ usuario, token }) => {
        this.guardando.set(false);
        this.usuario.set(usuario);
        this.nuevoCorreo = usuario.email;
        this.authService.actualizarCorreoSesion(usuario.email, token);
        this.toastService.success('Correo actualizado correctamente.');
      },
      error: err => {
        this.guardando.set(false);
        this.toastService.error(err.error?.error || 'No se pudo actualizar el correo.');
      }
    });
  }

  cancelarEdicionCorreo(): void {
    this.nuevoCorreo = this.usuario()?.email ?? '';
  }
}
