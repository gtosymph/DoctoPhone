/**
 * Lecteur de CSV Samsung Health, portage fidèle de `SamsungCsvReader.kt`.
 *
 * Le format n'est pas un CSV standard :
 * - la ligne 1 décrit le type de donnée, pas les colonnes ;
 * - la ligne 2 porte les noms de colonnes, précédés d'un BOM UTF-8 ;
 * - chaque ligne se termine par une virgule, qui crée une colonne vide parasite ;
 * - les champs de texte libre suivent la RFC 4180 (guillemets, virgules internes).
 *
 * Chargé en script classique (pas de modules), pour rester utilisable depuis la page,
 * le Web Worker et la page de tests sans bundler. Toutes les fonctions sont pures.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  const BOM = '\uFEFF';
  const SAMSUNG_PREFIX = 'com.samsung';
  const TIMESTAMP_RE = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})(?:\.(\d{3}))?$/;

  function stripBom(value) {
    return value.startsWith(BOM) ? value.slice(1) : value;
  }

  /** Découpe une ligne selon la RFC 4180. Les cellules vides deviennent `null`. */
  function splitRecord(record, columnCount) {
    const cells = [];
    let field = '';
    let inQuotes = false;
    let position = 0;

    while (position < record.length) {
      const character = record[position];
      if (inQuotes && character === '"' && record[position + 1] === '"') {
        field += '"';
        position += 1;
      } else if (character === '"') {
        inQuotes = !inQuotes;
      } else if (character === ',' && !inQuotes) {
        cells.push(field.length > 0 ? field : null);
        field = '';
      } else {
        field += character;
      }
      position += 1;
    }
    cells.push(field.length > 0 ? field : null);

    while (cells.length < columnCount) cells.push(null);
    return cells;
  }

  function hasBalancedQuotes(record) {
    let count = 0;
    for (let i = 0; i < record.length; i += 1) if (record[i] === '"') count += 1;
    return count % 2 === 0;
  }

  /** Une ligne de données, accessible par nom de colonne. Ne lève jamais d'exception. */
  class SamsungCsvRow {
    constructor(index, cells) {
      this._index = index;
      this._cells = cells;
    }

    string(column) {
      const position = this._index[column];
      if (position === undefined) return null;
      const value = this._cells[position];
      return value === undefined ? null : value;
    }

    double(column) {
      const raw = this.string(column);
      if (raw === null) return null;
      const value = Number(raw);
      return Number.isFinite(value) ? value : null;
    }

    float(column) {
      return this.double(column);
    }

    int(column) {
      const value = this.double(column);
      return value === null ? null : Math.trunc(value);
    }

    long(column) {
      return this.int(column);
    }

    boolean(column) {
      const raw = this.string(column);
      if (raw === null) return null;
      if (raw === '0' || raw === 'false' || raw === 'FALSE') return false;
      return true;
    }

    /** Décalage horaire de la ligne, au format Samsung `UTC+0200`. Rendu en minutes signées. */
    zoneOffsetMinutes(column) {
      const raw = this.string(column);
      if (raw === null) return null;
      if (!raw.startsWith('UTC')) return null;
      const sign = raw[3];
      if (sign !== '+' && sign !== '-') return null;
      const digits = raw.slice(4);
      if (digits.length !== 4) return null;
      const hours = Number(digits.slice(0, 2));
      const minutes = Number(digits.slice(2, 4));
      if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return null;
      const total = hours * 60 + minutes;
      return sign === '-' ? -total : total;
    }

    /** Horodatage local, sans décalage. */
    localDateTime(column) {
      const raw = this.string(column);
      if (raw === null) return null;
      const match = TIMESTAMP_RE.exec(raw);
      if (!match) return null;
      return {
        year: Number(match[1]),
        month: Number(match[2]),
        day: Number(match[3]),
        hour: Number(match[4]),
        minute: Number(match[5]),
        second: Number(match[6]),
        millis: match[7] ? Number(match[7]) : 0,
      };
    }

    /** Instant absolu (epoch ms), converti depuis l'horodatage local + le décalage. */
    instant(column, offsetColumn) {
      const offsetCol = offsetColumn || 'time_offset';
      const local = this.localDateTime(column);
      if (local === null) return null;
      const offsetMinutes = this.zoneOffsetMinutes(offsetCol) || 0;
      const utcMillis = Date.UTC(
        local.year, local.month - 1, local.day,
        local.hour, local.minute, local.second, local.millis
      );
      return utcMillis - offsetMinutes * 60000;
    }

    /** Date locale (`YYYY-MM-DD`), le jour auquel appartient la mesure. */
    localDate(column, offsetColumn) {
      const local = this.localDateTime(column);
      if (local !== null) return dateKey(local.year, local.month, local.day);
      const instant = this.instant(column, offsetColumn);
      if (instant === null) return null;
      const offsetMinutes = this.zoneOffsetMinutes(offsetColumn || 'time_offset') || 0;
      const shifted = new Date(instant + offsetMinutes * 60000);
      return dateKey(shifted.getUTCFullYear(), shifted.getUTCMonth() + 1, shifted.getUTCDate());
    }
  }

  function dateKey(year, month, day) {
    const mm = String(month).padStart(2, '0');
    const dd = String(day).padStart(2, '0');
    return `${year}-${mm}-${dd}`;
  }

  /** Lit un document CSV Samsung Health complet depuis son texte brut (UTF-8). */
  function parseSamsungCsv(text) {
    const lines = text.split(/\r\n|\n|\r/);
    let cursor = 0;

    const descriptorLine = lines[cursor];
    cursor += 1;
    if (descriptorLine === undefined) throw new Error('Le fichier est vide.');
    const descriptorParts = stripBom(descriptorLine).split(',');
    if (descriptorParts.length < 3 || !descriptorParts[0].startsWith(SAMSUNG_PREFIX)) {
      throw new Error(`Ligne de description Samsung Health invalide : "${descriptorLine}".`);
    }
    const descriptor = {
      dataType: descriptorParts[0].trim(),
      appVersion: descriptorParts[1].trim(),
      schemaVersion: descriptorParts[2].trim(),
    };

    const columnsLine = lines[cursor];
    cursor += 1;
    if (columnsLine === undefined) throw new Error('Le fichier ne contient aucune ligne de colonnes.');
    const columns = stripBom(columnsLine).split(',').map((c) => c.trim());
    while (columns.length > 0 && columns[columns.length - 1] === '') columns.pop();

    const index = {};
    columns.forEach((name, position) => { index[name] = position; });

    const rows = [];
    let pending = '';
    for (; cursor < lines.length; cursor += 1) {
      pending += lines[cursor];
      if (hasBalancedQuotes(pending)) {
        const record = pending;
        pending = '';
        if (record.trim().length > 0) {
          rows.push(new SamsungCsvRow(index, splitRecord(record, columns.length)));
        }
      } else {
        // Un champ entre guillemets contient un retour à la ligne.
        pending += '\n';
      }
    }

    return { descriptor, columns, rows };
  }

  root.HA.csv = { parseSamsungCsv, SamsungCsvRow, dateKey };
})();
