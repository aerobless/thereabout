import {ApplicationConfig, importProvidersFrom} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideHttpClient, withXhr } from "@angular/common/http";
import {Configuration, ThereaboutApiApiModule} from "../../generated/backend-api/thereabout";
import {provideAnimationsAsync} from "@angular/platform-browser/animations/async";
import {MessageService} from "primeng/api";
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

const ThereaboutPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '#F3F8FF',
      100: '#E8F2FF',
      200: '#CDDEF5',
      300: '#ABC7ED',
      400: '#7DA5DC',
      500: '#477ECA',
      600: '#3266AC',
      700: '#28538E',
      800: '#233F6B',
      900: '#101B4A',
      950: '#0B1336'
    },
    colorScheme: {
      light: {
        surface: {
          0: '#FFFFFF', 50: '#F8F7F3', 100: '#EEF1F5', 200: '#DCE3EE',
          300: '#A5B1C7', 400: '#73839F', 500: '#566786', 600: '#465675',
          700: '#324264', 800: '#1C294F', 900: '#101B4A', 950: '#0B1336'
        },
        primary: {color: '{primary.600}', contrastColor: '#FFFFFF', hoverColor: '{primary.700}', activeColor: '{primary.800}'},
        highlight: {background: '{primary.100}', focusBackground: '{primary.200}', color: '{surface.900}', focusColor: '{surface.900}'},
        text: {color: '{surface.900}', hoverColor: '{surface.950}', mutedColor: '{surface.500}', hoverMutedColor: '{surface.600}'},
        content: {background: '{surface.0}', hoverBackground: '{primary.50}', borderColor: '{surface.200}', color: '{surface.900}', hoverColor: '{surface.950}'}
      }
    }
  },
  components: {
    card: {root: {borderRadius: '10px', shadow: '0 2px 12px rgba(16, 27, 74, 0.04)'}},
    panel: {root: {borderRadius: '10px'}}
  }
});

const primeUiLicense = (globalThis as typeof globalThis & { primeUiLicense?: string }).primeUiLicense;

export const appConfig: ApplicationConfig = {
  providers: [provideRouter(routes), provideHttpClient(withXhr()), importProvidersFrom(
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
