import { firstValueFrom, of } from 'rxjs';

import { mapArray } from './map-array';

describe('mapArray', () => {
  it('maps every value emitted in an array', async () => {
    const values = firstValueFrom(of([1, 2, 3]).pipe(mapArray((value) => `item-${value}`)));

    await expect(values).resolves.toEqual(['item-1', 'item-2', 'item-3']);
  });
});
