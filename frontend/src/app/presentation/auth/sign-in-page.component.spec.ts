import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SignInPageComponent } from './sign-in-page.component';
import { SignInPageViewModel } from './sign-in-page.view-model';

describe('SignInPageComponent', () => {
  it('explains the first sign-in and keeps the Google action prominent', () => {
    const fixture = createFixture();

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('h1')?.textContent?.trim()).toBe(
      'Les dépenses partagées, plus simples.',
    );
    expect(host.querySelector('.sign-in-page__button-host')).not.toBeNull();
    expect(host.textContent).toContain('Votre compte est créé automatiquement');
    expect(host.textContent).not.toContain('backend');
    expect(host.textContent).not.toContain('identité canonique');
  });
});

const createFixture = (): ComponentFixture<SignInPageComponent> => {
  TestBed.configureTestingModule({ imports: [SignInPageComponent] });
  TestBed.overrideComponent(SignInPageComponent, {
    set: {
      providers: [
        {
          provide: SignInPageViewModel,
          useValue: {
            initialize: vi.fn(),
            mountGoogleButton: vi.fn(),
            renderErrorMessage: () => null,
            sessionErrorMessage: () => null,
            isBusy: () => false,
            hasGoogleConfiguration: () => true,
          },
        },
      ],
      imports: [MatCardModule, MatChipsModule, MatDividerModule, MatProgressBarModule],
    },
  });

  return TestBed.createComponent(SignInPageComponent);
};
