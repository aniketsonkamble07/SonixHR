const http = require('http');
const https = require('https');

const API_BASE = 'https://sonixhr.onrender.com';

function request(url, method, data, headers = {}) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const bodyStr = data ? JSON.stringify(data) : '';
        const options = {
            hostname: urlObj.hostname,
            port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
            path: urlObj.pathname + urlObj.search,
            method: method,
            headers: {
                'Content-Type': 'application/json',
                ...headers
            }
        };

        if (bodyStr) {
            options.headers['Content-Length'] = Buffer.byteLength(bodyStr);
        }

        const client = urlObj.protocol === 'https:' ? https : http;
        const req = client.request(options, (res) => {
            let body = '';
            res.on('data', (chunk) => body += chunk);
            res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    try {
                        resolve(body ? JSON.parse(body) : null);
                    } catch (e) {
                        resolve(body);
                    }
                } else {
                    reject(new Error(`${method} ${url} failed with status ${res.statusCode}: ${body}`));
                }
            });
        });

        req.on('error', (err) => reject(err));
        if (bodyStr) {
            req.write(bodyStr);
        }
        req.end();
    });
}

async function run() {
    console.log('Logging in as Platform Admin...');
    let loginResponse;
    try {
        loginResponse = await request(`${API_BASE}/api/platform/auth/login`, 'POST', {
            email: 'admin@sonixhr.com',
            password: 'Admin@123'
        });
        console.log('Platform login successful.');
    } catch (e) {
        console.error('Platform login failed:', e.message);
        return;
    }

    const headers = { 'Authorization': `Bearer ${loginResponse.accessToken}` };

    console.log('Fetching all tenants to find Trident Tech...');
    let tenantsPage;
    try {
        tenantsPage = await request(`${API_BASE}/api/platform/tenants?size=100`, 'GET', null, headers);
    } catch (e) {
        console.error('Failed to fetch tenants:', e.message);
        return;
    }

    const tenants = tenantsPage.content || [];
    console.log(`Found ${tenants.length} tenants on platform.`);

    let targetTenant = tenants.find(t => t.companyName === 'Trident Tech Corp' || t.companyName === 'Trident Tech' || t.adminEmail === 'aditya.sharma@tridenttech.co.in');

    if (!targetTenant) {
        console.log('Target tenant not found in the list. Searching by email directly...');
        // Let's search with companyName filter
        try {
            const page = await request(`${API_BASE}/api/platform/tenants?companyName=Trident`, 'GET', null, headers);
            if (page.content && page.content.length > 0) {
                targetTenant = page.content[0];
            }
        } catch (e) {
            console.error('Failed to filter tenants:', e.message);
        }
    }

    if (!targetTenant) {
        console.error('Could not find Trident Tech tenant.');
        return;
    }

    console.log(`\nFound Trident Tech Tenant:\nID: ${targetTenant.id}\nCompany: ${targetTenant.companyName}\nAdmin: ${targetTenant.adminEmail}\nIs Active: ${targetTenant.isActive}\nStatus: ${targetTenant.status}`);

    if (!targetTenant.isActive || targetTenant.status !== 'ACTIVE') {
        console.log('\nActivating tenant...');
        try {
            const activated = await request(`${API_BASE}/api/platform/tenants/${targetTenant.id}/activate`, 'PUT', null, headers);
            console.log('Tenant activation response:', activated);
            console.log('\n--- SUCCESS: Tenant activated successfully! ---');
        } catch (e) {
            console.error('Failed to activate tenant:', e.message);
        }
    } else {
        console.log('\nTenant is already ACTIVE on platform.');
    }
}

run();
