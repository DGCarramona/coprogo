import { TranslatePipe } from '@ngx-translate/core';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthSessionFacade } from '../../../application/auth/auth-session.facade';
import { NavigationPort } from '../../../application/shared/navigation.port';
import { forgetGroup, rememberGroup } from '../../../application/shared/remembered-group';

@Component({
  selector: 'app-authenticated-shell',
  imports: [TranslatePipe, MatButtonModule, RouterLink, RouterLinkActive],
  templateUrl: './authenticated-shell.component.html',
  styleUrl: './authenticated-shell.component.scss',
})
export class AuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';

  readonly groupId: string | null;

  constructor(
    route: ActivatedRoute,
    private readonly authSessionFacade: AuthSessionFacade,
    private readonly navigation: NavigationPort,
    private readonly queryClient: QueryClient,
  ) {
    this.groupId = route.snapshot.paramMap.get('groupId');
    if (this.groupId !== null) rememberGroup(this.groupId);
  }

  async signOut(): Promise<void> {
    forgetGroup();
    this.authSessionFacade.signOut();
    this.queryClient.clear();
    await this.navigation.navigateByUrl('/connexion');
  }
}
