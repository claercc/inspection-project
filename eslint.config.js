import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import vue from 'eslint-plugin-vue';
import globals from 'globals';

export default [
  {
    ignores: ['**/node_modules/**', '**/target/**', '**/dist/**', '.tools/**', '.run/**'],
  },
  js.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['web/src/**/*.{js,vue}'],
    languageOptions: { globals: globals.browser },
  },
  {
    files: ['scripts/**/*.mjs', 'tests/**/*.mjs', '*.js', 'web/vite.config.js'],
    languageOptions: { globals: globals.node },
  },
  {
    files: ['tests/**/*.mjs'],
    // 测试由 Node 运行，但 Playwright 注入的回调在浏览器执行，因此也需要浏览器全局变量。
    languageOptions: { globals: globals.browser },
  },
  {
    rules: {
      curly: ['error', 'all'],
      eqeqeq: ['error', 'always'],
      'no-unused-vars': ['error', { caughtErrorsIgnorePattern: '^_' }],
    },
  },
  // 格式统一交给 Prettier；ESLint 检查代码正确性和 Vue 用法，避免两套格式规则互相覆盖。
  prettier,
];
