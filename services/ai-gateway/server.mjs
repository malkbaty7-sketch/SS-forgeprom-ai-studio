import { createGateway } from './gateway-http.mjs';
import { createAccess, consume } from './access.mjs';
import { createWebSearch } from './web-search.mjs';

const { GATEWAY_TOKEN, GATEWAY_ACCOUNTS, DATABASE_URL, OPENAI_API_KEY, OPENAI_MODEL, PORT = '8080' } = process.env;
if (!OPENAI_API_KEY || !OPENAI_MODEL) throw new Error('Configure OPENAI_API_KEY and OPENAI_MODEL');
const authenticate = createAccess(GATEWAY_ACCOUNTS, GATEWAY_TOKEN);
const takeQuota = DATABASE_URL
  ? (await import('./shared-quota.mjs')).createSharedQuota(DATABASE_URL)
  : consume;
const jobs = DATABASE_URL && process.env.ENABLE_JOBS === '1'
  ? (await import('./jobs.mjs')).createJobs(DATABASE_URL) : null;
const usageModule = DATABASE_URL && process.env.ENABLE_USAGE === '1' ? await import('./usage.mjs') : null;
const usage = usageModule?.createUsage(DATABASE_URL);
const storyModule = DATABASE_URL && process.env.ENABLE_STORY_INDEX === '1' ? await import('./story.mjs') : null;
const story = storyModule?.createStory(DATABASE_URL, OPENAI_API_KEY);
const billingModule = process.env.ENABLE_BILLING === '1' ? await import('./billing.mjs') : null;
if (billingModule && !DATABASE_URL) throw new Error('Billing requires DATABASE_URL');
const billing = billingModule?.createBilling(DATABASE_URL, process.env.STRIPE_SECRET_KEY, process.env.STRIPE_CUSTOMERS);
const webSearch = process.env.ENABLE_WEB_SEARCH === '1' ? createWebSearch(process.env.BRAVE_SEARCH_API_KEY) : null;
createGateway({ authenticate, takeQuota, jobs, usage, story, storyModule, billing, webSearch,
  openAiModel: OPENAI_MODEL }).listen(Number(PORT), '0.0.0.0');
