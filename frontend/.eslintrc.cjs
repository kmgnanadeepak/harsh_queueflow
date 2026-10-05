module.exports = {
  root: true,
  env: { browser: true, es2021: true },
  extends: [
    'eslint:recommended',
    'plugin:react/recommended',
    'plugin:react/jsx-runtime',
    'plugin:react-hooks/recommended',
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    ecmaFeatures: { jsx: true },
  },
  settings: {
    react: { version: 'detect' },
  },
  plugins: ['react-refresh', 'unused-imports'],
  ignorePatterns: ['dist', 'node_modules'],
  rules: {
    // Without this, a component referenced only from JSX looks unused to
    // no-unused-vars, because ESLint sees <Foo /> as a JSXElement rather than
    // a variable read.
    'react/jsx-uses-vars': 'error',
    // Unused imports are an error and are removed by `eslint --fix`.
    'unused-imports/no-unused-imports': ['error', { varsIgnorePattern: '^_' }],
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    'react/prop-types': 'off',
    'react-refresh/only-export-components': 'off',
  },
};
