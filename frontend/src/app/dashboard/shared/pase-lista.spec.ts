import { Asistencia } from '../../core/services/asistencia.service';
import { asistenciaEnFecha, fechaLocalISO, nuevaFilaPase } from './pase-lista';

describe('pase-lista', () => {
  it('premarca cada fila como Presente y sin observaciones', () => {
    expect(nuevaFilaPase()).toEqual({ estado: 'Presente', observaciones: '' });
  });

  it('genera la fecha local en formato yyyy-MM-dd', () => {
    expect(fechaLocalISO(new Date(2026, 6, 18))).toBe('2026-07-18');
    expect(fechaLocalISO(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('detecta la asistencia ya registrada del expediente en la fecha', () => {
    const asistencias = [
      { id: 1, fecha: '2026-07-18', vinculacion: { id: 5 }, estado: 'Presente' },
      { id: 2, fecha: '2026-07-18', practica: { id: 3 }, estado: 'Atraso' },
      { id: 3, fecha: '2026-07-17', vinculacion: { id: 5 }, estado: 'Falta' }
    ] as Asistencia[];

    expect(asistenciaEnFecha(asistencias, '2026-07-18', { vinculacionId: 5 })?.id).toBe(1);
    expect(asistenciaEnFecha(asistencias, '2026-07-18', { practicaId: 3 })?.id).toBe(2);
    expect(asistenciaEnFecha(asistencias, '2026-07-18', { vinculacionId: 99 })).toBeUndefined();
    expect(asistenciaEnFecha(asistencias, '2026-07-16', { vinculacionId: 5 })).toBeUndefined();
  });
});
