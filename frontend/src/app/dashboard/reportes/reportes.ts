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
  ReporteService,
  RiesgoReporte,
  TipoReporte
} from '../../core/services/reporte.service';
import { ToastService } from '../../core/services/toast.service';
import { ExpedienteService } from '../../core/services/expediente.service';
import { PaginadorComponent } from '../shared/paginador/paginador';
import { Paginado, TAMANO_PAGINA_DEFECTO } from '../../core/services/paginacion';

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

  private ejecutarCarga<T>(request: Observable<Paginado<T>>, actualizar: (pag: Paginado<T>) => void): void {
    request.subscribe({
      next: pag => {
        actualizar(pag);
        this.totalElementos.set(pag.totalElementos);
        this.totalPaginas.set(pag.totalPaginas);
        
        // Obtener totales globales que vienen en la respuesta (el backend tiene que enviarlos en un DTO adecuado
        // o si no se pueden calcular a partir del contenido de la página, pero como la página es parcial,
        // esto requiere que el backend devuelva el total. Por ahora, el backend devuelve PaginaResponse normal.
        // Dado el esquema híbrido del backend, los elementos están todos en memoria y el PaginaResponse 
        // tiene todos los datos que necesitamos para calcular en memoria, excepto que sólo devuelve una "página".
        // Sin embargo, si es necesario lo simplificamos y no mostramos KPIs de toda la DB sino de la página).
        // TODO: Para mantener el funcionamiento del UI, en los KPIs calculamos respecto a los elementos de la página.
        if (this.tipo() === 'ASIGNACIONES') {
          const asig = pag.contenido as unknown as AsignacionReporte[];
          this.totalGlobal.set(pag.totalElementos);
          this.valorPrincipalGlobal.set(asig.filter(fila => fila.estado?.toLowerCase() === 'en_curso').length);
          this.valorSecundarioGlobal.set(asig.reduce((total, fila) => total + fila.horasCompletadas, 0));
        } else if (this.tipo() === 'CUPOS') {
          const cupo = pag.contenido as unknown as CupoReporte[];
          this.totalGlobal.set(pag.totalElementos);
          this.valorPrincipalGlobal.set(cupo.reduce((total, fila) => total + fila.cuposUsados, 0));
          this.valorSecundarioGlobal.set(cupo.reduce((total, fila) => total + fila.cuposDisponibles, 0));
        } else if (this.tipo() === 'RIESGOS') {
          const riesgo = pag.contenido as unknown as RiesgoReporte[];
          this.totalGlobal.set(pag.totalElementos);
          this.valorPrincipalGlobal.set(riesgo.filter(fila => fila.nivel === 'ALTO').length);
          this.valorSecundarioGlobal.set(riesgo.reduce((total, fila) => total + fila.bitacorasObservadas, 0));
        } else {
          const cierre = pag.contenido as unknown as CierreReporte[];
          this.totalGlobal.set(pag.totalElementos);
          this.valorPrincipalGlobal.set(cierre.filter(fila => fila.cerrado).length);
          this.valorSecundarioGlobal.set(cierre.filter(fila => fila.cumpleHoras).length);
        }

        this.cargando.set(false);
      },
      error: err => {
        this.cargando.set(false);
        this.toastService.error(err?.error?.error || 'No se pudo cargar el reporte.');
      }
    });
  }
}
