import { provideTranslateService, TranslateLoader } from '@ngx-translate/core';
import { of } from 'rxjs';

import fr from './catalogs/fr.json';
import pt from './catalogs/pt.json';

const catalogs = { fr, pt: pt satisfies Record<keyof typeof fr, string> };

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
