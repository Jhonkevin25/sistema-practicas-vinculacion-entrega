import { CommonModule } from '@angular/common';
import { Component, inject, signal, OnInit } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { ToastService } from '../../core/services/toast.service';
import {
  ErrorFilaImportacion,
  ImportacionService,
  ResumenImportacion,
  TipoImportacion,
  Importacion
} from '../../core/services/importacion.service';
import { Paginado } from '../../core/services/paginacion';
import { FormsModule } from '@angular/forms';

export interface VistaPreviaCsv {
  encabezados: string[];
  filas: string[][];
  totalFilas: number;
}

export function construirVistaPreviaCsv(contenido: string, limite = 5): VistaPreviaCsv {
  const filas: string[][] = [];
  let fila: string[] = [];
  let celda = '';
  let entreComillas = false;
  const texto = contenido.replace(/^\uFEFF/, '');

  const agregarFila = (): void => {
    fila.push(celda.trim());
    if (fila.some(valor => valor.length > 0)) {
      filas.push(fila);
    }
    fila = [];
    celda = '';
  };

  for (let indice = 0; indice < texto.length; indice++) {
    const caracter = texto[indice];
    if (caracter === '"') {
      if (entreComillas && texto[indice + 1] === '"') {
        celda += '"';
        indice++;
      } else {
        entreComillas = !entreComillas;
      }
    } else if (caracter === ',' && !entreComillas) {
      fila.push(celda.trim());
      celda = '';
    } else if ((caracter === '\n' || caracter === '\r') && !entreComillas) {
      agregarFila();
      if (caracter === '\r' && texto[indice + 1] === '\n') indice++;
    } else {
      celda += caracter;
    }
  }

  if (celda.length > 0 || fila.length > 0) agregarFila();

  return {
    encabezados: filas[0] ?? [],
    filas: filas.slice(1, limite + 1),
    totalFilas: Math.max(0, filas.length - 1)
  };
}

@Component({
  selector: 'app-importaciones',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './importaciones.html'
})
export class ImportacionesComponent implements OnInit {
  private readonly importacionService = inject(ImportacionService);
  private readonly toastService = inject(ToastService);

  tipo = signal<TipoImportacion>('ESTUDIANTES');
  archivo = signal<File | null>(null);
  vistaPrevia = signal<VistaPreviaCsv | null>(null);
  previsualizando = signal(false);
  errorVistaPrevia = signal<string | null>(null);
  confirmacion = signal(false);
  subiendo = signal(false);
  resultado = signal<ResumenImportacion | null>(null);

  // Historial state
  historial = signal<Importacion[]>([]);
  totalHistorial = signal(0);
  page = signal(0);
  size = signal(10);
  filtroTipo = signal<string>('');
  filtroEstado = signal<string>('');
  cargandoHistorial = signal(false);
  mostrarDetalleModal = signal(false);
  importacionSeleccionada = signal<Importacion | null>(null);
  erroresSeleccionados = signal<ErrorFilaImportacion[]>([]);

  ngOnInit(): void {
    this.cargarHistorial();
  }

  cargarHistorial(): void {
    this.cargandoHistorial.set(true);
    this.importacionService.obtenerHistorial(
      this.page(),
      this.size(),
      this.filtroTipo() || undefined,
      this.filtroEstado() || undefined
    ).subscribe({
      next: (res: Paginado<Importacion>) => {
        this.historial.set(res.contenido);
        this.totalHistorial.set(res.totalElementos);
        this.cargandoHistorial.set(false);
      },
      error: () => {
        this.cargandoHistorial.set(false);
        this.toastService.error('No se pudo cargar el historial de importaciones.');
      }
    });
  }

  cambiarPagina(nuevaPagina: number): void {
    this.page.set(nuevaPagina);
    this.cargarHistorial();
  }

  aplicarFiltros(): void {
    this.page.set(0);
    this.cargarHistorial();
  }

  verDetalles(importacion: Importacion): void {
    this.importacionSeleccionada.set(importacion);
    try {
      const errores = JSON.parse(importacion.detalleErrores || '[]') as ErrorFilaImportacion[];
      this.erroresSeleccionados.set(Array.isArray(errores) ? errores : []);
    } catch {
      this.erroresSeleccionados.set([]);
    }
    this.mostrarDetalleModal.set(true);
  }

  cerrarModal(): void {
    this.mostrarDetalleModal.set(false);
    this.importacionSeleccionada.set(null);
    this.erroresSeleccionados.set([]);
  }

  seleccionarTipo(tipo: TipoImportacion, input: HTMLInputElement): void {
    if (this.subiendo()) return;
    this.tipo.set(tipo);
    this.limpiarSeleccion();
    input.value = '';
  }

  async seleccionarArchivo(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0] || null;
    this.vistaPrevia.set(null);
    this.errorVistaPrevia.set(null);
    this.confirmacion.set(false);
    if (!archivo) {
      this.archivo.set(null);
      return;
    }
    if (!archivo.name.toLowerCase().endsWith('.csv')) {
      this.toastService.warning('Selecciona un archivo con extensión .csv.');
      input.value = '';
      this.archivo.set(null);
      return;
    }
    if (archivo.size > 2 * 1024 * 1024) {
      this.toastService.warning('El archivo CSV no puede superar 2 MB.');
      input.value = '';
      this.archivo.set(null);
      return;
    }
    this.archivo.set(archivo);
    this.resultado.set(null);
    this.previsualizando.set(true);

