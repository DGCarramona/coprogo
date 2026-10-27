import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MemberGroupsViewModel } from './member-groups.view-model';

@Component({
  selector: 'app-member-groups-widget',
  imports: [RouterLink, MatButtonModule, MatCardModule],
  providers: [MemberGroupsViewModel],
  templateUrl: './member-groups-widget.component.html',
  styleUrl: './member-groups-widget.component.scss',
})
export class MemberGroupsWidgetComponent {
  constructor(readonly viewModel: MemberGroupsViewModel) {}
}
