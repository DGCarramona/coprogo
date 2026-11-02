import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import fr from './catalogs/fr.json';

@Pipe({ name: 'errorMessage', pure: false })
export class ErrorMessagePipe implements PipeTransform {
  constructor(private readonly translate: TranslateService) {}

  transform(message: string | null | undefined): string {
    if (!message) return '';
    // Older API responses may still carry text instead of a stable error code.
    const key = Object.hasOwn(fr, message)
      ? message
      : (Object.entries(fr).find(
          ([key, text]) => key.startsWith('errors.') && text === message,
        )?.[0] ?? 'errors.unexpected');
    const translated = this.translate.instant(key);
    return typeof translated === 'string' ? translated : '';
  }
}
