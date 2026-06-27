const http = require('http');

const API_BASE = 'http://localhost:8081';

function post(url, data, headers = {}) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const bodyStr = JSON.stringify(data);
        const options = {
            hostname: urlObj.hostname,
            port: urlObj.port,
            path: urlObj.pathname,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(bodyStr),
                ...headers
            }
        };

        const req = http.request(options, (res) => {
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
                    reject(new Error(`POST ${url} failed with status ${res.statusCode}: ${body}`));
                }
            });
        });

        req.on('error', (err) => reject(err));
        req.write(bodyStr);
        req.end();
    });
}

async function run() {
    console.log('--- Step 1: Register Tenant ---');
    const tenantPayload = {
        companyName: 'Test Corporation ' + Date.now(),
        adminEmail: `admin_${Date.now()}@testcorp.com`,
        adminName: 'Admin Person',
        adminPhone: '1234567890',
        planType: 'trial'
    };

    try {
        const tenantInfo = await post(`${API_BASE}/api/public/register`, tenantPayload);
        console.log('Tenant Registered:', tenantInfo.companyName, '(ID:', tenantInfo.tenantId, ')');

        console.log('--- Step 2: Login Tenant Admin ---');
        const loginPayload = {
            email: tenantPayload.adminEmail,
            password: 'Admin@123'
        };
        const loginInfo = await post(`${API_BASE}/api/tenant/auth/login`, loginPayload, {
            'X-Tenant-ID': tenantInfo.tenantId.toString()
        });
        console.log('Logged in successfully, got access token.');
        const token = loginInfo.accessToken;

        console.log('--- Step 3: Create Department ---');
        const deptPayload = {
            name: 'Engineering',
            code: 'ENG_' + Date.now(),
            description: 'Core engineering department'
        };
        const deptInfo = await post(`${API_BASE}/api/tenant/departments`, deptPayload, {
            'Authorization': `Bearer ${token}`
        });
        console.log('Department Created:', deptInfo.name, '(ID:', deptInfo.id, ')');

        console.log('--- Step 4: Create 5 Employees ---');
        const employees = [
            { firstName: 'Alice', lastName: 'Smith', email: `alice_${Date.now()}@testcorp.com` },
            { firstName: 'Bob', lastName: 'Jones', email: `bob_${Date.now()}@testcorp.com` },
            { firstName: 'Charlie', lastName: 'Brown', email: `charlie_${Date.now()}@testcorp.com` },
            { firstName: 'Diana', lastName: 'Prince', email: `diana_${Date.now()}@testcorp.com` },
            { firstName: 'Evan', lastName: 'Wright', email: `evan_${Date.now()}@testcorp.com` }
        ];

        for (let i = 0; i < employees.length; i++) {
            const emp = employees[i];
            const empPayload = {
                firstName: emp.firstName,
                lastName: emp.lastName,
                email: emp.email,
                departmentId: deptInfo.id,
                position: 'Software Engineer',
                hireDate: '2026-06-01',
                employmentType: 'FULL_TIME',
                probationMonths: 3
            };
            const empInfo = await post(`${API_BASE}/api/employees`, empPayload, {
                'Authorization': `Bearer ${token}`
            });
            console.log(`Employee ${i + 1} Created: ${empInfo.fullName} (Code: ${empInfo.employeeCode})`);
        }

        console.log('--- Seeding Completed Successfully! ---');
        console.log(`Use the registered admin email: "${tenantPayload.adminEmail}" and password: "Admin@123" to log in.`);
    } catch (error) {
        console.error('Seeding failed:', error.message);
    }
}

run();

