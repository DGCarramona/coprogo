import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { ExpenseListPageViewModel } from './expense-list-page.view-model';

describe('ExpenseListPageViewModel', () => {
  describe('groupId', () => {
    it('exposes the group selected by the route', () => {
      const viewModel = new ExpenseListPageViewModel(routeWithGroup('group-1'));

      expect(viewModel.groupId).toBe('group-1');
    });

    it('exposes a missing route group', () => {
      const viewModel = new ExpenseListPageViewModel(routeWithGroup(null));

      expect(viewModel.groupId).toBeNull();
    });
  });
});

const routeWithGroup = (groupId: string | null): ActivatedRoute =>
  ({
    snapshot: {
      paramMap: convertToParamMap(groupId === null ? {} : { groupId }),
    },
  }) as ActivatedRoute;
