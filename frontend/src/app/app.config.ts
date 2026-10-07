import {ApplicationConfig, importProvidersFrom, inject, provideAppInitializer} from '@angular/core';
import { provideRouter } from '@angular/router';

import {CurrentUserService} from './shared/current-user/current-user.service';
import {userContextInterceptor} from './shared/current-user/user-context.interceptor';
import { routes } from './app.routes';
import { provideHttpClient, withXhr, withInterceptors } from "@angular/common/http";
import {Configuration, ThereaboutApiApiModule} from "../../generated/backend-api/thereabout";
import {provideAnimationsAsync} from "@angular/platform-browser/animations/async";
import {MessageService} from "primeng/api";
import { providePrimeNG } from 'primeng/config';
import {ThereaboutPreset} from './shared/styles/app-theme';

const primeUiLicense = (globalThis as typeof globalThis & { primeUiLicense?: string }).primeUiLicense;

export const appConfig: ApplicationConfig = {
  providers: [provideAppInitializer(() => inject(CurrentUserService).start()), provideRouter(routes), provideHttpClient(withXhr(), withInterceptors([userContextInterceptor])), importProvidersFrom(
      ThereaboutApiApiModule.forRoot(() => new Configuration({ basePath: '' })),
  ), provideAnimationsAsync(), MessageService,
  providePrimeNG({
    license: primeUiLicense,
    theme: {
      preset: ThereaboutPreset,
      options: {darkModeSelector: false}
    }
  })]
};
