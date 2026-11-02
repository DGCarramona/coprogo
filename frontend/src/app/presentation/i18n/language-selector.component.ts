import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { LocalizationService } from './localization.service';

@Component({
  selector: 'app-language-selector',
  imports: [TranslatePipe],
  templateUrl: './language-selector.component.html',
  styleUrl: './language-selector.component.scss',
})
export class LanguageSelectorComponent {
  constructor(readonly localization: LocalizationService) {}

  select(event: Event): void {
    const language = (event.target as HTMLSelectElement).value;
    if (language === 'fr' || language === 'pt') this.localization.selectLanguage(language);
  }
}
