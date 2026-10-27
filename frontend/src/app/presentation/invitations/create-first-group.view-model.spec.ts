import { GroupCreationPort } from '../../application/group/group-creation.port';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';
import { QueryClient } from '@tanstack/angular-query-experimental';

describe('CreateFirstGroupViewModel', () => {
  describe('createGroup', () => {
    it.each(['', '   ', 'a'.repeat(101)])(
      'rejects an invalid name %j without creating a group',
      async (name) => {
        const port = new StubGroupCreationPort();
        const viewModel = createViewModel(port);
        await viewModel.createGroup(name);
        expect(port.createCalls).toBe(0);
        expect(viewModel.errorMessage()).not.toBeNull();
      },
    );
    it('invalidates the member groups after creation', async () => {
      const queryClient = new QueryClient();
      queryClient.setQueryData(['member-groups'], []);
      const viewModel = new CreateFirstGroupViewModel(new StubGroupCreationPort(), queryClient);

      await viewModel.createGroup('Appartement');

      expect(queryClient.getQueryState(['member-groups'])?.isInvalidated).toBe(true);
    });
    it('creates a group', async () => {
      const groupCreationPort = new StubGroupCreationPort();
      const viewModel = createViewModel(groupCreationPort);

      await viewModel.createGroup('  Appartement familial  ');

      expect(groupCreationPort.createCalls).toBe(1);
      expect(groupCreationPort.names).toEqual(['Appartement familial']);
      expect(viewModel.createdGroupId()).toBe('group-1');
      expect(viewModel.isCreatingGroup()).toBe(false);
    });

    it('exposes creation errors', async () => {
      const groupCreationPort = new StubGroupCreationPort();
      groupCreationPort.failure = new Error('Creation impossible');
      const viewModel = createViewModel(groupCreationPort);

      await viewModel.createGroup('Appartement');

      expect(viewModel.createdGroupId()).toBeNull();
      expect(viewModel.errorMessage()).toBe('Creation impossible');
      expect(viewModel.isCreatingGroup()).toBe(false);
    });
  });
});

const createViewModel = (groupCreationPort: StubGroupCreationPort): CreateFirstGroupViewModel =>
  new CreateFirstGroupViewModel(groupCreationPort, new QueryClient());

class StubGroupCreationPort extends GroupCreationPort {
  createCalls = 0;
  names: string[] = [];
  failure: Error | null = null;

  override async create(name: string): Promise<string> {
    this.createCalls += 1;
    this.names.push(name);
    if (this.failure) {
      throw this.failure;
    }

    return 'group-1';
  }
}
