import { ErrorMessagePipe } from '../i18n/error-message.pipe';
import { TranslatePipe } from '@ngx-translate/core';
import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { ExpenseDetailPageViewModel } from './expense-detail-page.view-model';

@Component({
  selector: 'app-expense-detail-page',
  imports: [
    ErrorMessagePipe,
    TranslatePipe,
    AuthenticatedShellComponent,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatProgressBarModule,
    RouterLink,
  ],
  templateUrl: './expense-detail-page.component.html',
  styleUrl: './expense-detail-page.component.scss',
  providers: [ExpenseDetailPageViewModel],
})
export class ExpenseDetailPageComponent {
  constructor(protected readonly viewModel: ExpenseDetailPageViewModel) {}

  protected updateRejectionReason(event: Event): void {
    this.viewModel.rejectionReason.set((event.target as HTMLTextAreaElement).value);
  }
}