    try {
      const contenido = await archivo.text();
      if (this.archivo() !== archivo) return;
      const vistaPrevia = construirVistaPreviaCsv(contenido);
      this.vistaPrevia.set(vistaPrevia);
      if (vistaPrevia.encabezados.length === 0 || vistaPrevia.totalFilas === 0) {
        this.errorVistaPrevia.set('El CSV debe incluir una fila de encabezados y al menos una fila de datos.');
        this.toastService.warning('El archivo CSV no contiene datos para importar.');
      }
    } catch {
      if (this.archivo() !== archivo) return;
      this.errorVistaPrevia.set('No fue posible leer el archivo seleccionado.');
      this.toastService.error('No fue posible generar la vista previa del CSV.');
    } finally {
      if (this.archivo() === archivo) this.previsualizando.set(false);
    }
  }

  importar(): void {
    if (this.subiendo()) return;
    const archivo = this.archivo();
    if (!archivo) {
      this.toastService.warning('Selecciona el archivo CSV que deseas importar.');
      return;
    }
    if (!this.vistaPrevia() || this.errorVistaPrevia()) {
      this.toastService.warning('Revisa el archivo antes de continuar con la importación.');
      return;
    }
    if (!this.confirmacion()) {
      this.toastService.warning('Confirma que el tipo y el archivo son correctos antes de importar.');
      return;
    }
    this.subiendo.set(true);
    const request = this.tipo() === 'ESTUDIANTES'
      ? this.importacionService.importarEstudiantes(archivo)
      : this.importacionService.importarNotas(archivo);
    request.pipe(finalize(() => this.subiendo.set(false))).subscribe({
      next: resultado => {
        this.resultado.set(resultado);
        this.confirmacion.set(false);
        this.cargarHistorial();
        if (resultado.filasError === 0) {
          this.toastService.success('Importación institucional completada.');
        } else {
          this.toastService.warning('La importación terminó con filas que requieren revisión.');
        }
      },
      error: err => {
        this.toastService.error(err?.error?.error || 'No se pudo procesar el archivo CSV.');
      }
    });
  }

  nombreTipo(tipo: TipoImportacion = this.tipo()): string {
    return tipo === 'ESTUDIANTES' ? 'Estudiantes' : 'Notas académicas';
  }

  tamanoArchivo(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    return `${(bytes / 1024).toFixed(bytes < 1024 * 100 ? 1 : 0)} KB`;
  }

  private limpiarSeleccion(): void {
    this.archivo.set(null);
    this.vistaPrevia.set(null);
    this.previsualizando.set(false);
    this.errorVistaPrevia.set(null);
    this.confirmacion.set(false);
    this.resultado.set(null);
  }

  descargarPlantilla(): void {
    const contenido = this.tipo() === 'ESTUDIANTES'
      ? 'external_id,cedula,email_institucional,nombre,apellido,matricula,carrera,semestre,periodo_academico,estado_matricula\n'
      : 'external_id,email_institucional,periodo_academico,semestre,promedio\n';
    const nombre = this.tipo() === 'ESTUDIANTES'
      ? 'plantilla_estudiantes.csv'
      : 'plantilla_notas.csv';
    const url = URL.createObjectURL(new Blob(['\uFEFF' + contenido], { type: 'text/csv;charset=utf-8' }));
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    enlace.click();
    URL.revokeObjectURL(url);
  }

  descargarErroresCSV(): void {
    const errores = this.erroresSeleccionados();
    if (!errores || errores.length === 0) {
      this.toastService.warning('No hay errores para descargar.');
      return;
    }
    
    // Obtenemos las cabeceras del primer objeto (usualmente fila, mensaje)
    const cabeceras = Object.keys(errores[0]) as (keyof ErrorFilaImportacion)[];
    
    // Formamos el contenido CSV
    const filasCSV = errores.map(error => 
      cabeceras.map(cabecera => {
        const dato = error[cabecera];
        let valor = dato === null || dato === undefined ? '' : String(dato);
        // Escapar comillas dobles y envolver en comillas si hay comas o nuevas líneas
        if (valor.includes(',') || valor.includes('"') || valor.includes('\n')) {
          valor = `"${valor.replace(/"/g, '""')}"`;
        }
        return valor;
      }).join(',')
    );
    
    const contenido = cabeceras.join(',') + '\n' + filasCSV.join('\n');
    const url = URL.createObjectURL(new Blob(['\uFEFF' + contenido], { type: 'text/csv;charset=utf-8' }));
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = `errores_importacion_${this.importacionSeleccionada()?.id || 'descarga'}.csv`;
    enlace.click();
    URL.revokeObjectURL(url);
  }

  claseEstado(estado: string): string {
    if (estado === 'COMPLETADA') return 'bg-green-50 border-green-200 text-green-700';
    if (estado === 'CON_ERRORES') return 'bg-amber-50 border-amber-200 text-amber-700';
    return 'bg-red-50 border-red-200 text-red-700';
  }
}
