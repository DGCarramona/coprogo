import { ErrorMessagePipe } from '../../i18n/error-message.pipe';
import { TranslatePipe } from '@ngx-translate/core';
import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { MemberDebtReductionHistoryWidgetViewModel } from './member-debt-reduction-history-widget.view-model';

@Component({
  selector: 'app-member-debt-reduction-history-widget',
  imports: [ErrorMessagePipe, TranslatePipe, MatButtonModule, MatProgressBarModule],
  templateUrl: './member-debt-reduction-history-widget.component.html',
  styleUrl: './member-debt-reduction-history-widget.component.scss',
  providers: [MemberDebtReductionHistoryWidgetViewModel],
})
export class MemberDebtReductionHistoryWidgetComponent {
  @Input({ required: true }) groupId!: string;
  @Input({ required: true }) member!: string;

  constructor(protected readonly viewModel: MemberDebtReductionHistoryWidgetViewModel) {}

  protected load(): void {
    this.viewModel.load(this.groupId, this.member);
  }
}
