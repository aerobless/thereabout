import {ApplicationConfig, importProvidersFrom} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import {provideHttpClient} from "@angular/common/http";
import {Configuration, ThereaboutApiApiModule} from "../../generated/backend-api/thereabout";
import {provideAnimations} from "@angular/platform-browser/animations";
import {MessageService} from "primeng/api";

export const appConfig: ApplicationConfig = {
  providers: [provideRouter(routes), provideHttpClient(), importProvidersFrom(
      ThereaboutApiApiModule.forRoot(() => new Configuration({ basePath: '' })),
  ), provideAnimations(), MessageService]
};
