import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { submit } from '@angular/forms/signals';
import { provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { Observable, of, throwError } from 'rxjs';

import { GroupMembersPort } from '../../../application/group/group-members.port';
import {
  DirectReimbursementRecordingPort,
  type DirectReimbursementRecordingCommand,
} from '../../../application/reimbursement/direct-reimbursement-recording.port';
import type { GroupMember } from '../../../domain/group/group-member';
import { DirectReimbursementWidgetViewModel } from './direct-reimbursement-widget.view-model';

describe('DirectReimbursementWidgetViewModel', () => {
  let queryClient: QueryClient;
  let membersPort: StubGroupMembersPort;
  let recordingPort: StubDirectReimbursementRecordingPort;
  let injector: Injector;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    TestBed.configureTestingModule({ providers: [provideTanStackQuery(queryClient)] });
    membersPort = new StubGroupMembersPort();
    recordingPort = new StubDirectReimbursementRecordingPort();
    injector = TestBed.inject(Injector);
  });

  describe('initialize', () => {
    it('loads the members of the initialized group', async () => {
      const viewModel = createViewModel();

      viewModel.initialize('group-1');
      await waitFor(() => viewModel.members().length === 2);

      expect(viewModel.members()).toEqual(['alice@example.com', 'bob@example.com']);
      expect(membersPort.requestedGroupIds).toEqual(['group-1']);
    });

    it('starts with an invalid empty form', () => {
      const viewModel = createViewModel();

      viewModel.initialize('group-1');

      expect(viewModel.form().value()).toEqual({
        paidBy: '',
        amountInEuros: '',
        reimbursedAt: '',
      });
      expect(viewModel.form().invalid()).toBe(true);
    });

    it('retries loading the members after a failure', async () => {
      membersPort.failure = new Error('Membres indisponibles');
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      await waitFor(() => viewModel.hasLoadError());

      expect(viewModel.loadErrorMessage()).toBe('Membres indisponibles');

      membersPort.failure = null;
      viewModel.retry();
      await waitFor(() => viewModel.members().length === 2);

      expect(membersPort.requestedGroupIds).toEqual(['group-1', 'group-1']);
    });
  });

  describe('form submission', () => {
    it('records the exact direct reimbursement and invalidates its group history', async () => {
      queryClient.setQueryData(['groups', 'group-1', 'reimbursements'], ['cached']);
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, validFormValue());

      await expect(submit(viewModel.form)).resolves.toBe(true);

      expect(recordingPort.commands).toEqual([
        {
          groupId: 'group-1',
          paidBy: 'bob@example.com',
          amountInCents: 2550,
          reimbursedAt: new Date('2026-10-01T09:30'),
        },
      ]);
      expect(
        queryClient.getQueryState(['groups', 'group-1', 'reimbursements'])?.isInvalidated,
      ).toBe(true);
      await waitFor(() => viewModel.isRecorded());
      expect(viewModel.isRecorded()).toBe(true);
      expect(viewModel.form().value()).toEqual({
        paidBy: '',
        amountInEuros: '',
        reimbursedAt: '',
      });
    });

    it.each([
      {
        name: 'without a payer',
        value: validFormValue({ paidBy: '' }),
      },
      {
        name: 'with an invalid amount',
        value: validFormValue({ amountInEuros: '25,555' }),
      },
      {
        name: 'without a reimbursement date',
        value: validFormValue({ reimbursedAt: '' }),
      },
      {
        name: 'with an invalid reimbursement date',
        value: validFormValue({ reimbursedAt: 'date-invalide' }),
      },
    ])('rejects submission $name', async ({ value }) => {
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);

      expect(recordingPort.commands).toEqual([]);
    });

    it('keeps the form values and exposes the error when recording fails', async () => {
      recordingPort.failure = new Error('Service indisponible');
      const viewModel = createViewModel();
      viewModel.initialize('group-1');
      const value = validFormValue();
      fillForm(viewModel, value);

      await expect(submit(viewModel.form)).resolves.toBe(false);
      await waitFor(() => viewModel.hasRecordingError());

      expect(viewModel.form().value()).toEqual(value);
      expect(viewModel.recordingErrorMessage()).toBe('Service indisponible');
    });
  });

  const createViewModel = (): DirectReimbursementWidgetViewModel => {
    const viewModel = new DirectReimbursementWidgetViewModel(
      membersPort,
      recordingPort,
      queryClient,
      injector,
    );
    TestBed.tick();
    return viewModel;
  };
});

interface FormValue {
  readonly paidBy: string;
  readonly amountInEuros: string;
  readonly reimbursedAt: string;
}

const validFormValue = (overrides: Partial<FormValue> = {}): FormValue => ({
  paidBy: 'bob@example.com',
  amountInEuros: '25,50',
  reimbursedAt: '2026-10-01T09:30',
  ...overrides,
});

const fillForm = (viewModel: DirectReimbursementWidgetViewModel, value: FormValue): void => {
  viewModel.form.paidBy().value.set(value.paidBy);
  viewModel.form.amountInEuros().value.set(value.amountInEuros);
  viewModel.form.reimbursedAt().value.set(value.reimbursedAt);
};

class StubGroupMembersPort extends GroupMembersPort {
  readonly members: readonly GroupMember[] = [
    { member: 'alice@example.com', joinedAt: new Date('2026-01-01T00:00:00Z') },
    { member: 'bob@example.com', joinedAt: new Date('2026-01-02T00:00:00Z') },
  ];
  failure: Error | null = null;
  readonly requestedGroupIds: string[] = [];

  override listByGroup(groupId: string): Observable<readonly GroupMember[]> {
    this.requestedGroupIds.push(groupId);
    return this.failure === null ? of(this.members) : throwError(() => this.failure);
  }
}

class StubDirectReimbursementRecordingPort extends DirectReimbursementRecordingPort {
  readonly commands: DirectReimbursementRecordingCommand[] = [];
  failure: Error | null = null;

  override async record(command: DirectReimbursementRecordingCommand): Promise<void> {
    this.commands.push(command);
    if (this.failure) throw this.failure;
  }
}

const waitFor = async (condition: () => boolean): Promise<void> => {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    TestBed.tick();
    if (condition()) return;
    await new Promise((resolve) => setTimeout(resolve));
  }

  throw new Error('La condition attendue n a pas ete atteinte.');
};
