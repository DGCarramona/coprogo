import { computed, Injectable, Injector } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { MemberGroupsPort } from '../../application/group/member-groups.port';
import { describeError } from '../../application/shared/describe-error';
import { memberGroupsQueryKey } from './member-groups-query';

@Injectable()
export class MemberGroupsViewModel {
  private readonly groupsQuery;
  readonly groups = computed(() => this.groupsQuery.data() ?? []);
  readonly isLoading = computed(() => this.groupsQuery.isPending());
  readonly hasLoadError = computed(() => this.groupsQuery.isError());
  readonly errorMessage = computed(() =>
    describeError(this.groupsQuery.error(), 'Vos groupes n’ont pas pu être chargés.'),
  );

  constructor(port: MemberGroupsPort, injector: Injector) {
    this.groupsQuery = injectQuery(
      () => ({
        queryKey: memberGroupsQueryKey,
        queryFn: () => port.list(),
      }),
      { injector },
    );
  }

  retry(): void {
    void this.groupsQuery.refetch();
  }
}
