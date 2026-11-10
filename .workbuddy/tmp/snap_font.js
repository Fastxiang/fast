const fs = require('fs');

const file = process.argv[2];
let css = fs.readFileSync(file, 'utf8');

const before = [...new Set([...css.matchAll(/font-size:\s*(\d+)px/g)].map(m => m[1]))].sort((a, b) => a - b);

// 原版像素字体 em = 10px（sCapHeight 700/1000 -> 7px 大写高度），
// 只有 10 的整数倍才能 1:1 落在像素网格上，否则 AWT 栅格化出来是糊的
const map = { '13': '10', '16': '20', '24': '30' };

css = css.replace(/font-size:\s*(\d+)px/g, (m, v) => (map[v] ? 'font-size: ' + map[v] + 'px' : m));

fs.writeFileSync(file, css);

const after = [...new Set([...css.matchAll(/font-size:\s*(\d+)px/g)].map(m => m[1]))].sort((a, b) => a - b);
console.log('before:', before.join(' '));
console.log('after :', after.join(' '));
