import { Component, HostListener, input, output } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-calificacion-modal',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './calificacion-modal.html'
})
export class CalificacionModalComponent {
  proceso = input.required<'Prácticas' | 'Vinculación'>();
  estudiante = input.required<string>();
  parcial = input.required<number>();
  rol = input.required<string>();
  notaTutor = input.required<number>();
  notaCoordinacion = input.required<number>();
  guardando = input(false);

  notaTutorChange = output<number>();
  notaCoordinacionChange = output<number>();
  guardar = output<void>();
  cerrar = output<void>();

  @HostListener('document:keydown.escape')
  cerrarConEscape(): void {
    this.cerrar.emit();
  }
}
