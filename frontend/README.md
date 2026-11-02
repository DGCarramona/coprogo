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

## Languages

The SPA supports French (`fr-FR`, the default) and European Portuguese (`pt-PT`).
The language selector switches the current screen without navigation and remembers the choice
under `coprogo.language` in browser storage. Blocked storage does not prevent switching languages.

Translation sources are plain JSON files: `src/app/presentation/i18n/catalogs/fr.json` and
`pt.json`. Angular imports them through its native build pipeline (`resolveJsonModule`):
`npm start` recompiles the changed catalog and reloads the page during development;
`npm run build` embeds the catalogs in the optimized production JavaScript bundles under
`dist/frontend/browser/`. No separate generation command or runtime catalog request is needed.

Use nested dictionaries grouped by feature, with stable technical ASCII keys in English.
Reference their leaf messages through dotted paths, for example
`expenses.proposal.title`, `cashPool.withdrawal.submit` and `errors.expenses.loadList`.
Accents and punctuation belong in translated values, never in keys. Changing display copy does
not require renaming its key. The loader checks both catalogs recursively at compile time,
rejecting missing or additional keys and mismatches between dictionaries and message strings.
Keep interpolation parameters consistent between both catalogs.
Translate complete sentences with parameters rather than concatenating translated fragments.
Group names, expense titles, member addresses, filenames and rejection reasons remain user data.

Use `TranslatePipe` for application copy, `ErrorMessagePipe` for errors (unknown server messages
receive a translated fallback), and `FinancialFormatter` / `LocalizedDatePipe` for localized display.
Keep raw server data in TanStack Query and derive formatted values in computed Signals, so a language
change updates cached results without fetching them again. Amounts, currency and date input values
remain unchanged.

## Browser tests (Playwright)

Install the browser once after `npm ci`, then run the tests:

```bash
npm run test:e2e:install
npm run test:e2e
```

Playwright starts Angular automatically at `http://127.0.0.1:4201` and stops it afterwards.
An existing server on that address is reused locally. The default projects run Chromium with
a desktop viewport and a Pixel 7 mobile viewport. No backend or Google credentials are needed
for the initial smoke test: it checks the signed-out redirect, page rendering and reload.
The external Google script is blocked only within that test.

```bash
npm run test:e2e -- --project=chromium       # Desktop only
npm run test:e2e -- e2e/app.spec.ts         # One test file
npm run test:e2e:ui                        # Interactive runner
npm run test:e2e:debug                     # Inspector and step-by-step debugging
npm run test:e2e:report                    # Last HTML report
npm run test:e2e:typecheck                 # Check test and configuration types
```

Add browser scenarios under `e2e/*.spec.ts` when a real browser adds useful coverage of a workflow
or layout. Use accessible roles/labels, awaited Playwright assertions and isolated test data;
keep business rule tests in the existing unit suites. Angular unit tests remain scoped to `src/`.
There is no browser suite for individual translations and the browser command runs separately
from `npm test` and the current CI checks.

Failures produce screenshots and traces under `test-results/`, plus an HTML report under
`playwright-report/`; both directories are ignored by Git and Prettier. View a trace with
`npx playwright show-trace test-results/<test-directory>/trace.zip`.

For a Linux CI runner, install browser system dependencies with
`npx playwright install --with-deps chromium`, then run `npm run test:e2e:typecheck`
and `npm run test:e2e`. With `CI=true`, Playwright requires a fresh server, forbids focused tests
and retries a failing test once. Upload `playwright-report/` and `test-results/` on failure
when adding a browser job to CI.
