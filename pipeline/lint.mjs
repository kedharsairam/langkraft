// Simple, dependency-free lint for JSONL content. Run: node pipeline/lint.mjs
import { readFileSync, readdirSync, existsSync } from "node:fs";
import { join } from "node:path";

const assetsDir = new URL("../app/src/main/assets/langkraft/", import.meta.url).pathname.replace(/^\/+/, "");
const files = existsSync(assetsDir) ? readdirSync(assetsDir).filter((f) => f.endsWith(".jsonl")) : [];
if (files.length === 0) { console.error("No JSONL found in", assetsDir); process.exit(1); }

let errors = 0;
for (const file of files) {
  const lines = readFileSync(join(assetsDir, file), "utf8").split("\n").filter((l) => l.trim());
  console.log(`Checking ${file} (${lines.length} rows)`);
  for (const [i, line] of lines.entries()) {
    let row;
    try { row = JSON.parse(line); } catch (e) { console.error(`  line ${i + 1}: bad JSON`); errors++; continue; }
    const need = ["id", "lang", "theme", "english", "romanization", "youSay", "soundTip", "note", "source", "checked"];
    for (const k of need) {
      if (!row[k] || String(row[k]).trim() === "") { console.error(`  line ${i + 1}: missing ${k}`); errors++; }
    }
    if (row.youSay && !/[A-ZÀ-Ý]/.test(row.youSay)) { console.error(`  line ${i + 1}: youSay has no CAPS stress: ${row.youSay}`); errors++; }
    if (row.checked && !/^\d{4}-\d{2}-\d{2}$/.test(row.checked)) { console.error(`  line ${i + 1}: checked must be YYYY-MM-DD`); errors++; }
  }
}
if (errors) { console.error(`Lint failed with ${errors} errors.`); process.exit(1); }
console.log("Content lint OK.");
