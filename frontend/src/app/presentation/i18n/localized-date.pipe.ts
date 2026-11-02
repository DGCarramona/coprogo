import { Pipe, PipeTransform } from '@angular/core';

import { FinancialFormatter } from './financial-formatter';

@Pipe({ name: 'localizedDate', pure: false })
export class LocalizedDatePipe implements PipeTransform {
  constructor(private readonly formatter: FinancialFormatter) {}

  transform(date: Date): string {
    return this.formatter.dateTime(date);
  }
}
