import { Component, input, output } from '@angular/core';
import { DocEstudiante, TipoDocumento } from '../../../core/services/documento.service';

interface DocumentoVista { tipo: TipoDocumento; titulo: string; descripcion: string; }

@Component({ selector: 'app-carga-documentos-panel', standalone: true, templateUrl: './carga-documentos-panel.html' })
export class CargaDocumentosPanelComponent {
  proceso = input.required<'PRACTICAS' | 'VINCULACION'>();
  documentos = input.required<DocEstudiante[]>();
  subiendo = input<TipoDocumento | null>(null);
  subir = output<{ tipo: TipoDocumento; evento: Event }>();
  ver = output<TipoDocumento>();

  items(): DocumentoVista[] {
    const vinculacion = this.proceso() === 'VINCULACION';
    return [
      { tipo: 'cv', titulo: 'Hoja de Vida (CV)', descripcion: vinculacion ? 'Información académica y de vinculación.' : 'Información académica y experiencia laboral.' },
      { tipo: 'carta', titulo: vinculacion ? 'Carta de Solicitud de Vinculación' : 'Carta de Solicitud de Prácticas', descripcion: vinculacion ? 'Dirigida al encargado del módulo.' : 'Dirigida al encargado de prácticas.' },
      { tipo: 'cedula', titulo: 'Copia de Cédula', descripcion: 'Documento de identidad legible en PDF o imagen.' }
    ];
  }

  documento(tipo: TipoDocumento): DocEstudiante | undefined {
    return this.documentos().find(item => item.tipoDocumento === tipo);
  }

  tieneArchivo(tipo: TipoDocumento): boolean { return !!this.documento(tipo)?.urlArchivo; }

  estaSubiendo(tipo: TipoDocumento): boolean { return this.subiendo() === tipo; }

  etiqueta(tipo: TipoDocumento): string {
    if (this.estaSubiendo(tipo)) return 'Subiendo...';
    const estado = this.documento(tipo)?.estado;
    if (estado === 'aprobado') return 'Aprobado';
    if (estado === 'requiere_correccion' || estado === 'rechazado') return 'Requiere corrección';
    if (this.tieneArchivo(tipo)) return 'En revisión';
    return 'Pendiente';
  }

  clase(tipo: TipoDocumento): string {
    if (this.estaSubiendo(tipo)) return 'bg-blue-50 border-blue-200 text-blue-700';
    const estado = this.documento(tipo)?.estado;
    if (estado === 'aprobado') return 'bg-green-50 border-green-200 text-green-700';
    if (estado === 'requiere_correccion' || estado === 'rechazado') return 'bg-red-50 border-red-200 text-red-700';
    if (this.tieneArchivo(tipo)) return 'bg-blue-50 border-blue-200 text-blue-700';
    return 'bg-amber-50 border-amber-200 text-amber-800';
  }
}
