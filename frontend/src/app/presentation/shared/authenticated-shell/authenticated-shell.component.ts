import { Component, Input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthSessionFacade } from '../../../application/auth/auth-session.facade';
import { NavigationPort } from '../../../application/shared/navigation.port';

@Component({
  selector: 'app-authenticated-shell',
  imports: [MatButtonModule, MatToolbarModule],
  templateUrl: './authenticated-shell.component.html',
  styleUrl: './authenticated-shell.component.scss',
})
export class AuthenticatedShellComponent {
  @Input() eyebrow = '';
  @Input() title = '';
  @Input() description = '';

  constructor(
    private readonly authSessionFacade: AuthSessionFacade,
    private readonly navigation: NavigationPort,
  ) {}

  async signOut(): Promise<void> {
    this.authSessionFacade.signOut();
    await this.navigation.navigateByUrl('/connexion');
  }
}
