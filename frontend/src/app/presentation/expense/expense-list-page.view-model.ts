import { Injectable } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Injectable()
export class ExpenseListPageViewModel {
  readonly groupId: string | null;

  constructor(route: ActivatedRoute) {
    this.groupId = route.snapshot.paramMap.get('groupId');
  }
}
