import { Component } from '@angular/core';
import { FormField, FormRoot } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';

import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { RevenueDistributionPreviewPageViewModel } from './revenue-distribution-preview-page.view-model';

@Component({
  selector: 'app-revenue-distribution-preview-page',
  imports: [
    AuthenticatedShellComponent,
    FormField,
    FormRoot,
    MatButtonModule,
    MatCardModule,
    RouterLink,
  ],
  providers: [RevenueDistributionPreviewPageViewModel],
  templateUrl: './revenue-distribution-preview-page.component.html',
  styleUrl: './revenue-distribution-preview-page.component.scss',
})
export class RevenueDistributionPreviewPageComponent {
  constructor(protected readonly viewModel: RevenueDistributionPreviewPageViewModel) {}
}
