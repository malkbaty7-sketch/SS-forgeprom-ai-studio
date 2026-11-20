import { setTimeout } from 'node:timers/promises';
import { createJobs } from './jobs.mjs';
import { generate } from './provider.mjs';

const { DATABASE_URL, OPENAI_API_KEY, OPENAI_MODEL } = process.env;
if (!DATABASE_URL || !OPENAI_API_KEY || !OPENAI_MODEL) throw new Error('Configure worker database and provider');
const jobs = createJobs(DATABASE_URL);
const usageModule = process.env.ENABLE_USAGE === '1' ? await import('./usage.mjs') : null;
const usage = usageModule?.createUsage(DATABASE_URL);
const runGeneration = (messages, account) => usage
  ? usageModule.meteredGeneration(generate, usage, account, OPENAI_MODEL, messages)
  : generate(messages);
while (true) {
  try { if (!(await jobs.runOne(runGeneration))) await setTimeout(1000); }
  catch { console.error('Worker database operation failed'); await setTimeout(3000); }
}
