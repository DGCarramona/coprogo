import { Injectable } from '@angular/core';
import { Router } from '@angular/router';

import { NavigationPort } from '../../application/shared/navigation.port';

@Injectable({ providedIn: 'root' })
export class RouterNavigationAdapter extends NavigationPort {
  constructor(private readonly router: Router) {
    super();
  }

  override async navigateByUrl(url: string): Promise<boolean> {
    return this.router.navigateByUrl(url);
  }
}
