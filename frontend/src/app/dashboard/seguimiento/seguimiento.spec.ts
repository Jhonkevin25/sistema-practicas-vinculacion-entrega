import { describe, expect, it } from 'vitest';
import { SeguimientoPractica } from '../../core/services/practica.service';
import { SeguimientoVinculacion } from '../../core/services/vinculacion.service';
import {
  filtrarSeguimientos,
  puedeFinalizarExpediente,
  unificarSeguimientos,
  validarFinalizacion
} from './seguimiento';

const base = {
  estudianteId: 1,
  estudiante: 'Ana Perez',
  carrera: 'Software',
  porcentajeAvance: 50,
  bitacorasPendientes: 0,
  bitacorasRechazadas: 0,
  bitacorasCorreccion: 0,
  asistenciasRegistradas: 2,
  parcialesCerrados: 1,
  encuestasCompletadas: 1,
  notasTutorPendientes: 0,
  notasCoordPendientes: 0,
  encuestasPendientes: 0,
  listoParaCierre: false,
  riesgo: false,
  alertas: []
};

describe('seguimiento unificado', () => {
  it('combina prácticas y vinculación conservando su identidad de proceso', () => {
    const practica: SeguimientoPractica = { ...base, practicaId: 7, empresa: 'Empresa Uno' };
    const vinculacion: SeguimientoVinculacion = { ...base, vinculacionId: 7, proyecto: 'Proyecto Uno' };

    const resultado = unificarSeguimientos([practica], [vinculacion]);

    expect(resultado).toHaveLength(2);
    expect(resultado.map(item => item.clave)).toContain('PRACTICAS-7');
    expect(resultado.map(item => item.clave)).toContain('VINCULACION-7');
    expect(resultado.find(item => item.proceso === 'PRACTICAS')?.entidad).toBe('Empresa Uno');
    expect(resultado.find(item => item.proceso === 'VINCULACION')?.entidad).toBe('Proyecto Uno');
  });

  it('prioriza los expedientes marcados en riesgo por el backend', () => {
    const normal: SeguimientoPractica = { ...base, practicaId: 1, estudiante: 'Ana Perez' };
    const riesgo: SeguimientoPractica = { ...base, practicaId: 2, estudiante: 'Zoe Ruiz', riesgo: true };

    expect(unificarSeguimientos([normal, riesgo], [])[0].expedienteId).toBe(2);
  });

  it('evita mostrar días negativos cuando existen actividades con fecha futura', () => {
    const practica: SeguimientoPractica = { ...base, practicaId: 3, diasSinActividad: -2 };

    expect(unificarSeguimientos([practica], [])[0].diasSinActividad).toBe(0);
  });

  it('filtra por proceso, texto y pendientes sin recalcular reglas académicas', () => {
    const practica: SeguimientoPractica = {
      ...base,
      practicaId: 1,
      estudiante: 'Ana Perez',
      empresa: 'Hospital Central',
      notasCoordPendientes: 1
    };
    const vinculacion: SeguimientoVinculacion = {
      ...base,
      vinculacionId: 2,
      estudiante: 'Luis Mora',
      proyecto: 'Fundacion Norte'
    };
    const items = unificarSeguimientos([practica], [vinculacion]);

    expect(filtrarSeguimientos(items, {
      busqueda: 'hospital',
      proceso: 'PRACTICAS',
      pendiente: 'NOTAS'
    }).map(item => item.expedienteId)).toEqual([1]);
  });

  it('habilita la finalización solo para expedientes pendientes o en curso', () => {
    expect(puedeFinalizarExpediente({ estado: 'pendiente' })).toBe(true);
    expect(puedeFinalizarExpediente({ estado: 'EN_CURSO' })).toBe(true);
    expect(puedeFinalizarExpediente({ estado: 'reprobado' })).toBe(false);
    expect(puedeFinalizarExpediente({ estado: 'retirado' })).toBe(false);
  });

  it('valida el estado permitido y el motivo mínimo de una finalización', () => {
    expect(validarFinalizacion({ estado: 'pendiente' }, 'REPROBADO', 'Motivo suficientemente largo')).toContain('solo está disponible');
    expect(validarFinalizacion({ estado: 'en_curso' }, 'RETIRADO', 'Retiro solicitado por el estudiante')).toBeNull();
    expect(validarFinalizacion({ estado: 'en_curso' }, 'RETIRADO', 'corto')).toContain('10 caracteres');
    expect(validarFinalizacion({ estado: 'completado' }, 'RETIRADO', 'Motivo suficientemente largo')).toContain('estado terminal');
  });
});
