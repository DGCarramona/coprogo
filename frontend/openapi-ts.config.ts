import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: 'http://localhost:8080/swagger/coprogo-1.0.0.yml',
  output: 'src/app/infrastructure/api/generated',
});
