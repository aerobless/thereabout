import {definePreset} from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

// Shared palette and PrimeNG control tokens; native controls consume the same tokens.
export const ThereaboutPreset = definePreset(Aura, {
  extend: {
    feedback: {
      success: {color: '#387F69', background: '#EDF6F1'},
      warning: {color: '#8B6329', background: '#FFF7E9'},
      danger: {color: '#9D402F', hoverColor: '#843526', background: '#FFF0ED'}
    }
  },
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
        primary: {color: '#24466C', contrastColor: '{surface.0}', hoverColor: '{primary.600}', activeColor: '{primary.800}'},
        highlight: {background: '{primary.100}', focusBackground: '{primary.200}', color: '{surface.900}', focusColor: '{surface.900}'},
        text: {color: '{surface.900}', hoverColor: '{surface.950}', mutedColor: '{surface.500}', hoverMutedColor: '{surface.600}'},
        content: {background: '{surface.0}', hoverBackground: '{primary.50}', borderColor: '{surface.200}', color: '{surface.900}', hoverColor: '{surface.950}'}
      }
    }
  },
  components: {
    button: {
      root: {
        borderRadius: '7px', paddingX: '13px', paddingY: '9px', fontSize: '13px',
        secondary: {
          background: '{surface.0}', hoverBackground: '{primary.50}', activeBackground: '{primary.100}',
          borderColor: '{surface.200}', hoverBorderColor: '{surface.200}', activeBorderColor: '{surface.300}',
          color: '{surface.900}', hoverColor: '{surface.900}', activeColor: '{surface.900}'
        },
        danger: {
          background: '{feedback.danger.color}', hoverBackground: '{feedback.danger.hover.color}', activeBackground: '{feedback.danger.hover.color}',
          borderColor: '{feedback.danger.color}', hoverBorderColor: '{feedback.danger.hover.color}', activeBorderColor: '{feedback.danger.hover.color}'
        }
      },
      outlined: {
        primary: {borderColor: '{surface.200}'},
        secondary: {color: '{surface.900}', hoverBackground: '{primary.50}'},
        danger: {color: '{feedback.danger.color}', borderColor: '{feedback.danger.color}', hoverBackground: '{feedback.danger.background}'}
      },
      text: {
        secondary: {color: '{surface.900}', hoverBackground: '{primary.50}'},
        danger: {color: '{feedback.danger.color}', hoverBackground: '{feedback.danger.background}'}
      }
    },
    card: {root: {borderRadius: '10px', shadow: '0 2px 12px rgba(16, 27, 74, 0.04)'}},
    panel: {root: {borderRadius: '10px'}}
  }
});
