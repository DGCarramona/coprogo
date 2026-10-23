# Frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 21.2.2.

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

### Public application configuration

The application reads these public configuration values:

| Variable               | Purpose                        | Default when absent        |
| ---------------------- | ------------------------------ | -------------------------- |
| `APP_API_BASE_URL`     | Backend origin, without `/api` | `http://localhost:8080`    |
| `APP_GOOGLE_CLIENT_ID` | Google OAuth web client ID     | Google sign-in unavailable |

Angular CLI does not automatically expose shell variables through Vite's `import.meta.env` API.
`angular.json` defines an empty object for local defaults. Deployment builds replace that object
using Angular CLI's native `--define` option; no custom builder or wrapper is required.

Set both variables in the build environment, then run the following command from `frontend/`
(POSIX shell):

```sh
npm ci && npm run build -- --define "import.meta.env={\"APP_API_BASE_URL\":\"${APP_API_BASE_URL}\",\"APP_GOOGLE_CLIENT_ID\":\"${APP_GOOGLE_CLIENT_ID}\"}"
```

Serve the generated static files from `dist/frontend/browser`.
Both variables must contain their public URL/client ID values without surrounding quotes.
After changing a variable, trigger a new build and deploy: these values are compiled into the
JavaScript files, not read from the hosting environment by the browser. Never add passwords or
storage access keys to the definition.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

## API client generation (OpenAPI)

The frontend can generate a typed API client from the backend OpenAPI specification with:

```bash
npm run generate:api
```

By default, the generator reads the public backend endpoint `http://localhost:8080/openapi.yml`. Ensure the backend is running before generating the client.
