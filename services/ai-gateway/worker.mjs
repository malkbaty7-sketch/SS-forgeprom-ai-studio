import { setTimeout } from 'node:timers/promises';
import { createJobs } from './jobs.mjs';
import { generate } from './provider.mjs';

const { DATABASE_URL, OPENAI_API_KEY, OPENAI_MODEL } = process.env;
if (!DATABASE_URL || !OPENAI_API_KEY || !OPENAI_MODEL) throw new Error('Configure worker database and provider');
const jobs = createJobs(DATABASE_URL);
while (true) {
  try { if (!(await jobs.runOne(generate))) await setTimeout(1000); }
  catch { console.error('Worker database operation failed'); await setTimeout(3000); }
}
