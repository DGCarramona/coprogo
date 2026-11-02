import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { TranslatePipe } from '@ngx-translate/core';
import { ErrorMessagePipe } from '../i18n/error-message.pipe';
import { LocalizedDatePipe } from '../i18n/localized-date.pipe';
import { GroupInvitationsViewModel } from './group-invitations.view-model';

@Component({
  selector: 'app-group-invitations-widget',
  imports: [MatButtonModule, MatCardModule, TranslatePipe, ErrorMessagePipe, LocalizedDatePipe],
  templateUrl: './group-invitations-widget.component.html',
  styleUrl: './group-invitations-widget.component.scss',
  providers: [GroupInvitationsViewModel],
})
export class GroupInvitationsWidgetComponent {
  @Input({ required: true }) set groupId(value: string) {
    this.viewModel.setGroup(value);
  }
  constructor(protected readonly viewModel: GroupInvitationsViewModel) {}
}
