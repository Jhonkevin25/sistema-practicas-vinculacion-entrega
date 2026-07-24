/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}"
  ],
  theme: {
    extend: {
      colors: {
        'unibe': {
          'blue': '#002f6c',
          'blue-hover': '#00224d',
          'gold': '#ffcc00',
          'gold-hover': '#e6b800',
          'bg': '#f5f7fb'
        }
      }
    },
  },
  plugins: [],
}

