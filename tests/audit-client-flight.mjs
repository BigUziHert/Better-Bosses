import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

// Offline audit only: reads recorded client/server evidence, never a live game API.
const resultRoot = process.argv[2] ?? 'tests/results/full-fights';
const clientFile = process.argv[3] ?? 'tests/evidence/shared-reader-snapshots/client-fight-events-armor.jsonl';
if (!fs.existsSync(clientFile)) throw new Error('Create the closed client snapshot with tests/snapshot-client-evidence.ps1 first.');
const liveClientRoot=fs.realpathSync('run-client-playtest').toLowerCase()+path.sep;
if(fs.realpathSync(clientFile).toLowerCase().startsWith(liveClientRoot)) throw new Error('Live client logs require the shared snapshot helper.');
const output = process.argv[4] ?? 'tests/reports/victory-validation';
const attempts = [];
for (const queue of fs.readdirSync(resultRoot).filter(name => name >= '2026-11-05T04-36')) {
  for (const name of fs.readdirSync(path.join(resultRoot, queue)).filter(name => name.endsWith('.telemetry.coverage.json'))) {
    const coverage = JSON.parse(fs.readFileSync(path.join(resultRoot, queue, name), 'utf8'));
    if (coverage.outcome !== 'won' || !coverage.naturalDefeatObserved) continue;
    const ordinary = name.replace('.telemetry.coverage.json', '.jsonl');
    const rows = fs.readFileSync(path.join(resultRoot, queue, ordinary), 'utf8').trim().split('\n').map(JSON.parse);
    const started = rows.find(row => row.record === 'started'), finished = rows.at(-1);
    const deaths = rows.filter(row => row.record === 'boss_death_event' && row.cancelled === false);
    if (!started || finished.record !== 'finished' || finished.playerHP <= 0 || deaths.length < started.bosses.length || finished.bosses.some(boss => boss.hp > 0)) {
      throw new Error(`Native win lacks an independent death witness: ${queue}/${ordinary}`);
    }
    attempts.push({
      attempt: `${queue}/${ordinary.replace('.jsonl', '')}`, boss: coverage.boss,
      startUtc: started.timestamp, endUtc: finished.timestamp, firstNativeBossDeathUtc: deaths[0].timestamp,
      samples: [],
    });
  }
}
const clientBytes = fs.readFileSync(clientFile);
let parseErrors = 0;
for (const line of clientBytes.toString('utf8').trim().split('\n')) {
  let row;
  try { row = JSON.parse(line); } catch { parseErrors++; continue; }
  if (row.event !== 'sample') continue;
  const utc = Date.parse(row.utc);
  const attempt = attempts.find(a => a.boss === row.boss && utc >= Date.parse(a.startUtc) && utc <= Date.parse(a.endUtc));
  if (!attempt) continue;
  attempt.samples.push({
    utc: row.utc, health: row.health, bossHealth: row.bossHealth, position: row.position,
    flying: row.physics?.flying, mayFly: row.physics?.mayFly,
    passenger: row.physics?.passenger, vehicle: row.physics?.vehicle,
    onGround: row.physics?.onGround, blockBelow: row.physics?.blockBelow,
    nativeKnocked: row.nativeCombatInputState?.nativeKnocked,
    afterNativeBossDeath: utc >= Date.parse(attempt.firstNativeBossDeathUtc),
  });
}
function ranges(rows) {
  const groups = [];
  for (const row of rows) {
    const previous = groups.at(-1);
    if (!previous || Date.parse(row.utc) - Date.parse(previous.endUtc) > 2500) groups.push({startUtc: row.utc, endUtc: row.utc, samples: [row]});
    else { previous.endUtc = row.utc; previous.samples.push(row); }
  }
  return groups;
}
const summaries = attempts.map(a => {
  a.samples.sort((x, y) => Date.parse(x.utc) - Date.parse(y.utc));
  const flying = a.samples.filter(row => row.flying === true);
  const nonPassenger = flying.filter(row => row.passenger === false);
  const flagged = ranges(nonPassenger);
  const lastFlag = nonPassenger.at(-1);
  const clear = lastFlag && a.samples.find(row => Date.parse(row.utc) > Date.parse(lastFlag.utc) && row.flying === false);
  const gaps = a.samples.slice(1).map((row, i) => (Date.parse(row.utc) - Date.parse(a.samples[i].utc)) / 1000);
  return {
    attempt: a.attempt, boss: a.boss, startUtc: a.startUtc, endUtc: a.endUtc,
    firstNativeBossDeathUtc: a.firstNativeBossDeathUtc, samples: a.samples.length,
    missingPhysicsSamples: a.samples.filter(row => row.flying === undefined || row.passenger === undefined).length,
    flyingSamples: flying.length, nonPassengerFlyingSamples: nonPassenger.length,
    nonPassengerFlyingBeforeBossDeathAndNotKnockedSamples: nonPassenger.filter(row => !row.afterNativeBossDeath && row.nativeKnocked === false).length,
    largestSampleGapSeconds: gaps.length ? Math.max(...gaps) : null,
    firstClearSampleAfterLastFlagUtc: clear?.utc ?? null,
    flaggedIntervals: flagged,
    assessment: a.samples.length === 0 ? 'no_client_samples' : nonPassenger.length ? 'startup_or_combat_flight_flag_requires_qualification' : a.samples.some(row => row.flying === undefined || row.passenger === undefined) ? 'incomplete_physics_fields' : 'no_nonpassenger_flight_flag_observed',
  };
});
const report = {
  generatedUtc: new Date().toISOString(),
  scope: 'All independently witnessed maximum-kit native wins in the selected new-run directories. Matching client sample UTC intervals include combat and death/cleanup delay; player/boss IDs must agree.',
  method: 'Read-only offline client sample audit. Flying/passenger/knocked flags are reported directly. Sampling cannot exclude brief transitions between samples. Native death credit is separate from movement validity; a flight flag is not silently discarded because it lasts only a few seconds.',
  clientLog: clientFile.replaceAll('\\', '/'), clientLogSha256: crypto.createHash('sha256').update(clientBytes).digest('hex'),
  clientJsonParseErrors: parseErrors, winningAttempts: summaries.length,
  distinctBosses: new Set(summaries.map(a => a.boss)).size,
  matchedSamples: summaries.reduce((n, a) => n + a.samples, 0),
  missingPhysicsSamples: summaries.reduce((n, a) => n + a.missingPhysicsSamples, 0),
  attemptsWithNonPassengerFlying: summaries.filter(a => a.nonPassengerFlyingSamples > 0).length,
  distinctBossesWithNonPassengerFlying: new Set(summaries.filter(a => a.nonPassengerFlyingSamples > 0).map(a => a.boss)).size,
  summaries,
};
fs.mkdirSync(output, {recursive: true});
fs.writeFileSync(path.join(output, 'client-flight-audit.json'), JSON.stringify(report, null, 2) + '\n');
const columns = ['attempt', 'boss', 'samples', 'missingPhysicsSamples', 'flyingSamples', 'nonPassengerFlyingSamples', 'nonPassengerFlyingBeforeBossDeathAndNotKnockedSamples', 'firstClearSampleAfterLastFlagUtc', 'largestSampleGapSeconds', 'assessment'];
const quote = value => `"${String(value ?? '').replaceAll('"', '""')}"`;
fs.writeFileSync(path.join(output, 'client-flight-audit.csv'), [columns.join(','), ...summaries.map(row => columns.map(key => quote(row[key])).join(','))].join('\n') + '\n');
console.log(JSON.stringify({winningAttempts: report.winningAttempts, bosses: report.distinctBosses, samples: report.matchedSamples, missingPhysics: report.missingPhysicsSamples, flaggedAttempts: report.attemptsWithNonPassengerFlying, flaggedBosses: report.distinctBossesWithNonPassengerFlying, parseErrors}));
