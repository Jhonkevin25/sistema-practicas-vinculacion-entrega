import { Component, HostListener, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

// Forma del acta devuelta por GET /api/{practicas,vinculacion}/{id}/acta
// (CierreExpedienteComponent.baseActa + estudianteResumen en el backend)
export interface ActaCierre {
  proceso?: string;
  expedienteId?: number;
  estado?: string;
  fechaInicio?: string;
  fechaFin?: string;
  periodoAcademico?: string;
  horasRequeridas?: number;
  horasCompletadas?: number;
  cerradoPor?: string;
  cerradoEn?: string;
  estudiante?: {
    nombre?: string;
    matricula?: string;
    carrera?: string;
    semestre?: number;
  };
  entidad?: string;
  proyecto?: string;
  tutor?: string;
}

@Component({
  selector: 'app-acta-cierre-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './acta-cierre-modal.html'
})
export class ActaCierreModalComponent {
  titulo = input.required<string>();
  acta = input.required<ActaCierre>();
  cerrar = output<void>();

  @HostListener('document:keydown.escape')
  cerrarConEscape(): void {
    this.cerrar.emit();
  }
}
