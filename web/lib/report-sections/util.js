/**
 * Fonctions communes aux constructeurs de section du rapport (`report-sections/*.js`).
 *
 * Rien ici ne connaît la forme complète d'un `ReportModel` : c'est `report-builder.js`
 * qui assemble les sections en un modèle conforme au contrat de `report-model.js`.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};
  root.HA.reportSections = root.HA.reportSections || {};

  /**
   * Résout le décalage horaire (minutes) à utiliser pour une mesure : délègue à
   * `HA.aggregate.resolveOffsetMinutes`, source unique de la chaîne de repli à trois
   * niveaux (mesure, puis défaut de la période, puis fuseau du navigateur).
   */
  function resolveOffset(item, defaultOffsetMinutes) {
    return HA.aggregate.resolveOffsetMinutes(item.offsetMinutes, defaultOffsetMinutes);
  }

  /** Regroupe des mesures horodatées par jour local, via `resolveOffset`. */
  function groupByLocalDate(items, timeOf, defaultOffsetMinutes) {
    const map = {};
    items.forEach((item) => {
      const time = timeOf(item);
      if (time === null || time === undefined) return;
      const date = HA.aggregate.localDateKeyOf(time, resolveOffset(item, defaultOffsetMinutes));
      (map[date] = map[date] || []).push(item);
    });
    return map;
  }

  /** Regroupe des enregistrements déjà quotidiens (un par jour) par leur champ `date`. */
  function groupByDate(items) {
    const map = {};
    items.forEach((item) => {
      if (!item || !item.date) return;
      (map[item.date] = map[item.date] || []).push(item);
    });
    return map;
  }

  /** Heure locale (0-23) d'un instant, via `offsetMinutes` (repli sur le fuseau du navigateur). */
  function localHourOf(epochMs, offsetMinutes) {
    if (!Number.isFinite(offsetMinutes)) return new Date(epochMs).getHours();
    return new Date(epochMs + offsetMinutes * 60000).getUTCHours();
  }

  /** Restreint une liste d'enregistrements datés à l'intervalle [from, to] (inclusif, comparaison de chaînes ISO). */
  function withinPeriod(items, dateOf, fromKey, toKey) {
    if (!fromKey || !toKey) return items;
    return items.filter((item) => {
      const date = dateOf(item);
      return date !== null && date !== undefined && date >= fromKey && date <= toKey;
    });
  }

  /** Convertit une heure d'horloge décimale (0-24) en heure relative à minuit : 22.5 -> -1.5, 2.25 -> 2.25. */
  function relativeOfClockHours(hours) {
    return hours >= 12 ? hours - 24 : hours;
  }

  /** Étend un nombre de secondes depuis minuit en heure relative à minuit : -1.5 = 22h30, 2.25 = 2h15. */
  function relativeHoursOf(secondOfDay) {
    return relativeOfClockHours(secondOfDay / 3600);
  }

  /**
   * Reconstruit le décalage horaire (minutes) d'un instant absolu et de son heure locale
   * déjà connue (secondes depuis minuit) — ex. `bedTime` et `localBedSecondOfDay`/
   * `localBedTime` d'une nuit. Ce décalage n'est pas conservé tel quel sur l'objet :
   * on le retrouve par différence.
   */
  function deriveOffsetFromLocalSeconds(epochMs, localSecondOfDay) {
    if (epochMs === null || epochMs === undefined ||
        localSecondOfDay === null || localSecondOfDay === undefined) return null;
    const utcDate = new Date(epochMs);
    const utcSeconds = utcDate.getUTCHours() * 3600 + utcDate.getUTCMinutes() * 60 + utcDate.getUTCSeconds();
    let diff = localSecondOfDay - utcSeconds;
    if (diff > 43200) diff -= 86400;
    if (diff < -43200) diff += 86400;
    return diff / 60;
  }

  /**
   * Secondes depuis minuit local d'un instant absolu.
   *
   * Le résultat est en secondes entières : les millisecondes sont écartées. Elles n'ont
   * aucun sens sur une heure de coucher et elles suffisaient à faire diverger les deux
   * implémentations (voir report-model.js). Ne pas arrondir à la minute pour autant.
   * Repli sur le fuseau du navigateur si `offsetMinutes` est indéfini.
   */
  function localSecondOfDayAt(epochMs, offsetMinutes) {
    if (!Number.isFinite(offsetMinutes)) {
      const local = new Date(epochMs);
      return local.getHours() * 3600 + local.getMinutes() * 60 + local.getSeconds();
    }
    const shifted = new Date(epochMs + offsetMinutes * 60000);
    return shifted.getUTCHours() * 3600 + shifted.getUTCMinutes() * 60 + shifted.getUTCSeconds();
  }

  /** Lit une heure d'horloge `HH:MM:SS` en secondes depuis minuit. `null` si le format ne correspond pas. */
  function parseHmsToSeconds(hms) {
    if (typeof hms !== 'string') return null;
    const match = /^(\d{1,2}):(\d{2}):(\d{2})$/.exec(hms.trim());
    if (!match) return null;
    return Number(match[1]) * 3600 + Number(match[2]) * 60 + Number(match[3]);
  }

  function toDayValues(dailyMap, valueOf) {
    return Object.keys(dailyMap).sort().map((date) => ({ date, value: valueOf(dailyMap[date]) }));
  }

  function unionDates(...groups) {
    const set = new Set();
    groups.forEach((g) => Object.keys(g).forEach((d) => set.add(d)));
    return Array.from(set).sort();
  }

  function makeTile(key, label, value, unit, sub, status) {
    const safeStatus = HA.reportModel.TILE_STATUS.includes(status) ? status : 'neutral';
    return { key, label, value, unit: unit || null, sub, status: safeStatus };
  }

  const MONTHS_FR = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
  const MONTHS_FR_SHORT = ['janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin', 'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'];

  /** Date `AAAA-MM-JJ` en français lisible : « 23 juin 2025 ». */
  function formatDate(dateKey) {
    const [y, m, d] = dateKey.split('-').map(Number);
    return `${d} ${MONTHS_FR[m - 1]} ${y}`;
  }

  function formatPeriodLabel(fromKey, toKey) {
    if (!fromKey || !toKey) return '';
    const format = (key) => {
      const [y, m, d] = key.split('-').map(Number);
      return `${d} ${MONTHS_FR_SHORT[m - 1]} ${y}`;
    };
    return fromKey === toKey ? format(fromKey) : `${format(fromKey)} – ${format(toKey)}`;
  }

  root.HA.reportSections.util = {
    resolveOffset,
    groupByLocalDate,
    groupByDate,
    localHourOf,
    withinPeriod,
    relativeHoursOf,
    relativeOfClockHours,
    deriveOffsetFromLocalSeconds,
    localSecondOfDayAt,
    parseHmsToSeconds,
    toDayValues,
    unionDates,
    makeTile,
    formatDate,
    formatPeriodLabel,
  };
})();
