import { provideTranslateService, TranslateLoader } from '@ngx-translate/core';
import { of } from 'rxjs';

import fr from './catalogs/fr.json';
import pt from './catalogs/pt.json';

type TranslationShape<T> = {
  [Key in keyof T]: T[Key] extends string ? string : TranslationShape<T[Key]>;
};

// Check both directions to reject missing and additional keys at every depth.
const catalogs = {
  fr: fr satisfies TranslationShape<typeof pt>,
  pt: pt satisfies TranslationShape<typeof fr>,
};

// Both catalogs ship with the application: switching language needs no network request.
export const provideLocalization = () =>
  provideTranslateService({
    lang: 'fr',
    fallbackLang: 'fr',
    loader: {
      provide: TranslateLoader,
      useValue: {
        getTranslation: (language: string) => of(language === 'pt' ? catalogs.pt : catalogs.fr),
      },
    },
  });
