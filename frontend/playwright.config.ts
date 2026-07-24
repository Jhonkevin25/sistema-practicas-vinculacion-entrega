import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env['E2E_BASE_URL'] ?? 'http://localhost:4200';
const reuseExistingServer = !process.env['CI'];
const backendCommand =
  process.platform === 'win32'
    ? 'cd ..\\backend\\sistema-practicas && gradlew.bat bootRun --no-daemon'
    : 'cd ../backend/sistema-practicas && ./gradlew bootRun --no-daemon';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: process.env['E2E_BASE_URL']
    ? undefined
    : [
        {
          command: backendCommand,
          url: 'http://127.0.0.1:8080/api/practicas',
          reuseExistingServer,
          timeout: 120_000,
          stdout: 'pipe',
          stderr: 'pipe',
        },
        {
          command: 'npm start -- --host localhost --port 4200',
          url: baseURL,
          reuseExistingServer,
          timeout: 120_000,
          stdout: 'pipe',
          stderr: 'pipe',
        },
      ],
});
