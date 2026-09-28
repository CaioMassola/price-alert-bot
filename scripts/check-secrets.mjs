// Local pre-publication check. Reports filenames/rules, never matched values.
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
const git = args => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 });
const staged = process.argv.includes('--staged');
const files = [...new Set(git(staged ? ['ls-files', '-z'] : ['ls-files', '-co', '--exclude-standard', '-z']).split('\0').filter(Boolean))];
const secrets = fs.existsSync('.env') ? fs.readFileSync('.env', 'utf8').split(/\r?\n/).flatMap(line => {
  const i = line.indexOf('=');
  if (i < 0 || !/(PASSWORD|SECRET|TOKEN|WEBHOOK)/.test(line.slice(0, i)) || /EXPIRES/.test(line.slice(0, i))) return [];
  const value = line.slice(i + 1).trim().replace(/^"(.*)"$/, '$1');
  return value.length >= 8 && !['change-me-locally'].includes(value) ? [value] : [];
}) : [];
const rules = [
  ['mercadolivre-token', /APP_USR-\d+-\d+-[a-zA-Z0-9]{20,}-\d+/],
  ['discord-webhook', /discord\.com\/api\/webhooks\/\d+\/[A-Za-z0-9_-]{30,}/],
  ['github-token', /(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})/],
  ['private-key', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/],
  ['aws-key', /\bAKIA[A-Z0-9]{16}\b/]
];
let findings = 0;
for (const file of files) {
  if (/(^|\/)(?:\.runtime|target|\.git)\//.test(file) || /(^|\/)\.env(?:\..+)?$/.test(file) && !file.endsWith('.env.example')) {
    console.error(`${file}: forbidden-publication-path`); findings++; continue;
  }
  const content = staged ? git(['show', ':' + file]) : fs.readFileSync(file, 'utf8');
  if (secrets.some(secret => content.includes(secret))) { console.error(`${file}: local-secret-value`); findings++; }
  for (const [name, rule] of rules) if (rule.test(content)) { console.error(`${file}: ${name}`); findings++; }
}
console.log(`Checked ${files.length} files; findings: ${findings}. Values are never printed.`);
if (findings) process.exitCode = 1;
