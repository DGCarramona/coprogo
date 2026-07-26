import { NavigationPort } from '../../../../src/app/application/shared/navigation.port';

export class StubNavigationPort extends NavigationPort {
  navigatedTo: string | null = null;

  override async navigateByUrl(url: string): Promise<boolean> {
    this.navigatedTo = url;
    return true;
  }
}
