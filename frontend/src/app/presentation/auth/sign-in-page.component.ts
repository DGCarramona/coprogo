import { Component, ElementRef, OnInit, afterNextRender, inject, viewChild } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { SignInPageViewModel } from './sign-in-page.view-model';

@Component({
  selector: 'app-sign-in-page',
  imports: [MatCardModule, MatChipsModule, MatDividerModule, MatProgressBarModule],
  templateUrl: './sign-in-page.component.html',
  styleUrl: './sign-in-page.component.scss',
  providers: [SignInPageViewModel],
})
export class SignInPageComponent implements OnInit {
  private readonly viewModel = inject(SignInPageViewModel);

  protected readonly googleButtonHost = viewChild.required<ElementRef<HTMLDivElement>>('googleButtonHost');

  constructor() {
    afterNextRender(() => {
      this.viewModel.mountGoogleButton(this.googleButtonHost().nativeElement);
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
