import type { GeneratorConfig } from 'ng-openapi';

export default {
  input: 'http://localhost:8080/openapi.yml',
  output: 'src/app/infrastructure/api/generated',
  options: {
    dateType: 'Date',
    enumStyle: 'union',
    generateServices: true,
    responseTypeMapping: {
      'application/yaml': 'text',
    },
  },
} satisfies GeneratorConfig;
