import { Practica } from '../../core/services/practica.service';
import { Vinculacion } from '../../core/services/vinculacion.service';
import { construirOpcionesExpediente, periodoNotasPorDefecto } from './expediente-academico';

describe('expediente-academico', () => {
  it('usa el periodo actual del estudiante cuando tiene notas registradas ahí', () => {
    expect(periodoNotasPorDefecto(['2026-1', '2025-2'], '2026-1')).toBe('2026-1');
  });

  it('cae al periodo más reciente con notas cuando el actual no tiene', () => {
    expect(periodoNotasPorDefecto(['2025-2', '2025-1'], '2026-1')).toBe('2025-2');
    expect(periodoNotasPorDefecto([], '2026-1')).toBe('TODOS');
  });

  it('ordena los expedientes con los activos primero y luego por periodo reciente', () => {
    const practicas = [
      { id: 1, estado: 'completado', periodoAcademico: '2026-1', empresa: { nombre: 'TechCorp' } },
      { id: 2, estado: 'en_curso', periodoAcademico: '2026-1', empresa: { nombre: 'InnovaSoft' } }
    ] as Practica[];
    const vinculaciones = [
      { id: 5, estado: 'completado', periodoAcademico: '2025-2', proyecto: { nombre: 'Proyecto Jurídico' } }
    ] as Vinculacion[];

    const opciones = construirOpcionesExpediente(practicas, vinculaciones);
    expect(opciones.map(o => o.clave)).toEqual(['P-2', 'P-1', 'V-5']);
    expect(opciones[0].activo).toBe(true);
    expect(opciones[0].etiqueta).toContain('InnovaSoft');
    expect(opciones[2].etiqueta).toContain('Proyecto Jurídico');
  });
});
