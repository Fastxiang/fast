const fs = require('fs');
const dir = 'D:/Minecraft/fast_mod/fast-forge-1.20.1-47.4.0/src/main/resources/assets/apricityui/apricity/fast/';
const js = fs.readFileSync(dir + 'shop.js', 'utf8');
const html = fs.readFileSync(dir + 'shop.html', 'utf8');
const css = fs.readFileSync(dir + 'shop.css', 'utf8');

const bad = [
  [/window\s*\.\s*[A-Za-z_]\w*\s*=[^=]/g, 'assign to window.x (kills the whole script)'],
  [/\bconst\b/g, 'const'],
  [/\blet\b/g, 'let'],
  [/=>/g, 'arrow function'],
  [/[`]/g, 'template literal'],
  [/\?\./g, 'optional chaining'],
  [/Object\.entries/g, 'Object.entries'],
  [/\.includes\s*\(/g, 'Array.includes'],
  [/Number\.is(Finite|SafeInteger)/g, 'Number.isXxx'],
  [/toLocaleString/g, 'toLocaleString'],
  [/\.replaceChildren\s*\(/g, 'replaceChildren'],
  [/\bdataset\b/g, 'dataset'],
  [/\.closest\s*\(/g, 'closest'],
  [/\bfor\s+each\b/g, 'for each'],
];
let hit = false;
for (const [re, msg] of bad) {
  const m = js.match(re);
  if (m) { hit = true; console.log('!! ' + msg + '  x' + m.length); }
}
if (!hit) console.log('fatal-syntax scan: PASS');

const ids = [...html.matchAll(/id="([^"]+)"/g)].map(m => m[1]);
const dup = ids.filter((v, i) => ids.indexOf(v) !== i);
console.log('html ids:', ids.length, 'duplicates:', dup);

const used = [...js.matchAll(/fsEl\("([^"]+)"\)|fsBind\("([^"]+)"/g)].map(m => m[1] || m[2]);
const missing = [...new Set(used)].filter(x => !ids.includes(x));
console.log('js ids missing in html:', missing);

let d = 0, line = 1, neg = null;
for (const ch of css) { if (ch === '\n') line++; if (ch === '{') d++; if (ch === '}') { d--; if (d < 0 && !neg) neg = line; } }
console.log('css brace balance:', d, 'first negative at line:', neg);

// css: unsupported properties that would be silently dropped
const unsupported = ['text-shadow', 'outline', 'overflow-wrap', 'inset:', '-moz-appearance', 'font:'];
for (const p of unsupported) {
  const n = (css.split(p).length - 1);
  if (n) console.log('note: css contains "' + p + '" x' + n + ' (ignored by AUI)');
}

// classes used in css vs html/js
const cssClasses = new Set([...css.matchAll(/\.([a-zA-Z][\w-]*)/g)].map(m => m[1]));
const htmlClasses = new Set();
for (const m of html.matchAll(/class="([^"]+)"/g)) m[1].split(/\s+/).forEach(c => c && htmlClasses.add(c));
for (const m of js.matchAll(/class=\\?"([^"\\]*)/g)) m[1].split(/\s+/).forEach(c => c && htmlClasses.add(c));
const unused = [...cssClasses].filter(c => !htmlClasses.has(c));
console.log('css classes not found in html/js:', unused);
