// Contract test: every error response from an archive Lambda carries a stable `code`, and every
// code is mapped to a string resource on the Android client (ArchiveErrorMapper.CODES), so users
// see the error in their own language instead of the English `error` text.
// Run: npm test   (uses node:test, no dependencies)
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const lambdaDir = path.join(__dirname, '..', 'lambdas');
const mapperPath = path.join(__dirname, '..', '..', 'android', 'app', 'src', 'main', 'java',
  'com', 'kreativekoala', 'cleanup', 'ui', 'archive', 'ArchiveErrorMapper.kt');

const files = fs.readdirSync(lambdaDir).filter((f) => f.endsWith('.js'));

function errorResponses(src) {
  // response(<4xx|5xx>, { error: '...' ... }) blocks, up to the closing "})"
  const out = [];
  const re = /response\(\s*([45]\d\d)\s*,\s*\{([^}]*)\}/g;
  let m;
  while ((m = re.exec(src))) out.push({ status: Number(m[1]), body: m[2] });
  return out;
}

test('every error response has an error message and a stable UPPER_SNAKE code', () => {
  let seen = 0;
  for (const f of files) {
    const src = fs.readFileSync(path.join(lambdaDir, f), 'utf8');
    for (const r of errorResponses(src)) {
      seen++;
      assert.match(r.body, /error:\s*'[^']+'/, `${f}: ${r.status} response lost its error text`);
      assert.match(r.body, /code:\s*'[A-Z][A-Z_]+'/, `${f}: ${r.status} response has no code`);
    }
  }
  assert.ok(seen >= 20, `expected to find the error responses, found ${seen}`);
});

test('every code a Lambda can return is mapped by the Android client', () => {
  const kt = fs.readFileSync(mapperPath, 'utf8');
  const mapped = new Set([...kt.matchAll(/"([A-Z][A-Z_]+)"\s+to\s+R\.string\./g)].map((m) => m[1]));
  const used = new Set();
  for (const f of files) {
    const src = fs.readFileSync(path.join(lambdaDir, f), 'utf8');
    for (const r of errorResponses(src)) {
      const c = /code:\s*'([A-Z_]+)'/.exec(r.body);
      if (c) used.add(c[1]);
    }
  }
  for (const c of used) assert.ok(mapped.has(c), `code ${c} has no string resource mapping in ArchiveErrorMapper.kt`);
  for (const c of mapped) assert.ok(used.has(c), `ArchiveErrorMapper maps ${c} but no Lambda returns it`);
});
