const fs = require('fs');
const path = require('path');

const content = fs.readFileSync(path.join(__dirname, 'thread_dump.txt'), 'utf16le');
const lines = content.split('\n');

for (const line of lines) {
    if (line.trim().startsWith('"')) {
        console.log(line.trim());
    }
}
