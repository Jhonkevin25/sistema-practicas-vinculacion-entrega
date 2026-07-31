import { CommonModule } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DocEstudiante } from '../../../core/services/documento.service';

export type ProcesoRevision = 'TODOS' | 'GENERAL' | 'PRACTICAS' | 'VINCULACION';
export type EstadoRevision = 'TODOS' | 'pendiente' | 'cargado' | 'en_revision' | 'aprobado' | 'rechazado' | 'requiere_correccion';

export interface FiltrosDocumentosRevision {
  proceso: ProcesoRevision;
  estado: EstadoRevision;
  carrera: string;
}

export type TipoAccionDocumentoRevision = 'ver' | 'aprobar' | 'corregir';

export interface AccionDocumentoRevision {
  id: number;
  tipo: TipoAccionDocumentoRevision;
}

export interface GrupoDocumentosRevision {
  clave: string;
  nombre: string;
  email: string;
  carrera: string;
  documentos: DocEstudiante[];
  aprobados: number;
  pendientes: number;
}

@Component({
  selector: 'app-documentos-revision-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './documentos-revision-panel.html'
})
export class DocumentosRevisionPanelComponent {
  documentos = input.required<DocEstudiante[]>();
  carreras = input<string[]>([]);
  proceso = input<ProcesoRevision>('TODOS');
  estado = input<EstadoRevision>('TODOS');
  carrera = input('TODOS');
  observaciones = input<Record<number, string>>({});
  cargando = input(false);
  accionEnCurso = input<AccionDocumentoRevision | null>(null);

  busqueda = signal('');
  gruposExpandidos = signal<Set<string>>(new Set());

  grupos = computed<GrupoDocumentosRevision[]>(() => {
    const agrupados = new Map<string, GrupoDocumentosRevision>();
    for (const doc of this.documentos()) {
      const estudiante = doc.estudiante;
      const usuario = estudiante?.usuario;
      const clave = estudiante?.id != null
        ? `estudiante-${estudiante.id}`
        : `correo-${usuario?.email || this.nombreEstudiante(doc)}`;
      let grupo = agrupados.get(clave);
      if (!grupo) {
        grupo = {
          clave,
          nombre: this.nombreEstudiante(doc),
          email: usuario?.email || '',
          carrera: estudiante?.carrera || doc.carrera || 'Sin carrera',
          documentos: [],
          aprobados: 0,
          pendientes: 0
        };
        agrupados.set(clave, grupo);
      }
      grupo.documentos.push(doc);
      if (doc.estado === 'aprobado') grupo.aprobados++;
      else grupo.pendientes++;
    }

    const termino = this.normalizar(this.busqueda());
    return [...agrupados.values()]
      .filter(grupo => !termino || this.normalizar(
        `${grupo.nombre} ${grupo.email} ${grupo.carrera}`
      ).includes(termino))
      .map(grupo => ({
        ...grupo,
        documentos: [...grupo.documentos].sort((a, b) =>
          this.tipoDocumentoTexto(a.tipoDocumento)
            .localeCompare(this.tipoDocumentoTexto(b.tipoDocumento), 'es'))
      }))
      .sort((a, b) =>
        b.pendientes - a.pendientes || a.nombre.localeCompare(b.nombre, 'es'));
  });

  filtrosChange = output<FiltrosDocumentosRevision>();
  actualizar = output<void>();
  ver = output<DocEstudiante>();
  aprobar = output<DocEstudiante>();
  corregir = output<DocEstudiante>();

  cambiarFiltro(campo: keyof FiltrosDocumentosRevision, valor: string): void {
    const filtros: FiltrosDocumentosRevision = {
      proceso: this.proceso(),
      estado: this.estado(),
      carrera: this.carrera()
    };
    if (campo === 'proceso') filtros.proceso = valor as ProcesoRevision;
    if (campo === 'estado') filtros.estado = valor as EstadoRevision;
    if (campo === 'carrera') filtros.carrera = valor;
    this.filtrosChange.emit(filtros);
  }

  actualizarObservacion(id: number | undefined, valor: string): void {
    if (id !== undefined) this.observaciones()[id] = valor;
  }

  alternarGrupo(clave: string): void {
    const estaExpandido = this.gruposExpandidos().has(clave);
    this.gruposExpandidos.set(estaExpandido ? new Set() : new Set([clave]));
  }

  grupoExpandido(clave: string): boolean {
    return this.gruposExpandidos().has(clave);
  }

  estaProcesando(doc: DocEstudiante, tipo: TipoAccionDocumentoRevision): boolean {
    return doc.id != null
      && this.accionEnCurso()?.id === doc.id
      && this.accionEnCurso()?.tipo === tipo;
  }

  documentoOcupado(doc: DocEstudiante): boolean {
    return doc.id != null && this.accionEnCurso()?.id === doc.id;
  }

  nombreEstudiante(doc: DocEstudiante): string {
    const usuario = doc.estudiante?.usuario;
    return usuario ? `${usuario.nombre || ''} ${usuario.apellido || ''}`.trim() : 'Sin estudiante';
  }

  claseEstado(estado?: string): string {
    if (estado === 'aprobado') return 'bg-green-50 border-green-200 text-green-700';
    if (estado === 'requiere_correccion' || estado === 'rechazado') return 'bg-red-50 border-red-200 text-red-700';
    return 'bg-amber-50 border-amber-200 text-amber-800';
  }

  etiquetaEstado(estado?: string): string {
    if (estado === 'aprobado') return 'Aprobado';
    if (estado === 'requiere_correccion') return 'Requiere corrección';
    if (estado === 'cargado' || estado === 'en_revision') return 'Pendiente de revisión';
    return estado || 'Pendiente';
  }

  // Solo carta/carta_aceptacion de PRACTICAS traen etapa poblada (backend
  // Fase >47); '' cuando no aplica, para que el revisor sepa a cual de las
  // dos practicas corresponde el documento.
  etapaTexto(etapa?: string): string {
    if (etapa === 'PRACTICA_1') return 'Práctica I';
    if (etapa === 'PRACTICA_2') return 'Práctica II';
    return '';
  }

  tipoDocumentoTexto(tipo?: string): string {
    const etiquetas: Record<string, string> = {
      cv: 'Hoja de vida',
      cedula: 'Copia de cédula',
      carta: 'Carta de solicitud',
      carta_aceptacion: 'Carta de aceptación',
      informe_final: 'Informe final',
      certificado: 'Certificado'
    };
    return etiquetas[tipo || ''] || tipo || 'Documento';
  }

  private normalizar(valor: string): string {
    return valor.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  }
}
