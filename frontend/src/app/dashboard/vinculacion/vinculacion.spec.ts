import { describe, expect, it } from 'vitest';
import { asistenciaParaVinculacion, tieneVinculacionActiva } from './vinculacion';

describe('actividad de vinculación', () => {
  it('considera activos solo los estados pendiente y en curso', () => {
    expect(tieneVinculacionActiva([{ estado: 'pendiente' }])).toBe(true);
    expect(tieneVinculacionActiva([{ estado: 'EN_CURSO' }])).toBe(true);
  });

  it('no bloquea un nuevo intento por expedientes históricos', () => {
    expect(tieneVinculacionActiva([
      { estado: 'reprobado' },
      { estado: 'retirado' },
      { estado: 'completado' }
    ])).toBe(false);
  });
});

describe('asistencia de vinculación', () => {
  it('envía la vinculación exacta sin confiar en un estudiante del cliente', () => {
    expect(asistenciaParaVinculacion(41, {
      fecha: '2026-07-15',
      horaIngreso: '08:00',
      horaSalida: '12:00',
      estado: 'Atraso',
      observaciones: 'Ingreso posterior al horario.'
    })).toEqual({
      vinculacion: { id: 41 },
      fecha: '2026-07-15',
      horaIngreso: '08:00',
      horaSalida: '12:00',
      estado: 'Atraso',
      observaciones: 'Ingreso posterior al horario.'
    });
  });
});
