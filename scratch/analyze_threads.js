const fs = require('fs');
const path = require('path');

const dumpPath = path.join(__dirname, 'thread_dump.txt');
if (!fs.existsSync(dumpPath)) {
    console.error("Thread dump file not found.");
    process.exit(1);
}

const content = fs.readFileSync(dumpPath, 'utf16le');
const lines = content.split('\n');

let currentThread = null;
let currentStack = [];

for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith('"')) {
        if (currentThread && !currentStack.some(s => s.includes('LinkedBlockingQueue.take') || s.includes('ParkingToWait'))) {
            console.log(`\nThread: ${currentThread}`);
            console.log(currentStack.slice(0, 15).join('\n'));
        }
        currentThread = trimmed;
        currentStack = [];
    } else if (currentThread && trimmed) {
        currentStack.push(trimmed);
    }
}

if (currentThread && !currentStack.some(s => s.includes('LinkedBlockingQueue.take') || s.includes('ParkingToWait'))) {
    console.log(`\nThread: ${currentThread}`);
    console.log(currentStack.slice(0, 15).join('\n'));
}
