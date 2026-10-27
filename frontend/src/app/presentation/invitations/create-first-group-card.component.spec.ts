import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CreateFirstGroupCardComponent } from './create-first-group-card.component';

describe('CreateFirstGroupCardComponent', () => {
  it('requests creation with the entered group name', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(CreateFirstGroupCardComponent);
    const names: string[] = [];
    fixture.componentInstance.createRequested.subscribe((name) => names.push(name));
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const input = host.querySelector<HTMLInputElement>('input[aria-label="Nom du groupe"]');
    if (!input) throw new Error('Champ de nom absent');
    input.value = 'Appartement familial';
    expect(input.required).toBe(true);
    expect(input.maxLength).toBe(100);

    host
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    expect(names).toEqual(['Appartement familial']);
  });
});
