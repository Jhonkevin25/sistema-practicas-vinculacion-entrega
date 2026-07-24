import { expect, Page, test } from '@playwright/test';

type RolE2E = 'ADMIN' | 'COORDINADOR' | 'TUTOR' | 'ESTUDIANTE';

function credenciales(rol: RolE2E) {
  return {
    correo: process.env[`E2E_${rol}_EMAIL`],
    contrasena: process.env[`E2E_${rol}_PASSWORD`],
  };
}

async function iniciarSesion(page: Page, correo: string, contrasena: string) {
  await page.goto('/login');
  await page.getByLabel('Correo institucional').fill(correo);
  await page.getByLabel('Contraseña').fill(contrasena);
  await page.getByRole('button', { name: 'Ingresar al sistema' }).click();

  await expect(page).toHaveURL(/\/dashboard(?:\/overview)?$/);
  await expect(page.getByRole('button', { name: 'Salir' })).toBeVisible();
}

test.describe('Navegacion autorizada por rol', () => {
  test('ADMIN ve la administracion completa', async ({ page }) => {
    const cuenta = credenciales('ADMIN');
    test.skip(!cuenta.correo || !cuenta.contrasena, 'Faltan E2E_ADMIN_EMAIL y E2E_ADMIN_PASSWORD');

    await iniciarSesion(page, cuenta.correo!, cuenta.contrasena!);
    const navegacion = page.getByRole('navigation');
    await expect(navegacion.getByRole('link', { name: 'Usuarios', exact: true })).toBeVisible();
    await expect(
      navegacion.getByRole('link', { name: 'Importaciones', exact: true }),
    ).toBeVisible();
    await expect(navegacion.getByRole('link', { name: 'Estudiantes', exact: true })).toBeVisible();
  });

  test('COORDINADOR no ve administracion de usuarios ni importaciones', async ({ page }) => {
    const cuenta = credenciales('COORDINADOR');
    test.skip(
      !cuenta.correo || !cuenta.contrasena,
      'Faltan E2E_COORDINADOR_EMAIL y E2E_COORDINADOR_PASSWORD',
    );

    await iniciarSesion(page, cuenta.correo!, cuenta.contrasena!);
    const navegacion = page.getByRole('navigation');
    await expect(navegacion.getByRole('link', { name: 'Estudiantes', exact: true })).toBeVisible();
    await expect(navegacion.getByRole('link', { name: 'Usuarios', exact: true })).toHaveCount(0);
    await expect(navegacion.getByRole('link', { name: 'Importaciones', exact: true })).toHaveCount(
      0,
    );
  });

  test('TUTOR ve solo los procesos asignados a su area', async ({ page }) => {
    const cuenta = credenciales('TUTOR');
    test.skip(!cuenta.correo || !cuenta.contrasena, 'Faltan E2E_TUTOR_EMAIL y E2E_TUTOR_PASSWORD');

    await iniciarSesion(page, cuenta.correo!, cuenta.contrasena!);
    const navegacion = page.getByRole('navigation');
    await expect(navegacion.getByRole('link', { name: 'Inicio', exact: true })).toBeVisible();
    await expect(
      navegacion.getByRole('link', { name: /^(Prácticas|Vinculación)$/ }),
    ).not.toHaveCount(0);
    await expect(navegacion.getByRole('link', { name: 'Usuarios', exact: true })).toHaveCount(0);
    await expect(navegacion.getByRole('link', { name: 'Estudiantes', exact: true })).toHaveCount(0);
  });

  test('ESTUDIANTE ve sus dos procesos y no los catalogos administrativos', async ({ page }) => {
    const cuenta = credenciales('ESTUDIANTE');
    test.skip(
      !cuenta.correo || !cuenta.contrasena,
      'Faltan E2E_ESTUDIANTE_EMAIL y E2E_ESTUDIANTE_PASSWORD',
    );

    await iniciarSesion(page, cuenta.correo!, cuenta.contrasena!);
    const navegacion = page.getByRole('navigation');
    await expect(navegacion.getByRole('link', { name: 'Prácticas', exact: true })).toBeVisible();
    await expect(navegacion.getByRole('link', { name: 'Vinculación', exact: true })).toBeVisible();
    await expect(navegacion.getByRole('link', { name: 'Usuarios', exact: true })).toHaveCount(0);
    await expect(navegacion.getByRole('link', { name: 'Estudiantes', exact: true })).toHaveCount(0);
  });
});
