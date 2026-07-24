import starlight from '@astrojs/starlight';
import { defineConfig } from 'astro/config';
import { readFileSync } from 'node:fs';

import { sidebar } from './src/sidebar.mjs';

function getBasePath() {
  if (process.env.DOCS_BASE) return process.env.DOCS_BASE;
  if (process.env.READTHEDOCS !== 'True') return undefined;

  const language = process.env.READTHEDOCS_LANGUAGE || 'en';
  const version = process.env.READTHEDOCS_VERSION || 'latest';
  return `/${language}/${version}`;
}

const base = getBasePath();
const basePrefix = base ? base.replace(/\/$/, '') : '';
const legacyRedirectRoutes = JSON.parse(
  readFileSync(new URL('./_build/starlight-legacy-routes.json', import.meta.url), 'utf8'),
);
const legacyRedirects = Object.fromEntries(
  Object.entries(legacyRedirectRoutes).map(([from, to]) => [from, `${basePrefix}${to}`]),
);

export default defineConfig({
  site: process.env.DOCS_SITE_URL || 'https://chipyard.readthedocs.io',
  base,
  redirects: legacyRedirects,
  integrations: [
    starlight({
      title: 'Chipyard',
      description: 'Chipyard documentation',
      logo: {
        dark: './_static/images/chipyard-logo-dark.svg',
        light: './_static/images/chipyard-logo.svg',
        alt: 'Chipyard',
        replacesTitle: true,
      },
      components: {
        Sidebar: './src/components/Sidebar.astro',
      },
      customCss: ['./src/styles/sphinx.css'],
      expressiveCode: {
        shiki: {
          // Preserve Sphinx's lexer identifiers in generated fences, and teach
          // Shiki only about identifiers without a directly matching grammar.
          langAlias: {
            C: 'c',
            Verilog: 'verilog',
            default: 'text',
            dts: 'c',
            kconfig: 'shell',
            none: 'text',
            shell: 'bash',
          },
        },
      },
      sidebar,
      tableOfContents: {
        minHeadingLevel: 2,
        maxHeadingLevel: 4,
      },
      head: [
        {
          tag: 'meta',
          attrs: {
            name: 'readthedocs-addons-api-version',
            content: '1',
          },
        },
        {
          tag: 'script',
          attrs: {
            src: `${basePrefix}/readthedocs-version-switcher.js`,
            defer: true,
          },
        },
      ],
    }),
  ],
});
