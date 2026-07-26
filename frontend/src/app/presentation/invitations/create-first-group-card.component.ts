import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-create-first-group-card',
  imports: [RouterLink, MatButtonModule, MatCardModule],
  templateUrl: './create-first-group-card.component.html',
})
export class CreateFirstGroupCardComponent {
  readonly createdGroupId = input<string | null>(null);
  readonly errorMessage = input<string | null>(null);
  readonly creating = input(false);
  readonly createRequested = output<void>();
}
