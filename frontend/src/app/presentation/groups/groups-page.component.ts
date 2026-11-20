import { TranslatePipe } from '@ngx-translate/core';
import { Component } from '@angular/core';

import { AuthenticatedShellComponent } from '../shared/authenticated-shell/authenticated-shell.component';
import { CreateFirstGroupCardComponent } from './create-first-group-card.component';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';
import { MemberGroupsWidgetComponent } from './member-groups-widget.component';

@Component({
  selector: 'app-groups-page',
  imports: [
    TranslatePipe,
    AuthenticatedShellComponent,
    CreateFirstGroupCardComponent,
    MemberGroupsWidgetComponent,
  ],
  templateUrl: './groups-page.component.html',
  styleUrl: './groups-page.component.scss',
  providers: [CreateFirstGroupViewModel],
})
export class GroupsPageComponent {
  constructor(protected readonly createGroupViewModel: CreateFirstGroupViewModel) {}
}
