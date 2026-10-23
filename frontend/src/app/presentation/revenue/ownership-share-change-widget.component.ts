import { Component, input, OnInit } from '@angular/core';
import { FormField, FormRoot } from '@angular/forms/signals';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { OwnershipShareChangeWidgetViewModel } from './ownership-share-change-widget.view-model';

@Component({
  selector: 'app-ownership-share-change-widget',
  imports: [FormField, FormRoot, MatButtonModule, MatCardModule],
  providers: [OwnershipShareChangeWidgetViewModel],
  templateUrl: './ownership-share-change-widget.component.html',
  styleUrl: './ownership-share-change-widget.component.scss',
})
export class OwnershipShareChangeWidgetComponent implements OnInit {
  readonly groupId = input.required<string>();

  constructor(readonly viewModel: OwnershipShareChangeWidgetViewModel) {}

  ngOnInit(): void {
    this.viewModel.initialize(this.groupId());
  }

  updatePercentage(member: string, event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) this.viewModel.setPercentage(member, input.value);
  }
}
