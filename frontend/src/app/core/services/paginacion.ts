import { HttpParams } from '@angular/common/http';

// Fase 43: envelope uniforme de los listados paginados del backend
// (espejo de PaginaResponse<T> en el API).
export interface Paginado<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

export const TAMANO_PAGINA_DEFECTO = 20;

// Construye los query params de un listado paginado omitiendo los filtros
// vacíos y el valor centinela 'TODOS' de los selects.
export function paramsPaginado(
  pagina: number,
  tamano: number,
  filtros: Record<string, string | number | boolean | null | undefined> = {},
  sortBy?: string,
  sortDir?: string
): HttpParams {
  let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
  if (sortBy) params = params.set('sortBy', sortBy);
  if (sortDir) params = params.set('sortDir', sortDir);

  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor === null || valor === undefined) continue;
    const texto = `${valor}`.trim();
    if (texto === '' || texto === 'TODOS') continue;
    params = params.set(clave, texto);
  }
  return params;
}
