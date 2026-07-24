import { describe, expect, it } from 'vitest';
import { Usuario } from '../../core/services/usuario.service';
import { esMismoUsuario } from './usuarios';

const usuario: Usuario = {
  id: 1,
  nombre: 'Administradora',
  apellido: 'Sistema',
  email: 'Admin@unibe.edu.ec'
};

describe('gestión segura de usuarios', () => {
  it('reconoce la cuenta conectada sin depender de mayúsculas', () => {
    expect(esMismoUsuario(usuario, 'admin@unibe.edu.ec')).toBe(true);
  });

  it('no confunde otra cuenta con el usuario conectado', () => {
    expect(esMismoUsuario(usuario, 'coordinador@unibe.edu.ec')).toBe(false);
  });
});
