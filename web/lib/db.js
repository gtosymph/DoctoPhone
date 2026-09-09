/**
 * Persistance locale dans IndexedDB — schéma v2.
 *
 * La version 1 ne gardait que les `DailySnapshot` (un agrégat compact par jour)
 * dans `dailySnapshots`, plus un magasin `meta`. Le rapport enrichi a besoin de
 * mesures brutes pour au moins quatre graphiques (profil horaire du stress,
 * distribution des durées de nuit, stades de sommeil par mois, corrélations sur
 * jours appariés) : la version 2 ajoute un magasin par type de mesure Samsung
 * reconnu, sans toucher aux données déjà présentes de la version 1.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const DB_NAME = 'health-analyzer';
  const DB_VERSION = 2;
  const STORE_DAYS = 'dailySnapshots';
  const STORE_META = 'meta';
  const TIME_INDEX = 'byTime';
  // Au-delà, une transaction unique bloquerait le fil : la série HRV compte
  // à elle seule environ 144 000 fenêtres sur un historique de plusieurs années.
  const BATCH_SIZE = 500;

  /**
   * Magasins de mesures ponctuelles (clé `id`), et le champ epoch qu'ils indexent.
   * `sleepNights` n'a ni `time` ni `start` dans son modèle : `bedTime` en tient
   * lieu, la nuit étant rattachée à l'instant du coucher.
   */
  const PUNCTUAL_STORES = {
    sleepNights: 'bedTime',
    sleepStages: 'start',
    heartRate: 'time',
    stress: 'start',
    stressAlerts: 'start',
    hrv: 'time',
    spo2: 'time',
    exercise: 'start',
    bodyComposition: 'time',
    bloodPressure: 'time',
    ecg: 'time',
    snoring: 'start',
    respiratory: 'time',
    skinTemp: 'time',
    sleepApnea: 'time',
  };

  /** Magasins de séries journalières (clé `date`) : un objet par jour, déjà daté par son mappeur. */
  const DAILY_STORES = ['dailySteps', 'dailyActivity', 'energyScores', 'dailyFloors'];

  function open() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE_DAYS)) {
          db.createObjectStore(STORE_DAYS, { keyPath: 'date' });
        }
        if (!db.objectStoreNames.contains(STORE_META)) {
          db.createObjectStore(STORE_META, { keyPath: 'key' });
        }
        Object.keys(PUNCTUAL_STORES).forEach((name) => {
          if (!db.objectStoreNames.contains(name)) {
            const store = db.createObjectStore(name, { keyPath: 'id' });
            store.createIndex(TIME_INDEX, PUNCTUAL_STORES[name]);
          }
        });
        DAILY_STORES.forEach((name) => {
          if (!db.objectStoreNames.contains(name)) {
            db.createObjectStore(name, { keyPath: 'date' });
          }
        });
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  function tx(db, storeNames, mode) {
    return db.transaction(storeNames, mode);
  }

  function requestToPromise(request) {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  function txDone(transaction) {
    return new Promise((resolve, reject) => {
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
  }

  /** Remplace la totalité des agrégats journaliers par ceux fournis (import complet). */
  async function saveDailySnapshots(days, summary) {
    const db = await open();
    const transaction = tx(db, [STORE_DAYS, STORE_META], 'readwrite');
    const dayStore = transaction.objectStore(STORE_DAYS);
    dayStore.clear();
    days.forEach((day) => dayStore.put(day));
    if (summary) {
      transaction.objectStore(STORE_META).put({ key: 'importSummary', value: summary });
    }
    await txDone(transaction);
    db.close();
  }

  async function loadAllDailySnapshots() {
    const db = await open();
    const transaction = tx(db, [STORE_DAYS], 'readonly');
    const rows = await requestToPromise(transaction.objectStore(STORE_DAYS).getAll());
    db.close();
    return rows.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  }

  async function loadImportSummary() {
    const db = await open();
    const transaction = tx(db, [STORE_META], 'readonly');
    const row = await requestToPromise(transaction.objectStore(STORE_META).get('importSummary'));
    db.close();
    return row ? row.value : null;
  }

  /**
   * Écrit un magasin de mesures brutes par lots d'environ [BATCH_SIZE] objets,
   * chacun dans sa propre transaction : une seule transaction pour ~144 000
   * fenêtres HRV gèlerait le navigateur pendant tout l'import.
   */
  async function putAll(storeName, items) {
    if (!items || items.length === 0) return;
    const db = await open();
    for (let offset = 0; offset < items.length; offset += BATCH_SIZE) {
      const batch = items.slice(offset, offset + BATCH_SIZE);
      const transaction = tx(db, [storeName], 'readwrite');
      const store = transaction.objectStore(storeName);
      batch.forEach((item) => store.put(item));
      await txDone(transaction);
    }
    db.close();
  }

  /** Lit la totalité d'un magasin. */
  async function readAll(storeName) {
    const db = await open();
    const transaction = tx(db, [storeName], 'readonly');
    const rows = await requestToPromise(transaction.objectStore(storeName).getAll());
    db.close();
    return rows;
  }

  /** Lit un magasin de mesures ponctuelles par plage epoch (millisecondes), via son index temporel. */
  async function readRange(storeName, fromMillis, toMillis) {
    if (!(storeName in PUNCTUAL_STORES)) {
      throw new Error(`« ${storeName} » n'a pas d'index temporel (magasin à clé date ? voir readDateRange).`);
    }
    const db = await open();
    const transaction = tx(db, [storeName], 'readonly');
    const index = transaction.objectStore(storeName).index(TIME_INDEX);
    const rows = await requestToPromise(index.getAll(IDBKeyRange.bound(fromMillis, toMillis)));
    db.close();
    return rows;
  }

  /** Lit un magasin de séries journalières par plage de dates `AAAA-MM-JJ` (bornes incluses). */
  async function readDateRange(storeName, fromIso, toIso) {
    if (!DAILY_STORES.includes(storeName)) {
      throw new Error(`« ${storeName} » n'est pas un magasin à clé date (voir readRange).`);
    }
    const db = await open();
    const transaction = tx(db, [storeName], 'readonly');
    const rows = await requestToPromise(transaction.objectStore(storeName).getAll(IDBKeyRange.bound(fromIso, toIso)));
    db.close();
    return rows;
  }

  /** Efface toutes les données locales : agrégats journaliers, résumé d'import et toutes les mesures brutes. */
  async function clearAll() {
    const db = await open();
    const storeNames = [STORE_DAYS, STORE_META, ...Object.keys(PUNCTUAL_STORES), ...DAILY_STORES];
    const transaction = tx(db, storeNames, 'readwrite');
    storeNames.forEach((name) => transaction.objectStore(name).clear());
    await txDone(transaction);
    db.close();
  }

  /** Nombre d'objets par magasin de mesures brutes, pour le bilan d'import. */
  async function counts() {
    const db = await open();
    const storeNames = [...Object.keys(PUNCTUAL_STORES), ...DAILY_STORES];
    const transaction = tx(db, storeNames, 'readonly');
    const entries = await Promise.all(
      storeNames.map((name) =>
        requestToPromise(transaction.objectStore(name).count()).then((count) => [name, count])
      )
    );
    db.close();
    return Object.fromEntries(entries);
  }

  root.HA.db = {
    saveDailySnapshots, loadAllDailySnapshots, loadImportSummary,
    putAll, readAll, readRange, readDateRange, clearAll, counts,
  };
})();
