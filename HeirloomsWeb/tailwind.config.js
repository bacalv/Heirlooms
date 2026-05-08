/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,jsx}',
  ],
  theme: {
    extend: {
      colors: {
        parchment: '#F2EEDF',
        forest: '#3F4F33',
        bloom: '#D89B85',
        earth: '#B5694B',
        'new-leaf': '#7DA363',
        ink: '#2C2A26',
        'forest-04': 'rgba(63, 79, 51, 0.04)',
        'forest-08': 'rgba(63, 79, 51, 0.08)',
        'forest-15': 'rgba(63, 79, 51, 0.15)',
        'forest-25': 'rgba(63, 79, 51, 0.25)',
        'forest-75': 'rgba(63, 79, 51, 0.75)',
        'bloom-15': 'rgba(216, 155, 133, 0.15)',
        'earth-10': 'rgba(181, 105, 75, 0.10)',
        'text-primary': '#2C2A26',
        'text-body': '#5A5A52',
        'text-muted': '#6B7559',
      },
      fontFamily: {
        serif: ['Georgia', '"Times New Roman"', 'serif'],
        sans: ['-apple-system', 'system-ui', '"Segoe UI"', 'Roboto', 'sans-serif'],
        mono: ['ui-monospace', '"SF Mono"', 'Menlo', 'monospace'],
      },
      borderRadius: {
        chip: '12px',
        card: '10px',
        button: '6px',
      },
      transitionTimingFunction: {
        natural: 'cubic-bezier(0.4, 0.0, 0.2, 1)',
      },
    },
  },
  plugins: [],
}
