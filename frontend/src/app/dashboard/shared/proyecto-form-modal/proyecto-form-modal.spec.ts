import { describe, expect, it } from 'vitest';
import { maximoReservaProyecto } from './proyecto-form-modal';

describe('capacidad oficial para proyectos de vinculación', () => {
  it('usa los cupos disponibles calculados por el backend', () => {
    const maximo = maximoReservaProyecto({ activo: true, cuposDisponibles: 3 });

    expect(maximo).toBe(3);
  });

  it('reintegra la reserva actual al editar sin ampliar la oferta', () => {
    const maximo = maximoReservaProyecto(
      { activo: true, cuposDisponibles: 2 },
      4
    );

    expect(maximo).toBe(6);
  });

  it('no permite reservar desde una oferta inactiva o inexistente', () => {
    expect(maximoReservaProyecto({ activo: false, cuposDisponibles: 8 })).toBe(0);
    expect(maximoReservaProyecto(undefined)).toBe(0);
  });
});
