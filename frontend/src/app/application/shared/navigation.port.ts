export abstract class NavigationPort {
  abstract navigateByUrl(url: string): Promise<boolean>;
}
