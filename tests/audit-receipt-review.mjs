import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import crypto from 'node:crypto';

const root = process.cwd();
const report = path.join(root, 'tests/reports/victory-validation');
const options = Object.fromEntries(process.argv.slice(2).map(value => {
  const separator = value.indexOf('=');
  if (separator < 0) throw new Error('Use --receipt-dir=path and --out-dir=path.');
  return [value.slice(0, separator), value.slice(separator + 1)];
}));
const out = path.resolve(root, options['--out-dir'] ?? path.join(report, 'receipt-review-audit'));
const receipt = path.resolve(root, options['--receipt-dir'] ?? path.join(out, 'inputs'));
for (const destination of [out, receipt]) {
  if (!destination.startsWith(report + path.sep)) throw new Error('Receipt audit path must stay within victory-validation reports.');
}
fs.mkdirSync(out, {recursive:true});
const json = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, ''));
const hash = p => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex').toUpperCase();
const nativeEvidencePath = path.join(report, 'receipt-review-audit/native-source-evidence.json');
if (path.join(out, 'native-source-evidence.json') !== nativeEvidencePath) {
  fs.copyFileSync(nativeEvidencePath, path.join(out, 'native-source-evidence.json'));
}
const nativeEvidence = json(nativeEvidencePath);
if(!nativeEvidence.damageTag.values.includes('twilightforest:ghast_tear') ||
   !nativeEvidence.sourceExcerpt.includes('*= 0.75F') || !nativeEvidence.sourceExcerpt.includes('setNewDamage')) {
  throw new Error('Native helmet source evidence differs from the reviewed mechanism.');
}
const reviews = json(path.join(receipt, 'review-items.json'));
const receiptManifest = json(path.join(receipt, 'receipt-manifest.json'));
const selectedAttempts = new Map(receiptManifest.sources.map(x=>[x.attempt,x]));
const urReviews = reviews.filter(x => x.check === 'processed_minus_reductions');
const barReviews = reviews.filter(x => x.check === 'received_bar_not_finite_or_out_of_range');
if (reviews.length !== 32 || urReviews.length !== 24 || barReviews.length !== 8) throw new Error('Review input changed. Reassess scope.');
const urAttempt = '2026-11-05T10-50-20.914724500Z/01-twilightforest_ur_ghast-netherite_max';
if (urReviews.some(x => x.attempt !== urAttempt)) throw new Error('Unexpected damage review source.');
const urBase = path.join(root, 'tests/results/full-fights', urAttempt);
const primary = fs.readFileSync(urBase+'.jsonl', 'utf8').trim().split(/\r?\n/).map(JSON.parse);
const events = fs.readFileSync(urBase+'.telemetry.jsonl', 'utf8').trim().split(/\r?\n/).map(JSON.parse);
const snapshots = primary.filter(x => x.inventory);
const corrections = urReviews.map(review => {
  const id = Number(review.context.match(/attemptId=(\d+)/)[1]);
  const incoming = events.find(x => x.event === 'damage_incoming_processed' && x.attemptId === id);
  const post = events.find(x => x.event === 'damage_post' && x.attemptId === id);
  const gear = snapshots.filter(x => x.tick <= review.tick).at(-1);
  const helmet = gear?.inventory.find(x => x.slot === 39 && x.count > 0);
  if (!incoming || !post || !helmet || post.damageType !== 'twilightforest:ghast_tear') throw new Error('Helmet evidence unavailable.');
  const reductions = Object.values(post.reductionsHp).reduce((a,b)=>a+b,0);
  const helmetStep = incoming.amountAtObserver * 0.25;
  const remainder = incoming.amountAtObserver - helmetStep - post.blockedHp - reductions - post.damageHp;
  if (Math.abs(remainder) > 0.000001) throw new Error('Native helmet step does not reconcile receipt.');
  return {attempt:urAttempt, tick:review.tick, attemptId:id, damageType:post.damageType,
    equippedHelmet:helmet.item, gearSnapshotTick:gear.tick, processedIncomingHp:incoming.amountAtObserver,
    nativeHelmetMultiplier:0.75, nativeHelmetStepHp:helmetStep, namedReductionsHp:post.reductionsHp,
    healthDamageHp:post.damageHp, absorptionSpentHp:post.reductionsHp.ABSORPTION,
    rawStageResidualHp:review.residual_hp, correctedStageResidualHp:remainder,
    healthDeltaResidualHp:post.hpAfter-Math.max(0,post.hpBefore-post.damageHp),
    absorptionDeltaResidualHp:post.absorptionAfter-Math.max(0,post.absorptionBefore-post.reductionsHp.ABSORPTION)};
});

