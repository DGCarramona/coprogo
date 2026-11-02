import {
  formatMoneyFromCents,
  formatSignedMoneyFromCents,
  formatPercentage,
  formatDate,
} from '../../shared/format/financial-format';
import { Injectable } from '@angular/core';

import { LocalizationService } from './localization.service';

@Injectable({ providedIn: 'root' })
export class FinancialFormatter {
  constructor(private readonly localization: LocalizationService) {}

  money(amountInCents: number): string {
    return formatMoneyFromCents(amountInCents, this.localization.locale());
  }

  signedMoney(amountInCents: number): string {
    return formatSignedMoneyFromCents(amountInCents, this.localization.locale());
  }

  percentage(percentage: number): string {
    return formatPercentage(percentage, this.localization.locale());
  }

  date(date: Date): string {
    return formatDate(date, this.localization.locale());
  }

  dateTime(date: Date): string {
    return new Intl.DateTimeFormat(this.localization.locale(), {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  }
}
