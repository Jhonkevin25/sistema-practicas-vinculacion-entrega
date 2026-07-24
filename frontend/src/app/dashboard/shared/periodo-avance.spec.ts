import { SIN_PERIODO, coincidePeriodo, periodosDisponibles, porcentajeAvance } from './periodo-avance';

describe('periodo-avance', () => {
  it('lista los periodos sin duplicados, del más reciente al más antiguo', () => {
    expect(periodosDisponibles(['2025-2', '2026-1', '2025-2'])).toEqual(['2026-1', '2025-2']);
  });

  it('agrupa los expedientes sin periodo al final bajo SIN_PERIODO', () => {
    expect(periodosDisponibles(['2026-1', null, undefined, ' '])).toEqual(['2026-1', SIN_PERIODO]);
  });

  it('coincide segun la seleccion: TODOS, un periodo concreto o SIN_PERIODO', () => {
    expect(coincidePeriodo('TODOS', '2025-2')).toBe(true);
    expect(coincidePeriodo('2026-1', '2026-1')).toBe(true);
    expect(coincidePeriodo('2026-1', '2025-2')).toBe(false);
    expect(coincidePeriodo(SIN_PERIODO, null)).toBe(true);
    expect(coincidePeriodo(SIN_PERIODO, '2026-1')).toBe(false);
  });

  it('calcula el porcentaje de avance acotado entre 0 y 100', () => {
    expect(porcentajeAvance(4, 96)).toBe(4);
    expect(porcentajeAvance(48, 96)).toBe(50);
    expect(porcentajeAvance(200, 96)).toBe(100);
    expect(porcentajeAvance(4, 0)).toBe(0);
    expect(porcentajeAvance(undefined, undefined)).toBe(0);
  });
});
