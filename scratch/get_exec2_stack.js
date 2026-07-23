const fs = require('fs');
const path = require('path');

const content = fs.readFileSync(path.join(__dirname, 'thread_dump.txt'), 'utf16le');
const lines = content.split('\n');

let found = false;
let stack = [];

for (const line of lines) {
    if (line.trim().startsWith('"http-nio-8081-exec-2"')) {
        found = true;
        stack.push(line.trim());
    } else if (found) {
        if (line.trim().startsWith('"') || line.trim() === '') {
            if (stack.length > 1) break;
        } else {
            stack.push(line.trim());
        }
    }
}

console.log(stack.slice(0, 50).join('\n'));
