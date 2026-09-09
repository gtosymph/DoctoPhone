/**
 * Web Worker : porte tout le travail lourd de l'import (lecture des fichiers,
 * parsing CSV, mappage, agrégation journalière, écriture des mesures brutes dans
 * IndexedDB) loin du fil principal, pour que l'interface reste réactive pendant
 * que l'export Samsung Health défile.
 *
 * IndexedDB est accessible depuis un Worker : persister ici évite de faire
 * transiter par `postMessage` les tableaux les plus volumineux (jusqu'à environ
 * 144 000 fenêtres HRV) vers le fil principal pour rien.
 */
importScripts(
  'lib/csv.js', 'lib/mappers.js', 'lib/mappers-clinical.js', 'lib/hrv.js',
  'lib/db.js', 'lib/aggregate.js', 'lib/importer.js'
);

/** Fabrique la liste d'entrées à partir d'un `FileList`/tableau de `File` (import dossier). */
function entriesFromFiles(files) {
  return files.map((file) => ({
    relativePath: file.webkitRelativePath || file.name,
    getText: () => file.text(),
  }));
}

/** Fabrique la liste d'entrées à partir d'une archive ZIP, dépaquetée avec JSZip. */
async function entriesFromZip(zipFile) {
  importScripts('https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js');
  const buffer = await zipFile.arrayBuffer();
  const zip = await JSZip.loadAsync(buffer);
  const entries = [];
  zip.forEach((relativePath, entry) => {
    if (entry.dir) return;
    entries.push({ relativePath, getText: () => entry.async('text') });
  });
  return entries;
}

/** Persiste les mesures brutes de l'import, magasin par magasin, en signalant l'avancement. */
async function persistRecords(records, onProgress) {
  await HA.db.clearAll();
  const storeNames = Object.keys(records);
  let done = 0;
  for (const name of storeNames) {
    await HA.db.putAll(name, records[name]);
    done += 1;
    if (onProgress) onProgress({ done, total: storeNames.length, phase: 'db' });
  }
}

self.onmessage = async (event) => {
  const message = event.data;
  if (message.type !== 'import') return;

  try {
    const entries = message.mode === 'zip'
      ? await entriesFromZip(message.zipFile)
      : entriesFromFiles(message.files);

    const { records, ...result } = await HA.importer.run(
      entries,
      (progress) => self.postMessage({ type: 'progress', progress }),
      message.sleepTargetMinutes
    );

    await persistRecords(records, (progress) => self.postMessage({ type: 'progress', progress }));

    // `records` reste dans le Worker : déjà persisté, il n'a pas besoin de
    // retraverser `postMessage` (potentiellement des centaines de milliers d'objets).
    self.postMessage({ type: 'result', result });
  } catch (error) {
    self.postMessage({ type: 'error', message: error && error.message ? error.message : String(error) });
  }
};
