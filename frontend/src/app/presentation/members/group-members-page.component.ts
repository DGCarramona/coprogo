import { GroupInvitationsWidgetComponent } from './group-invitations-widget.component';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslatePipe } from '@ngx-translate/core';

import { ErrorMessagePipe } from '../i18n/error-message.pipe';
import { LocalizedDatePipe } from '../i18n/localized-date.pipe';
import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { GroupMembersPageViewModel } from './group-members-page.view-model';

@Component({
  selector: 'app-group-members-page',
  imports: [
    AuthenticatedShellComponent,
    GroupInvitationsWidgetComponent,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    TranslatePipe,
    ErrorMessagePipe,
    LocalizedDatePipe,
  ],
  templateUrl: './group-members-page.component.html',
  styleUrl: './group-members-page.component.scss',
  providers: [GroupMembersPageViewModel],
})
export class GroupMembersPageComponent {
  constructor(protected readonly viewModel: GroupMembersPageViewModel) {}

  async submit(event: Event, input: HTMLInputElement): Promise<void> {
    event.preventDefault();
    await this.viewModel.invite(input.value);
    if (this.viewModel.invitedEmail() !== null) input.value = '';
  }
}
