function makePlaceholderStore() {
  const values = [];
  return {
    take(value) {
      const token = "__EMBED_PLACEHOLDER_" + values.length + "__";
      values.push(value);
      return token;
    },
    restore(text) {
      return values.reduce((result, val, idx) => {
        const token = "__EMBED_PLACEHOLDER_" + idx + "__";
        const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        const indentedMatcher = new RegExp("(^|\\n)([ \\t]*)" + escaped, "g");
        const withIndented = result.replace(indentedMatcher, (_, ls, indent) =>
          ls + indent + String(val).replace(/\n/g, "\n" + indent)
        );
        return withIndented.split(token).join(val);
      }, text);
    },
  };
}

function trimEmptyLines(value) {
  return value.replace(/^\s+|\s+$/g, "");
}

function normalizeTagArgument(value) {
  return trimEmptyLines(String(value || "")).replace(/^(['"])([\s\S]*?)\1$/, "$2");
}

function parseMarkdownTable(block) {
  const lines = block.split("\n").map(l => l.trim()).filter(Boolean);
  if (lines.length < 2 || !/^\|?[\s:-]+\|[\s|:-]*$/.test(lines[1])) return null;
  function splitRow(row) { return row.replace(/^\||\|$/g, "").split("|").map(c => c.trim()); }
  return { headers: splitRow(lines[0]), rows: lines.slice(2).map(splitRow) };
}

function markdownTableToBbcode(block) {
  const table = parseMarkdownTable(block);
  if (!table) return block;
  let bbcode = "[TABLE][TR]";
  table.headers.forEach(h => { bbcode += "[TH]" + h + "[/TH]"; });
  bbcode += "[/TR]";
  table.rows.forEach(row => {
    bbcode += "[TR]";
    row.forEach(c => { bbcode += "[TD]" + c + "[/TD]"; });
    bbcode += "[/TR]";
  });
  bbcode += "[/TABLE]";
  return bbcode;
}

function markdownListsToBbcode(text) {
  return text.split(/\n{2,}/).map(block => {
    const lines = block.split("\n");
    if (lines.every(l => /^\s*[-*+]\s+/.test(l)))
      return "[LIST]\n" + lines.map(l => "[*]" + l.replace(/^\s*[-*+]\s+/, "")).join("\n") + "\n[/LIST]";
    if (lines.every(l => /^\s*\d+\.\s+/.test(l)))
      return "[LIST=1]\n" + lines.map(l => "[*]" + l.replace(/^\s*\d+\.\s+/, "")).join("\n") + "\n[/LIST]";
    return markdownTableToBbcode(block);
  }).join("\n\n");
}

function markdownQuotesToBbcode(text) {
  return text.split(/\n{2,}/).map(block => {
    const lines = block.split("\n");
    if (!lines.every(l => /^>\s?/.test(l))) return block;
    const stripped = lines.map(l => l.replace(/^>\s?/, ""));
    if (stripped.length > 1 && / said:$/.test(stripped[0]))
      return "[QUOTE=" + stripped[0].replace(/ said:$/, "") + "]" + stripped.slice(1).join("\n") + "[/QUOTE]";
    return "[QUOTE]" + stripped.join("\n") + "[/QUOTE]";
  }).join("\n\n");
}

function markdownHeadingsToBbcode(text) {
  return text.replace(/^(#{1,6})[ \t]+(.+)$/gm, (_, hashes, body) => {
    const level = hashes.length;
    const content = trimEmptyLines(body).replace(/[ \t]+#+\s*$/, "");
    if (level === 1) return "[SIZE=6][B]" + content + "[/B][/SIZE]";
    if (level === 2) return "[SIZE=5][B]" + content + "[/B][/SIZE]";
    return "[B]" + content + "[/B]";
  });
}

export function markdownToBbcode(input) {
  let text = (input || "").replace(/\r\n/g, "\n");
  const store = makePlaceholderStore();

  text = text.replace(/```(\w+)?\n([\s\S]*?)```/g, (_, language, body) => {
    const tag = language && language.toLowerCase() === "php" ? "PHP"
              : language && language.toLowerCase() === "html" ? "HTML"
              : "CODE";
    return store.take("[" + tag + "]" + trimEmptyLines(body) + "[/" + tag + "]");
  });
  text = text.replace(/`([^`\n]+)`/g, (_, body) =>
    store.take("[ICODE]" + body + "[/ICODE]")
  );

  text = markdownHeadingsToBbcode(text);
  text = text.replace(/<details>\s*<summary>([\s\S]*?)<\/summary>\s*([\s\S]*?)<\/details>/gi, (_, title, body) =>
    "[SPOILER=" + trimEmptyLines(title) + "]" + trimEmptyLines(body) + "[/SPOILER]"
  );
  text = text.replace(/>!([\s\S]*?)!</g, (_, body) =>
    "[SPOILER]" + trimEmptyLines(body) + "[/SPOILER]"
  );
  text = text.replace(/<div\s+align=["'](left|center|right)["']\s*>([\s\S]*?)<\/div>/gi, (_, alignment, body) =>
    "[" + alignment.toUpperCase() + "]" + body + "[/" + alignment.toUpperCase() + "]"
  );
  text = text.replace(/<p\s+align=["'](left|center|right)["']\s*>([\s\S]*?)<\/p>/gi, (_, alignment, body) => {
    const content = trimEmptyLines(body);
    const tagName = alignment.toUpperCase();
    if (/<a\s+[^>]*?href|<img\s/i.test(content)) {
      let processed = content
        .replace(/<a\s+[^>]*?href=["']([^"']+)["'][^>]*>\s*<img\s+[^>]*?src=["']([^"']+)["'][^>]*?\/?\s*>(?:\s*<\/a>)?/gi, (__, href, src) =>
          "[URL=" + href + "][IMG]" + src + "[/IMG][/URL]"
        )
        .replace(/<img\s+[^>]*?src=["']([^"']+)["'][^>]*\/?>/gi, (__, src) =>
          "[IMG]" + src + "[/IMG]"
        );
      processed = processed.replace(/(\[\/URL\]|\[\/IMG\])\s+(?=\[URL=|\[IMG\])/g, "$1");
      return "[" + tagName + "]" + processed + "[/" + tagName + "]";
    }
    return "[" + tagName + "]" + content + "[/" + tagName + "]";
  });
  text = text.replace(/<(h[1-6])\s+align=["'](left|center|right)["']\s*>([\s\S]*?)<\/\1>/gi, (_, tag, alignment, body) => {
    const level = parseInt(tag.slice(1), 10);
    const sized = level === 1 ? "[SIZE=6][B]" + trimEmptyLines(body) + "[/B][/SIZE]"
                : level === 2 ? "[SIZE=5][B]" + trimEmptyLines(body) + "[/B][/SIZE]"
                : "[B]" + trimEmptyLines(body) + "[/B]";
    return "[" + alignment.toUpperCase() + "]" + sized + "[/" + alignment.toUpperCase() + "]";
  });
  text = text.replace(/<a\s+[^>]*?href=["']([^"']+)["'][^>]*>\s*<img\s+[^>]*?src=["']([^"']+)["'][^>]*?\/?\s*>(?:\s*<\/a>)?/gi, (_, href, src) =>
    "[URL=" + href + "][IMG]" + src + "[/IMG][/URL]"
  );
  text = text.replace(/<img\s+[^>]*?src=["']([^"']+)["'][^>]*\/?>/gi, (_, src) =>
    "[IMG]" + src + "[/IMG]"
  );
  text = text.replace(/\[!\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, src, href) =>
    "[URL=" + trimEmptyLines(href) + "][IMG]" + trimEmptyLines(src) + "[/IMG][/URL]"
  );
  text = text.replace(/<u>([\s\S]*?)<\/u>/gi, "[U]$1[/U]");
  text = text.replace(/<span style="color:([^";]+);?">([\s\S]*?)<\/span>/gi, (_, color, body) =>
    "[COLOR=" + trimEmptyLines(color) + "]" + body + "[/COLOR]"
  );
  text = text.replace(/<span style="font-family:([^";]+);?">([\s\S]*?)<\/span>/gi, (_, font, body) =>
    "[FONT=" + trimEmptyLines(font) + "]" + body + "[/FONT]"
  );
  text = text.replace(/<span style="font-size:([^";]+);?">([\s\S]*?)<\/span>/gi, (_, size, body) =>
    "[SIZE=" + trimEmptyLines(size).replace(/em$/, "") + "]" + body + "[/SIZE]"
  );
  text = text.replace(/!\[[^\]]*?\]\(([^)]+)\)/g, (_, url) =>
    "[IMG]" + trimEmptyLines(url) + "[/IMG]"
  );
  text = text.replace(/\[([^\]]+)\]\(mailto:([^)]+)\)/g, (_, label, email) =>
    "[EMAIL=" + trimEmptyLines(email) + "]" + label + "[/EMAIL]"
  );
  text = text.replace(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g, (_, label, url) => {
    const match = url.match(/https:\/\/www\.spigotmc\.org\/members\/(\d+)\//);
    if (match) return "[USER=" + match[1] + "]" + label + "[/USER]";
    if (label === url) return "[URL]" + url + "[/URL]";
    return "[URL=" + url + "]" + label + "[/URL]";
  });
  text = text.replace(/\*\*([\s\S]*?)\*\*/g, "[B]$1[/B]");
  text = text.replace(/(^|[^\*])\*([^\*\n][\s\S]*?)\*(?!\*)/g, "$1[I]$2[/I]");
  text = text.replace(/~~([\s\S]*?)~~/g, "[S]$1[/S]");
  text = markdownListsToBbcode(text);
  text = markdownQuotesToBbcode(text);

  return trimEmptyLines(store.restore(text)).replace(/\n{3,}/g, "\n\n");
}
