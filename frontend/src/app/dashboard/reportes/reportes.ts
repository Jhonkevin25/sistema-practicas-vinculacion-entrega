import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import {
  AsignacionReporte,
  CierreReporte,
  CupoReporte,
  FiltrosReporte,
  ProcesoReporte,
  ReportePaginado,
  ReporteService,
  RiesgoReporte,
  TipoReporte
} from '../../core/services/reporte.service';
import { ToastService } from '../../core/services/toast.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import { PaginadorComponent } from '../shared/paginador/paginador';
import { TAMANO_PAGINA_DEFECTO } from '../../core/services/paginacion';

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule, PaginadorComponent],
  templateUrl: './reportes.html'
})
export class ReportesComponent {
  private readonly reporteService = inject(ReporteService);
  private readonly toastService = inject(ToastService);
  private readonly expedienteService = inject(ExpedienteService);

  tipo = signal<TipoReporte>('ASIGNACIONES');
  periodo = signal(this.expedienteService.periodoActual());
  carrera = signal('');
  proceso = signal<ProcesoReporte>('TODOS');
  entidad = signal('');
  cargando = signal(false);
  exportando = signal(false);

  asignaciones = signal<AsignacionReporte[]>([]);
  cupos = signal<CupoReporte[]>([]);
  riesgos = signal<RiesgoReporte[]>([]);
  cierres = signal<CierreReporte[]>([]);

  // Paginación
  pagina = signal(0);
  tamano = signal(TAMANO_PAGINA_DEFECTO);
  totalElementos = signal(0);
  totalPaginas = signal(0);

  // Totales globales para KPIs
  totalGlobal = signal(0);
  valorPrincipalGlobal = signal(0);
  valorSecundarioGlobal = signal(0);

  constructor() {
    this.cargar();
  }

  seleccionarTipo(tipo: TipoReporte): void {
    this.tipo.set(tipo);
    this.pagina.set(0);
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    const filtros = this.filtros();
    if (this.tipo() === 'ASIGNACIONES') {
      this.ejecutarCarga(this.reporteService.asignacionesPaginadas(this.pagina(), this.tamano(), filtros), 
                         pag => this.asignaciones.set(pag.contenido));
    } else if (this.tipo() === 'CUPOS') {
      this.ejecutarCarga(this.reporteService.cuposPaginados(this.pagina(), this.tamano(), filtros), 
                         pag => this.cupos.set(pag.contenido));
    } else if (this.tipo() === 'RIESGOS') {
      this.ejecutarCarga(this.reporteService.riesgosPaginados(this.pagina(), this.tamano(), filtros), 
                         pag => this.riesgos.set(pag.contenido));
    } else {
      this.ejecutarCarga(this.reporteService.cierresPaginados(this.pagina(), this.tamano(), filtros), 
                         pag => this.cierres.set(pag.contenido));
    }
  }

  cambioPagina(nuevaPagina: number): void {
    this.pagina.set(nuevaPagina);
    this.cargar();
  }

  limpiarFiltros(): void {
    this.periodo.set('');
    this.carrera.set('');
    this.proceso.set('TODOS');
    this.entidad.set('');
    this.pagina.set(0);
    this.cargar();
  }

  exportar(): void {
    this.exportando.set(true);
    const tipo = this.tipo();
    this.reporteService.exportar(tipo, this.filtros()).subscribe({
      next: archivo => {
        const url = URL.createObjectURL(archivo);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `reporte_${tipo.toLowerCase()}.csv`;
        enlace.click();
        URL.revokeObjectURL(url);
        this.exportando.set(false);
        this.toastService.success('Reporte CSV generado.');
      },
      error: err => {
        this.exportando.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo exportar el reporte.');
      }
    });
  }

  totalFilas(): number {
    return this.totalGlobal();
  }

  valorPrincipal(): number {
    return this.valorPrincipalGlobal();
  }

  etiquetaPrincipal(): string {
    if (this.tipo() === 'CUPOS') return 'Cupos utilizados';
    if (this.tipo() === 'RIESGOS') return 'Riesgo alto';
    if (this.tipo() === 'CIERRES') return 'Expedientes cerrados';
    return 'Procesos en curso';
  }

  valorSecundario(): number {
    return this.valorSecundarioGlobal();
  }

  etiquetaSecundaria(): string {
    if (this.tipo() === 'CUPOS') return 'Cupos disponibles';
    if (this.tipo() === 'RIESGOS') return 'Bitácoras observadas';
    if (this.tipo() === 'CIERRES') return 'Cumplen horas';
    return 'Horas completadas';
  }

  claseEstado(estado: string): string {
    const normalizado = estado?.toLowerCase();
    if (normalizado === 'completado') return 'bg-green-50 text-green-700 border-green-200';
    if (normalizado === 'en_curso') return 'bg-blue-50 text-blue-700 border-blue-200';
    return 'bg-amber-50 text-amber-700 border-amber-200';
  }

  private filtros(): FiltrosReporte {
    return {
      periodo: this.periodo(),
      carrera: this.carrera(),
      proceso: this.proceso(),
      entidad: this.entidad()
    };
  }

  private ejecutarCarga<T>(request: Observable<ReportePaginado<T>>, actualizar: (pag: ReportePaginado<T>) => void): void {
    request.subscribe({
      next: pag => {
        actualizar(pag);
        this.totalElementos.set(pag.totalElementos);
        this.totalPaginas.set(pag.totalPaginas);

        // Los KPIs (valorPrincipal/valorSecundario) vienen del backend ya
        // calculados sobre TODO el universo filtrado, no solo la página
        // visible (ver ReportePaginaResponse en el backend).
        this.totalGlobal.set(pag.totalElementos);
        this.valorPrincipalGlobal.set(pag.valorPrincipal);
        this.valorSecundarioGlobal.set(pag.valorSecundario);

        this.cargando.set(false);
      },
      error: err => {
        this.cargando.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo cargar el reporte.');
      }
    });
  }
}
