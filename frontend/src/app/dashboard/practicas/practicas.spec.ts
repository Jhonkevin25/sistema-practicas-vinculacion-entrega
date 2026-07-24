import { describe, expect, it } from 'vitest';
import { Carrera } from '../../core/services/carrera.service';
import { OfertaCuposEmpresa } from '../../core/services/oferta-cupos-empresa.service';
import {
  asistenciaParaPractica,
  carrerasBaseParaVacante,
  cuposDisponiblesOferta,
  postulacionPracticaActiva,
  tienePracticaActiva
} from './practicas';

const catalogo: Carrera[] = [
  { id: 1, nombre: 'Derecho', activo: true },
  { id: 2, nombre: 'Ingeniería en Software', activo: true },
  { id: 3, nombre: 'Carrera inactiva', activo: false }
];

describe('carreras disponibles al publicar vacantes', () => {
  it('permite al administrador usar todo el catálogo activo', () => {
    expect(carrerasBaseParaVacante('ADMIN', catalogo, [])).toEqual([
      'Derecho',
      'Ingeniería en Software'
    ]);
  });

  it('limita al coordinador a las carreras de su alcance', () => {
    expect(carrerasBaseParaVacante(
      'COORDINADOR',
      catalogo,
      ['Ingenieria en Software']
    )).toEqual(['Ingeniería en Software']);
  });

  it('no habilita la configuración para otros roles', () => {
    expect(carrerasBaseParaVacante('TUTOR', catalogo, ['Derecho'])).toEqual([]);
  });
});

describe('cupos disponibles de la oferta empresarial', () => {
  const base: OfertaCuposEmpresa = {
    id: 1,
    empresa: { id: 1, ruc: '1790000000001', nombre: 'Empresa' },
    periodoAcademico: '2026-2',
    distribucion: 'GENERAL',
    cuposTotales: 5,
    activo: true,
    carreras: [],
    cuposDisponibles: 2
  };

  it('comparte el saldo cuando la distribución es general', () => {
    expect(cuposDisponiblesOferta(base, 'Derecho')).toBe(2);
  });

  it('usa solo la asignación de la carrera y compara sin tildes', () => {
    const porCarrera: OfertaCuposEmpresa = {
      ...base,
      distribucion: 'POR_CARRERA',
      carreras: [{
        carrera: { id: 2, nombre: 'Ingeniería en Software', activo: true },
        cupos: 3,
        cuposDisponibles: 1
      }]
    };
    expect(cuposDisponiblesOferta(porCarrera, 'Ingenieria en Software')).toBe(1);
    expect(cuposDisponiblesOferta(porCarrera, 'Derecho')).toBe(0);
  });
});

describe('estado de postulaciones de prácticas', () => {
  it('mantiene activas solo las postulaciones pendientes o procesadas', () => {
    expect(postulacionPracticaActiva('Pendiente')).toBe(true);
    expect(postulacionPracticaActiva('PROCESADO')).toBe(true);
    expect(postulacionPracticaActiva('Aprobado')).toBe(false);
    expect(postulacionPracticaActiva('Rechazado')).toBe(false);
  });

  it('no habilita bitácoras por intentos históricos terminados', () => {
    expect(tienePracticaActiva([{ estado: 'reprobado' }, { estado: 'retirado' }])).toBe(false);
    expect(tienePracticaActiva([{ estado: 'completado' }, { estado: 'en_curso' }])).toBe(true);
  });
});

describe('asistencia de prácticas', () => {
  it('envía la práctica exacta sin confiar en un estudiante del cliente', () => {
    expect(asistenciaParaPractica(27, {
      fecha: '2026-07-15',
      horaIngreso: '08:00',
      horaSalida: '12:00',
      estado: 'Presente',
      observaciones: ''
    })).toEqual({
      practica: { id: 27 },
      fecha: '2026-07-15',
      horaIngreso: '08:00',
      horaSalida: '12:00',
      estado: 'Presente',
      observaciones: ''
    });
  });
});