const provenance = json(path.join(report, 'runtime-provenance.json'));
const byQueue = new Map();
for (const r of [...provenance.qualifyingNativeWins, ...provenance.supersededExecutions, provenance.currentExecution]) if(r?.queue) byQueue.set(r.queue,r);
const results = path.join(root,'tests/results/full-fights');
const intervals = [];
for (const dir of fs.readdirSync(results, {withFileTypes:true}).filter(x=>x.isDirectory() && x.name >= '2026-11-05T04-36')) {
  for (const file of fs.readdirSync(path.join(results,dir.name)).filter(x=>x.endsWith('.telemetry.coverage.json'))) {
    const base = file.replace('.telemetry.coverage.json','');
    const attempt=dir.name+'/'+base, source=selectedAttempts.get(attempt);
    if(!source) continue;
    const ordinaryPath=path.join(results,dir.name,base+'.jsonl');
    const telemetryPath=path.join(results,dir.name,base+'.telemetry.jsonl');
    if(hash(ordinaryPath)!==source.primary_sha256 || hash(telemetryPath)!==source.telemetry_sha256) throw new Error('Receipt source identity changed.');
    const rows=fs.readFileSync(ordinaryPath,'utf8').trim().split(/\r?\n/).map(JSON.parse);
    const start=rows.find(x=>x.record==='started'); const end=rows.at(-1);
    if (!start || end.record!=='finished') throw new Error('Closed-case catalogue lacks completed ordinary record.');
    intervals.push({attempt, start:Date.parse(start.timestamp), end:Date.parse(end.timestamp),
      productionSha256:byQueue.get(dir.name)?.productionSha256 ?? null, scope:'completed_case', values:0, samples:0, invalid:[]});
  }
}
const partialBase='2026-11-05T13-04-36.346328200Z/05-cataclysm_the_leviathan-netherite_max';
if(intervals.length!==receiptManifest.completedAttempts) throw new Error('Frozen receipt source selection is incomplete.');
const partialRows=fs.readFileSync(path.join(root,'tests/evidence/leviathan-stall-2026-11-05T13-32-09Z/05-cataclysm_the_leviathan-netherite_max.jsonl'),'utf8').trim().split(/\r?\n/).map(JSON.parse);
const partialStart=partialRows.find(x=>x.record==='started'), partialEnd=partialRows.at(-1);
if(partialEnd.record==='finished') throw new Error('Partial-case scope changed.');
intervals.push({attempt:partialBase,start:Date.parse(partialStart.timestamp),end:Date.parse(partialEnd.timestamp),
  productionSha256:'53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77',scope:'unfinished_pre_stall',values:0,samples:0,invalid:[]});
