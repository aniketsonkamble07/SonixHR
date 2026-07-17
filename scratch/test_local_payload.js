const http = require('http');

const API_BASE = 'http://localhost:8081';

function post(url, data, headers = {}) {
    return request(url, 'POST', data, headers);
}

function put(url, data, headers = {}) {
    return request(url, 'PUT', data, headers);
}

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
                'Content-Length': Buffer.byteLength(bodyStr),
                ...headers
            }
        };

        const client = urlObj.protocol === 'https:' ? require('https') : http;
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
    const adminEmail = `admin.test.${Date.now()}@test.com`;
    const companyName = `Test Company ${Date.now()}`;

    console.log('--- Register Tenant ---');
    const tenantPayload = {
        companyName: companyName,
        adminEmail: adminEmail,
        adminName: 'Aarav Sharma',
        adminPhone: '9876543210',
        planCode: 'trial',
        state: 'MAHARASHTRA',
        country: 'India'
    };

    try {
        const tenantInfo = await post(`${API_BASE}/api/public/register`, tenantPayload);
        console.log('Tenant Registered:', tenantInfo.companyName, '(ID:', tenantInfo.tenantId, ')');
        const tenantId = tenantInfo.tenantId;

        console.log('--- Activating Admin via Java CLI (Local) ---');
        const execSync = require('child_process').execSync;
        const cp = "E:/Viplora/sonixhr/scratch;C:/Users/win-10/.m2/repository/org/postgresql/postgresql/42.6.2/postgresql-42.6.2.jar;C:/Users/win-10/.m2/repository/org/springframework/security/spring-security-crypto/6.1.5/spring-security-crypto-6.1.5.jar;C:/Users/win-10/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar";
        const dbUrl = "jdbc:postgresql://localhost:5432/sonixhr_db?ssl=false";
        const cmd = `java -cp "${cp}" com.sonixhr.DbActivateAdmin "${dbUrl}" "postgres" "root" "${adminEmail}"`;
        execSync(cmd, { stdio: 'inherit' });

        console.log('--- Login Tenant Admin ---');
        const loginPayload = {
            email: adminEmail,
            password: 'Admin@123'
        };
        const loginInfo = await post(`${API_BASE}/api/tenant/auth/login`, loginPayload, {
            'X-Tenant-ID': tenantId.toString()
        });
        const token = loginInfo.accessToken;
        const headers = { 'Authorization': `Bearer ${token}` };

        console.log('--- Configure Global Payroll ---');
        const payrollPayload = {
            lopBasis: 'CALENDAR_DAYS',
            enablePfCapping: true,
            enableEsi: true,
            enablePt: true,
            enforceNewLabourCodes: false,
            defaultCurrency: 'INR',
            defaultTaxRegime: 'NEW_REGIME',
            enableOvertime: true,
            overtimeRatePerHour: 200.00,
            effectiveFrom: '2026-06-01',
            salaryStructures: [
                {
                    componentCode: 'BASIC',
                    componentName: 'Basic Salary',
                    componentType: 'EARNING',
                    calculationType: 'PERCENTAGE_OF_CTC',
                    value: 50.00,
                    evaluationOrder: 1,
                    partOfPfWages: true,
                    partOfEsiWages: true,
                    taxable: true,
                    lopApplicable: true,
                    employerContribution: false,
                    mandatory: true,
                    allowEmployeeOverride: false,
                    minValue: 0.00,
                    maxValue: 10000000.00,
                    formulaExpression: 'CTC * 0.5'
                }
            ]
        };

        const globalPayroll = await put(`${API_BASE}/api/payroll/config/tenants/${tenantId}/global`, payrollPayload, headers);
        console.log('Global Payroll Configured Successfully:', globalPayroll);

        console.log('--- Create Department ---');
        const deptInfo = await post(`${API_BASE}/api/tenant/departments`, {
            name: 'Engineering',
            code: 'ENG_' + Date.now(),
            description: 'Core software engineering'
        }, headers);
        console.log('Created department:', deptInfo.id);

        console.log('--- Fetch Tenant Roles ---');
        const roles = await request(`${API_BASE}/api/tenant/roles`, 'GET', null, headers);
        console.log('Fetched tenant roles:', roles.map(r => `${r.name} (ID: ${r.id})`).join(', '));
        const employeeRole = roles.find(r => r.name === 'Employee');
        if (!employeeRole) {
            throw new Error('Employee role not found for tenant!');
        }

        console.log('--- Register Employee ---');
        const empPayload = {
            firstName: 'Amit',
            lastName: 'Patel',
            email: `amit.patel.${Date.now()}@test.com`,
            departmentId: deptInfo.id,
            position: 'Software Engineer',
            hireDate: '2026-06-01',
            employmentType: 'FULL_TIME',
            roleIds: [employeeRole.id]
        };
        const empInfo = await post(`${API_BASE}/api/employees`, empPayload, headers);
        console.log('Employee created successfully:', empInfo.fullName, '(ID:', empInfo.id, ')');

        console.log('--- Configure Compensation Profile ---');
        const compPayload = {
            employeeId: empInfo.id,
            monthlyCtc: 100000.00,
            currency: 'INR',
            taxRegime: 'NEW_REGIME',
            effectiveFrom: '2026-06-01'
        };
        const compInfo = await post(`${API_BASE}/api/payroll/config/tenants/${tenantId}/employees/${empInfo.id}/profile`, compPayload, headers);
        console.log('Compensation profile created successfully:', compInfo);

    } catch (e) {
        console.error('Local test failed:', e.message);
    }
}

run();
