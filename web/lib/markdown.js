/**
 * Petit convertisseur Markdown → HTML, écrit à la main (aucune bibliothèque).
 * Couvre ce dont la réponse du LLM a besoin : titres, listes, gras/italique,
 * tableaux et paragraphes. Échappe systématiquement le HTML pour éviter
 * qu'une réponse ne puisse injecter du script dans la page.
 */
(function () {
  'use strict';
  const root = self;
  root.HA = root.HA || {};

  function escapeHtml(text) {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  function inline(text) {
    let out = escapeHtml(text);
    out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    out = out.replace(/__([^_]+)__/g, '<strong>$1</strong>');
    out = out.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    out = out.replace(/`([^`]+)`/g, '<code>$1</code>');
    return out;
  }

  const HEADING_RE = /^(#{1,4})\s+(.*)$/;
  const BULLET_RE = /^\s*[-*]\s+(.*)$/;
  const NUMBERED_RE = /^\s*\d+[.)]\s+(.*)$/;
  const TABLE_ROW_RE = /^\s*\|.*\|\s*$/;
  const TABLE_SEPARATOR_RE = /^:?-{1,}:?$/;

  function renderTable(rawLines) {
    const rows = rawLines.map((line) => line.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim()));
    let header = null;
    let body = rows;
    if (rows.length > 1 && rows[1].every((c) => TABLE_SEPARATOR_RE.test(c))) {
      header = rows[0];
      body = rows.slice(2);
    }
    let html = '<table>';
    if (header) {
      html += `<thead><tr>${header.map((c) => `<th>${inline(c)}</th>`).join('')}</tr></thead>`;
    }
    html += `<tbody>${body.map((r) => `<tr>${r.map((c) => `<td>${inline(c)}</td>`).join('')}</tr>`).join('')}</tbody>`;
    return `${html}</table>`;
  }

  function toHtml(markdown) {
    const lines = String(markdown || '').replace(/\r\n/g, '\n').split('\n');
    const blocks = [];
    let i = 0;

    while (i < lines.length) {
      const line = lines[i];

      const heading = HEADING_RE.exec(line);
      if (heading) {
        const level = heading[1].length;
        blocks.push(`<h${level}>${inline(heading[2])}</h${level}>`);
        i += 1;
        continue;
      }

      if (BULLET_RE.test(line)) {
        const items = [];
        while (i < lines.length && BULLET_RE.test(lines[i])) {
          items.push(`<li>${inline(BULLET_RE.exec(lines[i])[1])}</li>`);
          i += 1;
        }
        blocks.push(`<ul>${items.join('')}</ul>`);
        continue;
      }

      if (NUMBERED_RE.test(line)) {
        const items = [];
        while (i < lines.length && NUMBERED_RE.test(lines[i])) {
          items.push(`<li>${inline(NUMBERED_RE.exec(lines[i])[1])}</li>`);
          i += 1;
        }
        blocks.push(`<ol>${items.join('')}</ol>`);
        continue;
      }

      if (TABLE_ROW_RE.test(line)) {
        const tableLines = [];
        while (i < lines.length && TABLE_ROW_RE.test(lines[i])) {
          tableLines.push(lines[i]);
          i += 1;
        }
        blocks.push(renderTable(tableLines));
        continue;
      }

      if (line.trim() === '') {
        i += 1;
        continue;
      }

      const paragraph = [line];
      i += 1;
      while (
        i < lines.length && lines[i].trim() !== '' &&
        !HEADING_RE.test(lines[i]) && !BULLET_RE.test(lines[i]) &&
        !NUMBERED_RE.test(lines[i]) && !TABLE_ROW_RE.test(lines[i])
      ) {
        paragraph.push(lines[i]);
        i += 1;
      }
      blocks.push(`<p>${paragraph.map(inline).join('<br>')}</p>`);
    }

    return blocks.join('\n');
  }

  root.HA.markdown = { toHtml };
})();
