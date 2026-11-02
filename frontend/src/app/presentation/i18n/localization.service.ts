import { DOCUMENT } from '@angular/common';
import { computed, Inject, Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export type Language = 'fr' | 'pt';

@Injectable({ providedIn: 'root' })
export class LocalizationService {
  readonly language = computed<Language>(() =>
    this.translate.currentLang() === 'pt' ? 'pt' : 'fr',
  );
  readonly locale = computed(() => (this.language() === 'pt' ? 'pt-PT' : 'fr-FR'));

  constructor(
    private readonly translate: TranslateService,
    @Inject(DOCUMENT) private readonly document: Document,
  ) {
    let language: Language = 'fr';
    try {
      if (document.defaultView?.localStorage.getItem('coprogo.language') === 'pt') language = 'pt';
    } catch {
      // Private browsing or browser policies may disable persistent storage.
    }
    this.selectLanguage(language);
  }

  selectLanguage(language: Language): void {
    this.translate.use(language);
    this.document.documentElement.lang = language === 'pt' ? 'pt-PT' : 'fr';
    try {
      this.document.defaultView?.localStorage.setItem('coprogo.language', language);
    } catch {
      // The current choice still works for this session.
    }
  }
}
