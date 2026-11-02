import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import fr from './catalogs/fr.json';

interface TranslationDictionary {
  readonly [key: string]: string | TranslationDictionary;
}

const catalogEntries = (catalog: TranslationDictionary, prefix = ''): [string, string][] =>
  Object.entries(catalog).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === 'string' ? [[path, value]] : catalogEntries(value, path);
  });

const frenchMessages = new Map(catalogEntries(fr));

@Pipe({ name: 'errorMessage', pure: false })
export class ErrorMessagePipe implements PipeTransform {
  constructor(private readonly translate: TranslateService) {}

  transform(message: string | null | undefined): string {
    if (!message) return '';
    // Older API responses may still carry text instead of a stable error code.
    const key = frenchMessages.has(message)
      ? message
      : ([...frenchMessages].find(
          ([key, text]) => key.startsWith('errors.') && text === message,
        )?.[0] ?? 'errors.unexpected');
    const translated = this.translate.instant(key);
    return typeof translated === 'string' ? translated : '';
  }
}
