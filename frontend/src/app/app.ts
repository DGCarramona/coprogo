import { LanguageSelectorComponent } from './presentation/i18n/language-selector.component';
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, LanguageSelectorComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
