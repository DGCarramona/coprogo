import { LocalizationService } from '../i18n/localization.service';
import { ErrorMessagePipe } from '../i18n/error-message.pipe';
import { TranslatePipe } from '@ngx-translate/core';
import { Component, ElementRef, OnInit, afterRenderEffect, viewChild } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { SignInPageViewModel } from './sign-in-page.view-model';

@Component({
  selector: 'app-sign-in-page',
  imports: [
    ErrorMessagePipe,
    TranslatePipe,
    MatCardModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressBarModule,
  ],
  templateUrl: './sign-in-page.component.html',
  styleUrl: './sign-in-page.component.scss',
  providers: [SignInPageViewModel],
})
export class SignInPageComponent implements OnInit {
  protected readonly googleButtonHost =
    viewChild.required<ElementRef<HTMLDivElement>>('googleButtonHost');

  constructor(
    private readonly viewModel: SignInPageViewModel,
    private readonly localization: LocalizationService,
  ) {
    afterRenderEffect(() => {
      this.viewModel.mountGoogleButton(
        this.googleButtonHost().nativeElement,
        this.localization.locale(),
      );
    });
  }

  ngOnInit(): void {
    void this.viewModel.initialize();
  }

  protected sessionErrorMessage(): string | null {
    return this.viewModel.sessionErrorMessage();
  }

  protected renderErrorMessage(): string | null {
    return this.viewModel.renderErrorMessage();
  }

  protected isBusy(): boolean {
    return this.viewModel.isBusy();
  }

  protected hasGoogleConfiguration(): boolean {
    return this.viewModel.hasGoogleConfiguration();
  }
}
