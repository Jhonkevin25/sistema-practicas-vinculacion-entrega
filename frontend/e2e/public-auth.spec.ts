import { expect, test } from '@playwright/test';

test.describe('Autenticacion publica', () => {
  test('muestra el acceso institucional', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
    await expect(
      page.getByRole('img', { name: 'Universidad Iberoamericana del Ecuador' }).first(),
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Ingresar al sistema' })).toBeEnabled();
  });

  test('redirige una ruta protegida al inicio de sesion', async ({ page }) => {
    await page.goto('/dashboard/practicas');

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Iniciar sesión' })).toBeVisible();
  });

  test('rechaza credenciales invalidas con un mensaje generico', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Correo institucional').fill('e2e-inexistente@unibe.edu.ec');
    await page.getByLabel('Contraseña').fill('ClaveInvalida2026');
    await page.getByRole('button', { name: 'Ingresar al sistema' }).click();

    await expect(page.locator('.login-error')).toContainText('No se pudo iniciar sesión.');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('no revela si un correo existe al solicitar recuperacion', async ({ page }) => {
    await page.goto('/recuperar-password');
    await page.getByLabel('Correo institucional').fill('e2e-recuperacion-inexistente@unibe.edu.ec');
    await page.getByRole('button', { name: 'Enviar instrucciones' }).click();

    await expect(
      page.getByText(
        'Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.',
      ),
    ).toBeVisible();
  });
});
