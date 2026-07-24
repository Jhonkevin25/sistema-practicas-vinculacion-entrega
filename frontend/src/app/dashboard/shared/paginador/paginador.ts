import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

// Fase 43: control de paginación compartido por los listados del dashboard.
@Component({
  selector: 'app-paginador',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './paginador.html'
})
export class PaginadorComponent {
  // Página actual en base 0 (como la expone el backend)
  pagina = input.required<number>();
  totalPaginas = input.required<number>();
  totalElementos = input.required<number>();
  tamano = input<number>(20);
  cargando = input<boolean>(false);
  cambiar = output<number>();

  readonly desde = computed(() =>
    this.totalElementos() === 0 ? 0 : this.pagina() * this.tamano() + 1);
  readonly hasta = computed(() =>
    Math.min((this.pagina() + 1) * this.tamano(), this.totalElementos()));

  anterior(): void {
    if (this.pagina() > 0 && !this.cargando()) this.cambiar.emit(this.pagina() - 1);
  }

  siguiente(): void {
    if (this.pagina() + 1 < this.totalPaginas() && !this.cargando()) {
      this.cambiar.emit(this.pagina() + 1);
    }
  }
}
