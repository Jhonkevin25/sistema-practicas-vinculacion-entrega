import { describe, expect, it } from 'vitest';
import { CorreoCola } from '../../core/services/correo.service';
import { puedeReintentarCorreo } from './correos';

const correoBase: CorreoCola = {
  id: 7,
  destinatario: 'estudiante@unibe.edu.ec',
  asunto: 'Notificación institucional',
  cuerpoHtml: '<p>Mensaje</p>',
  estado: 'FALLIDO',
  intentos: 3,
  ultimoError: 'SMTP no disponible',
  fechaCreacion: '2026-07-18T10:00:00',
  fechaActualizacion: '2026-07-18T10:05:00'
};

describe('acciones de la cola de correos', () => {
  it('permite reintentar únicamente correos fallidos', () => {
    expect(puedeReintentarCorreo(correoBase, null)).toBe(true);
    expect(puedeReintentarCorreo({ ...correoBase, estado: 'PENDIENTE' }, null)).toBe(false);
    expect(puedeReintentarCorreo({ ...correoBase, estado: 'ENVIADO' }, null)).toBe(false);
  });

  it('bloquea un segundo reintento del mismo correo mientras procesa', () => {
    expect(puedeReintentarCorreo(correoBase, correoBase.id)).toBe(false);
  });
});
