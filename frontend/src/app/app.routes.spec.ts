import {Component, inject} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {ActivatedRoute, provideRouter, Router} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {routes} from './app.routes';

@Component({template: ''})
class RoutePage {
  readonly route = inject(ActivatedRoute);
}

// Keep the real routing and guards, without loading maps or making page API requests.
const testRoutes = routes.map(route => {
  if (route.redirectTo !== undefined) return route;
  const {loadComponent, component, ...routing} = route;
  return {...routing, component: RoutePage};
});

describe('Application destinations', () => {
  beforeEach(() => TestBed.configureTestingModule({providers: [provideRouter(testRoutes)]}));

  it('opens the launcher at root and keeps the old launcher URL as an alias', async () => {
    const harness = await RouterTestingHarness.create();
    const root = await harness.navigateByUrl('/', RoutePage);
    expect(root.route.snapshot.routeConfig?.path).toBe('');
    await harness.navigateByUrl('/launcher?source=bookmark#shortcuts', RoutePage);
    expect(TestBed.inject(Router).url).toBe('/?source=bookmark#shortcuts');
  });

  it('preserves dates, other query parameters and fragments on legacy day links', async () => {
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/?date=2026-06-16&source=trip#map', RoutePage);
    expect(TestBed.inject(Router).url).toBe('/dayview?date=2026-06-16&source=trip#map');
    expect(page.route.snapshot.data['mapPage']).toBe('day');
  });

  it('redirects a date query added while the launcher is already open', async () => {
    const harness = await RouterTestingHarness.create('/');
    await harness.navigateByUrl('/?date=2026-06-17', RoutePage);
    expect(TestBed.inject(Router).url).toBe('/dayview?date=2026-06-17');
  });

  it('keeps dated Day View and embedded trip routes intact', async () => {
    const harness = await RouterTestingHarness.create();
    const day = await harness.navigateByUrl('/dayview?date=2026-06-16', RoutePage);
    expect(day.route.snapshot.data['mapPage']).toBe('day');
    const url = '/locationhistory?fromDate=2026-06-13&toDate=2026-06-20&embed=true';
    const trip = await harness.navigateByUrl(url, RoutePage);
    expect(TestBed.inject(Router).url).toBe(url);
    expect(trip.route.snapshot.data['mapPage']).toBe('locations');
  });
});
