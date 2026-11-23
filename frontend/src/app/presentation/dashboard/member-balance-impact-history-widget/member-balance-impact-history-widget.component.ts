import { ErrorMessagePipe } from '../../i18n/error-message.pipe';
import { TranslatePipe } from '@ngx-translate/core';
import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { MemberBalanceImpactHistoryWidgetViewModel } from './member-balance-impact-history-widget.view-model';

@Component({
  selector: 'app-member-balance-impact-history-widget',
  imports: [ErrorMessagePipe, TranslatePipe, MatButtonModule, MatProgressBarModule],
  templateUrl: './member-balance-impact-history-widget.component.html',
  styleUrl: './member-balance-impact-history-widget.component.scss',
  providers: [MemberBalanceImpactHistoryWidgetViewModel],
})
export class MemberBalanceImpactHistoryWidgetComponent {
  @Input({ required: true }) groupId!: string;
  @Input({ required: true }) member!: string;

  constructor(protected readonly viewModel: MemberBalanceImpactHistoryWidgetViewModel) {}

  protected load(): void {
    this.viewModel.load(this.groupId, this.member);
  }
}
