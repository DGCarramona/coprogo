import { map } from 'rxjs';
import type { OperatorFunction } from 'rxjs';

export const mapArray = <T, R>(mapper: (value: T) => R): OperatorFunction<T[], R[]> =>
  map((values) => values.map(mapper));
