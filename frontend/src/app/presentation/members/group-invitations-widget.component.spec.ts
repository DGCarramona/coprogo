import { TestBed } from '@angular/core/testing';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import {
  GroupInvitationsPort,
  SentGroupInvitation,
} from '../../application/group/group-invitations.port';
import { provideLocalization } from '../i18n/provide-localization';
import { GroupInvitationsWidgetComponent } from './group-invitations-widget.component';

class Invitations extends GroupInvitationsPort {
  rows: readonly SentGroupInvitation[] = [
    {
      invitation: 'inv-1',
      invitedMember: 'dana@example.com',
      invitedBy: 'bob@example.com',
      invitedAt: new Date('2026-01-01'),
      canCancel: true,
    },
    {
      invitation: 'inv-2',
      invitedMember: 'eve@example.com',
      invitedBy: 'alice@example.com',
      invitedAt: new Date('2026-01-02'),
      canCancel: false,
    },
  ];
  override async list() {
    return this.rows;
  }
  override async cancel() {
    this.rows = this.rows.filter((row) => row.invitation !== 'inv-1');
  }
}
describe('GroupInvitationsWidgetComponent', () => {
  it('shows pending invitations and allows cancellation only when authorized', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideLocalization(),
        provideTanStackQuery(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
        { provide: GroupInvitationsPort, useClass: Invitations },
      ],
    });
    const fixture = TestBed.createComponent(GroupInvitationsWidgetComponent);
    fixture.componentRef.setInput('groupId', 'group-1');
    fixture.autoDetectChanges();
    const host = fixture.nativeElement as HTMLElement;
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.textContent).toContain('dana@example.com');
    });
    expect(host.textContent).toContain('eve@example.com');
    expect(host.querySelectorAll('button')).toHaveLength(1);
    host.querySelector<HTMLButtonElement>('button')!.click();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.textContent).not.toContain('dana@example.com');
    });
    expect(host.textContent).toContain('eve@example.com');
  });
});
