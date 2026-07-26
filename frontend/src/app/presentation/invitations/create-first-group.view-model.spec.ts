import { GroupCreationPort } from '../../application/group/group-creation.port';
import { CreateFirstGroupViewModel } from './create-first-group.view-model';

describe('CreateFirstGroupViewModel', () => {
  it('creates a group', async () => {
    const groupCreationPort = new StubGroupCreationPort();
    const viewModel = createViewModel(groupCreationPort);

    await viewModel.createGroup();

    expect(groupCreationPort.createCalls).toBe(1);
    expect(viewModel.createdGroupId()).toBe('group-1');
    expect(viewModel.isCreatingGroup()).toBe(false);
  });

  it('exposes creation errors', async () => {
    const groupCreationPort = new StubGroupCreationPort();
    groupCreationPort.failure = new Error('Creation impossible');
    const viewModel = createViewModel(groupCreationPort);

    await viewModel.createGroup();

    expect(viewModel.createdGroupId()).toBeNull();
    expect(viewModel.errorMessage()).toBe('Creation impossible');
    expect(viewModel.isCreatingGroup()).toBe(false);
  });
});

const createViewModel = (groupCreationPort: StubGroupCreationPort): CreateFirstGroupViewModel =>
  new CreateFirstGroupViewModel(groupCreationPort);

class StubGroupCreationPort extends GroupCreationPort {
  createCalls = 0;
  failure: Error | null = null;

  override async create(): Promise<string> {
    this.createCalls += 1;
    if (this.failure) {
      throw this.failure;
    }

    return 'group-1';
  }
}
