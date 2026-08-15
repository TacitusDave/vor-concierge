/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // Ramp built around the exact brand hex #004515 (pinned at 600, the shade actually
        // painted on buttons/active nav throughout the app), lightened for 400/500 (text, hover
        // states) and darkened for 700/900 (borders, subtle tints), same hue throughout.
        brand: {
          50: '#ecf9f0',
          100: '#d0f1da',
          400: '#0c9736',
          500: '#036320',
          600: '#004515',
          700: '#00330f',
          900: '#011b09',
        },
        ink: {
          950: '#05070d',
          900: '#0b1220',
          850: '#0e1729',
          800: '#131d33',
          700: '#1c2740',
        },
      },
    },
  },
  plugins: [],
}
