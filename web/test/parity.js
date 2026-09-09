#!/usr/bin/env node
/**
 * Harnais de parité web : construit un `ReportModel` à partir du jeu d'essai partagé
 * et l'écrit en JSON, pour comparaison avec la sortie du même calcul en Kotlin.
 *
 * Script Node autonome, sans navigateur : les modules web sont des scripts classiques
 * (IIFE attachées à `self`, pas de modules CommonJS/ES). On les charge dans un bac à
 * sable `vm`, avec un `self` qui pointe sur lui-même, exactement comme dans un
 * navigateur (`self === globalThis`).
 *
 * Usage : `node web/test/parity.js` (code de sortie non nul si une assertion échoue).
 */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const WEB_DIR = path.join(__dirname, '..');
const ROOT_DIR = path.join(WEB_DIR, '..');
const FIXTURE_PATH = path.join(ROOT_DIR, 'shared-fixtures', 'parity-input.json');
const OUTPUT_DIR = path.join(__dirname, 'out');
const OUTPUT_PATH = path.join(OUTPUT_DIR, 'web.json');

// Ordre de chargement fixé par le chef d'équipe : `report-model.js` puis `stats.js`
// puis `correlation.js` puis `csv.js` puis `aggregate.js`, puis les huit constructeurs
// de section (ordre libre entre eux, chacun ne s'exécute qu'au moment de l'appel),
// puis l'orchestrateur.
const MODULE_FILES = [
  'lib/report-model.js',
  'lib/stats.js',
  'lib/correlation.js',
  'lib/csv.js',
  'lib/aggregate.js',
  'lib/report-sections/util.js',
  'lib/report-sections/sleep.js',
  'lib/report-sections/heart.js',
  'lib/report-sections/activity.js',
  'lib/report-sections/body.js',
  'lib/report-sections/stress.js',
  'lib/report-sections/breathing.js',
  'lib/report-sections/tiles.js',
  'lib/report-builder.js',
];

/** Les 19 magasins IndexedDB attendus par `HA.reportBuilder.buildReport` (voir report-builder.js). */
const STORE_KEYS = [
  'sleepNights', 'sleepStages', 'heartRate', 'stress', 'stressAlerts', 'hrv',
  'spo2', 'exercise', 'bodyComposition', 'dailySteps', 'dailyActivity',
  'energyScores', 'bloodPressure', 'ecg', 'snoring', 'respiratory',
  'skinTemp', 'sleepApnea', 'dailyFloors',
];

function loadHA() {
  const sandbox = {};
  sandbox.self = sandbox;
  sandbox.console = console;
  vm.createContext(sandbox);

  for (const relativePath of MODULE_FILES) {
    const code = fs.readFileSync(path.join(WEB_DIR, relativePath), 'utf8');
    vm.runInContext(code, sandbox, { filename: relativePath });
  }

  if (!sandbox.HA || !sandbox.HA.reportBuilder) {
    throw new Error("HA.reportBuilder n'est pas défini après le chargement des modules.");
  }
  return sandbox.HA;
}

/**
 * Le jeu d'essai sert à la fois de `sources` (les 19 magasins) et d'`options`
 * (`from`, `to`, `zone`, `zoneOffsetMinutes`, `sleepTargetHours`, `profile`) — voir
 * `shared-fixtures/README.md`.
 */
function splitFixture(fixture) {
  const sources = {};
  STORE_KEYS.forEach((key) => { sources[key] = fixture[key] || []; });
  const options = {
    from: fixture.from,
    to: fixture.to,
    zone: fixture.zone,
    zoneOffsetMinutes: fixture.zoneOffsetMinutes,
    sleepTargetHours: fixture.sleepTargetHours,
    profile: fixture.profile,
  };
  return { sources, options };
}

const failures = [];

function check(condition, message) {
  if (!condition) failures.push(message);
}

function main() {
  const HA = loadHA();
  const fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8'));
  const { sources, options } = splitFixture(fixture);

  const model = HA.reportBuilder.buildReport(sources, options);

  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(model, null, 2));
  console.log(`Modèle écrit dans ${path.relative(ROOT_DIR, OUTPUT_PATH)}`);

  const problems = HA.reportModel.validateReportModel(model);
  check(problems.length === 0, `le modèle doit être conforme au contrat : ${problems.join('; ')}`);

  check(
    model.sleep.nightly.length === 166,
    `166 nuits attendues après fusion des 202 sessions fractionnées, obtenu ${model.sleep.nightly.length}`
  );
  check(model.tiles.length === 8, `8 tuiles attendues, obtenu ${model.tiles.length}`);
  check(model.correlations.length === 6, `6 corrélations attendues, obtenu ${model.correlations.length}`);

  const nightsWithoutBedRel = model.sleep.nightly.filter((n) => n.bedRel === null || n.bedRel === undefined);
  check(
    nightsWithoutBedRel.length === 0,
    `bedRel doit être non nul sur les 166 nuits (moitié du jeu d'essai sans localBedTime) — ` +
      `${nightsWithoutBedRel.length} nuit(s) sans bedRel : ${nightsWithoutBedRel.map((n) => n.date).join(', ')}`
  );

  if (failures.length > 0) {
    console.error(`\n${failures.length} vérification(s) échouée(s) :`);
    failures.forEach((f) => console.error(`  - ${f}`));
    process.exit(1);
  }

  console.log(`OK — ${model.sleep.nightly.length} nuits, ${model.tiles.length} tuiles, ${model.correlations.length} corrélations, bedRel toujours défini.`);
}

main();
