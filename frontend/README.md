# FrontendApp

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.17.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

El proyecto usa Playwright con Chromium. Después de `npm ci`, instala el navegador
una vez:

```bash
npx playwright install chromium
```

Las pruebas públicas no necesitan cuentas. Las pruebas por rol solo se ejecutan
cuando están definidas las variables `E2E_*` correspondientes:

```bash
npm run e2e:public
npm run e2e
npm run e2e:roles
```

Playwright inicia o reutiliza el backend y el frontend locales. La configuración
completa, las variables y el uso en CI están en
`../docs/pruebas-e2e-y-ci.md`.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