intervals.sort((a,b)=>a.start-b.start);
const lastEnd=Math.max(...intervals.map(x=>x.end));
let lineNumber=0, missingUtc=0, invalidUtc=0, malformedJson=0, firstDated=null, lastUndatedLine=0;
const errors=[];
const clientSnapshot=path.join(root,'tests/evidence/shared-reader-snapshots/client-fight-events-armor.jsonl');
if(!fs.existsSync(clientSnapshot)) throw new Error('Run tests/snapshot-client-evidence.ps1 to create the closed shared-copy snapshot.');
const reader=readline.createInterface({input:fs.createReadStream(clientSnapshot),crlfDelay:Infinity});
for await (const line of reader) {
  lineNumber++;
  if (!line.includes('"event":"sample"') || !line.includes('"receivedBossBars"')) continue;
  let sample; try {sample=JSON.parse(line);} catch {malformedJson++;errors.push({line:lineNumber,kind:'invalid_json'});continue;}
  if (!sample.utc) {missingUtc++;lastUndatedLine=lineNumber;continue;}
  const when=Date.parse(sample.utc);
  if(!Number.isFinite(when)){invalidUtc++;errors.push({line:lineNumber,kind:'invalid_utc',utc:sample.utc});continue;}
  if (!firstDated) firstDated={line:lineNumber,utc:sample.utc};
  if(when>lastEnd) break;
  const interval=intervals.find(x=>when>=x.start && when<=x.end);
  if(!interval) continue;
  interval.samples++;
  for(const bar of sample.receivedBossBars){interval.values++;if(!Number.isFinite(bar.progress)||bar.progress<0||bar.progress>1) interval.invalid.push({utc:sample.utc,clientTick:sample.clientTick,name:bar.name,progress:bar.progress});}
}
const production='53C2399B5040778E76EDE113793AAC6C263A6EEB5779AD52FA80F4D4B0F37C77';
const current=intervals.filter(x=>x.productionSha256===production);
const currentClosed=current.filter(x=>x.scope==='completed_case');
const partial=current.find(x=>x.scope==='unfinished_pre_stall');
const historicalInvalid=intervals.filter(x=>x.invalid.length);
if(historicalInvalid.reduce((s,x)=>s+x.invalid.length,0)!==8 || current.some(x=>x.invalid.length)) throw new Error('Bar findings changed. Investigate current values.');
const audit={generatedUtc:new Date().toISOString(),reviewInputSha256:hash(path.join(receipt,'review-items.json')),
  receiptManifestSha256:hash(path.join(receipt,'receipt-manifest.json')),sourceQualification:'native-source-evidence.json',
  clientSnapshotSha256:hash(clientSnapshot),clientReadAccess:'Closed snapshot created using FileShare.ReadWrite | FileShare.Delete',
  sourceQualificationSha256:hash(path.join(out,'native-source-evidence.json')),
  originalReviewItems:reviews.length,helmetExplainedItems:corrections.length,
  maxCorrectedStageResidualHp:Math.max(...corrections.map(x=>Math.abs(x.correctedStageResidualHp))),
  helmetQualification:'The damages_helmet tag and equipped head slot trigger the native 0.75 step before armor and effects. That step changes container newDamage without a named reduction. Raw review items remain preserved.',
  historicalBarDefects:barReviews.length,remainingUnexplainedDamageReviewItems:0,
  clientJsonParseErrors:malformedJson,clientInvalidUtcRows:invalidUtc,earlyClientSamplesWithoutUtc:missingUtc,lastUndatedSampleLine:lastUndatedLine,firstDatedSample:firstDated,
  undatedRowsPrecedeFirstDatedSample:lastUndatedLine<firstDated.line,
  currentProductionCompletedCases:currentClosed.length,currentProductionCompletedBarValues:currentClosed.reduce((s,x)=>s+x.values,0),
  currentProductionCompletedInvalidBarValues:currentClosed.reduce((s,x)=>s+x.invalid.length,0),
  unfinishedLeviathanPreStallSamples:partial.samples,unfinishedLeviathanPreStallBarValues:partial.values,unfinishedLeviathanPreStallInvalidBarValues:partial.invalid.length,
  scope:'Read-only independent adjudication. Finite bar observations establish the sampled values only. They do not certify every phase formula, visibility interval, future run or full encounter completion.',
  corrections,barIntervals:intervals,parseErrors:errors};
fs.writeFileSync(path.join(out,'independent-audit.json'),JSON.stringify(audit,null,2)+'\n');
console.log(JSON.stringify(Object.fromEntries(Object.entries(audit).filter(([k])=>!['corrections','barIntervals','parseErrors'].includes(k)))));
