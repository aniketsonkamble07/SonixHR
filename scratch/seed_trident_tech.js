const http = require('http');
const https = require('https');

const API_BASE = 'https://sonixhr.onrender.com';

function post(url, data, headers = {}) {
    return request(url, 'POST', data, headers);
}

function put(url, data, headers = {}) {
    return request(url, 'PUT', data, headers);
}

function get(url, headers = {}) {
    return request(url, 'GET', null, headers);
}

function del(url, headers = {}) {
    return request(url, 'DELETE', null, headers);
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

// Helpers for date operations
function getDatesInRange(startDate, endDate) {
    const dates = [];
    let current = new Date(startDate);
    const end = new Date(endDate);
    while (current <= end) {
        dates.push(new Date(current));
        current.setDate(current.getDate() + 1);
    }
    return dates;
}

function formatDate(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

async function run() {
    console.log('Checking Render server health...');
    let healthy = false;
    let retries = 0;
    while (!healthy && retries < 15) {
        try {
            await request(`${API_BASE}/api/public/check-email?email=test_health@test.com`, 'GET', null);
            healthy = true;
            console.log('Render server is online and ready!');
        } catch (e) {
            retries++;
            console.log(`Render server is starting... retry ${retries}/15. Waiting 5s...`);
            await new Promise(resolve => setTimeout(resolve, 5000));
        }
    }

    if (!healthy) {
        throw new Error('Render server failed to respond in time.');
    }

    const tenantPayload = {
        companyName: 'Trident Tech Corp',
        adminEmail: 'aditya.sharma.corp@tridenttech.co.in',
        adminName: 'Aditya Sharma',
        adminPhone: '9988776655',
        planCode: 'trial',
        state: 'MAHARASHTRA',
        country: 'India'
    };

    let tenantId;
    let activationToken;

    console.log('--- Step 1 & 2: Register & Activate Tenant ---');
    try {
        const tenantInfo = await post(`${API_BASE}/api/public/register`, tenantPayload);
        console.log('Tenant Registered:', tenantInfo.companyName, '(ID:', tenantInfo.tenantId, ')');
        tenantId = tenantInfo.tenantId;
        activationToken = tenantInfo.activationToken;
        console.log('Activation Token obtained:', activationToken ? 'SUCCESS' : 'MISSING');

        console.log('--- Step 2: Activate Tenant & Set Admin Password ---');
        await post(`${API_BASE}/api/public/set-password`, {
            token: activationToken,
            newPassword: 'Admin@123',
            confirmPassword: 'Admin@123'
        });
        console.log('Tenant Admin activated successfully and password configured.');
    } catch (e) {
        console.log('Tenant registration skipped or already exists:', e.message);
    }

    console.log('--- Step 3: Login Tenant Admin ---');
    let token;
    let headers;
    try {
        const loginPayload = {
            email: tenantPayload.adminEmail,
            password: 'Admin@123'
        };
        const loginInfo = await post(`${API_BASE}/api/tenant/auth/login`, loginPayload);
        console.log('Tenant Admin logged in successfully.');
        token = loginInfo.accessToken;
        headers = { 'Authorization': `Bearer ${token}` };

        // Parse tenantId from JWT claims
        const payloadBase64 = token.split('.')[1];
        const payload = JSON.parse(Buffer.from(payloadBase64, 'base64').toString());
        tenantId = payload.tenantId;
        console.log('Resolved Tenant ID from JWT claims:', tenantId);
    } catch (e) {
        console.error('Tenant Admin login failed:', e.message);
        process.exit(1);
    }

    console.log('--- Step 4: Create Departments ---');
    const departments = [
        { name: 'Engineering', code: 'ENG_TRIDENT', desc: 'Core software engineering' },
        { name: 'Human Resources', code: 'HR_TRIDENT', desc: 'Talent and operations' },
        { name: 'Sales', code: 'SLS_TRIDENT', desc: 'Business development' },
        { name: 'Finance', code: 'FIN_TRIDENT', desc: 'Accounting and payroll' }
    ];

    const createdDepts = {};
    for (const dept of departments) {
        try {
            const res = await post(`${API_BASE}/api/tenant/departments`, {
                name: dept.name,
                code: dept.code,
                description: dept.desc
            }, headers);
            console.log(`Created department: ${res.name} (ID: ${res.id})`);
            createdDepts[dept.name] = res.id;
        } catch (err) {
            console.error(`Failed to create department ${dept.name}:`, err.message);
        }
    }

    console.log('--- Step 5: Configure Global Payroll Config ---');
    const payrollPayload = {
        lopBasis: 'CALENDAR_DAYS',
        enablePfCapping: true,
        enableEsi: true,
        enablePt: true,
        enforceNewLabourCodes: false,
        defaultCurrency: 'INR',
        defaultTaxRegime: 'NEW_REGIME',
        enableOvertime: true,
        overtimeRatePerHour: 250.00,
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
            },
            {
                componentCode: 'HRA',
                componentName: 'House Rent Allowance',
                componentType: 'EARNING',
                calculationType: 'PERCENTAGE_OF_CTC',
                value: 20.00,
                evaluationOrder: 2,
                partOfPfWages: false,
                partOfEsiWages: true,
                taxable: true,
                lopApplicable: true,
                employerContribution: false,
                mandatory: true,
                allowEmployeeOverride: false,
                minValue: 0.00,
                maxValue: 10000000.00,
                formulaExpression: 'CTC * 0.2'
            },
            {
                componentCode: 'SA',
                componentName: 'Special Allowance',
                componentType: 'EARNING',
                calculationType: 'PERCENTAGE_OF_CTC',
                value: 30.00,
                evaluationOrder: 3,
                partOfPfWages: false,
                partOfEsiWages: true,
                taxable: true,
                lopApplicable: true,
                employerContribution: false,
                mandatory: true,
                allowEmployeeOverride: false,
                minValue: 0.00,
                maxValue: 10000000.00,
                formulaExpression: 'CTC * 0.3'
            }
        ]
    };

    try {
        await put(`${API_BASE}/api/payroll/config/tenants/${tenantId}/global`, payrollPayload, headers);
        console.log('Global Payroll Configured Successfully.');
    } catch (e) {
        console.error('Global Payroll configuration failed:', e.message);
    }

    console.log('--- Step 6: Create Custom Tenant Roles ---');
    const rolesToCreate = [
        { name: 'Developer', description: 'Core software engineer' },
        { name: 'Manager', description: 'Team leader or manager' },
        { name: 'Analyst', description: 'Business or financial analyst' },
        { name: 'Specialist', description: 'Subject matter expert or specialist' }
    ];

    const createdRoles = {};
    for (const roleDef of rolesToCreate) {
        try {
            const res = await post(`${API_BASE}/api/tenant/roles`, {
                name: roleDef.name,
                description: roleDef.description
            }, headers);
            console.log(`Created custom role: ${res.name} (ID: ${res.id})`);
            createdRoles[res.name] = res.id;
        } catch (err) {
            console.log(`Custom role already exists or failed: ${roleDef.name}`);
        }
    }

    // Retrieve all roles from backend to get IDs of default seeded roles (e.g. Manager)
    try {
        const existingRoles = await get(`${API_BASE}/api/tenant/roles`, headers);
        console.log(`Fetched ${existingRoles.length} roles from backend for ID mapping.`);
        for (const role of existingRoles) {
            createdRoles[role.name] = role.id;
        }
    } catch (err) {
        console.error('Failed to retrieve roles:', err.message);
    }

    console.log('--- Step 7: Register 10 Employees (Indian Names & Past Dates) ---');
    const employeeDefinitions = [
        { firstName: 'Rajesh', lastName: 'Kumar', email: 'rajesh.kumar@tridenttech.co.in', dept: 'Engineering', role: 'Developer', pos: 'Senior Developer', hire: '2026-06-01', ctc: 75000.00 },
        { firstName: 'Sunita', lastName: 'Sharma', email: 'sunita.sharma@tridenttech.co.in', dept: 'Human Resources', role: 'Specialist', pos: 'HR Lead Specialist', hire: '2026-06-01', ctc: 60000.00 },
        { firstName: 'Amit', lastName: 'Patel', email: 'amit.patel@tridenttech.co.in', dept: 'Sales', role: 'Manager', pos: 'Sales Lead Manager', hire: '2026-06-01', ctc: 80000.00 },
        { firstName: 'Priya', lastName: 'Nair', email: 'priya.nair@tridenttech.co.in', dept: 'Finance', role: 'Analyst', pos: 'Financial Analyst', hire: '2026-06-02', ctc: 70000.00 },
        { firstName: 'Aarav', lastName: 'Mehta', email: 'aarav.mehta@tridenttech.co.in', dept: 'Engineering', role: 'Developer', pos: 'Frontend Developer', hire: '2026-06-03', ctc: 55000.00 },
        { firstName: 'Neha', lastName: 'Gupta', email: 'neha.gupta@tridenttech.co.in', dept: 'Engineering', role: 'Developer', pos: 'Backend Developer', hire: '2026-06-04', ctc: 50000.00 },
        { firstName: 'Rohan', lastName: 'Joshi', email: 'rohan.joshi@tridenttech.co.in', dept: 'Sales', role: 'Analyst', pos: 'Sales Operations Analyst', hire: '2026-06-05', ctc: 48000.00 },
        { firstName: 'Divya', lastName: 'Rao', email: 'divya.rao@tridenttech.co.in', dept: 'Human Resources', role: 'Analyst', pos: 'Recruiting Analyst', hire: '2026-06-08', ctc: 52000.00 },
        { firstName: 'Vikram', lastName: 'Singh', email: 'vikram.singh@tridenttech.co.in', dept: 'Finance', role: 'Manager', pos: 'Finance Operations Manager', hire: '2026-06-10', ctc: 90000.00 },
        { firstName: 'Ananya', lastName: 'Verma', email: 'ananya.verma@tridenttech.co.in', dept: 'Engineering', role: 'Manager', pos: 'Engineering Delivery Manager', hire: '2026-06-12', ctc: 95000.00 }
    ];

    const createdEmployees = [];
    for (const def of employeeDefinitions) {
        try {
            const empPayload = {
                firstName: def.firstName,
                lastName: def.lastName,
                email: def.email,
                departmentId: createdDepts[def.dept],
                position: def.pos,
                hireDate: def.hire,
                employmentType: 'FULL_TIME',
                roleIds: [createdRoles[def.role]]
            };

            const empInfo = await post(`${API_BASE}/api/employees`, empPayload, headers);
            console.log(`Registered Employee: ${empInfo.fullName} (ID: ${empInfo.id})`);
            createdEmployees.push({
                id: empInfo.id,
                fullName: empInfo.fullName,
                email: def.email,
                hireDate: def.hire,
                ctc: def.ctc
            });
        } catch (err) {
            console.error(`Failed to register employee ${def.firstName} ${def.lastName}:`, err.message);
        }
    }

    console.log('--- Step 8: Configure Employee Compensations via API ---');
    for (const emp of createdEmployees) {
        try {
            const compensationPayload = {
                monthlyCtc: emp.ctc,
                currency: 'INR',
                taxRegime: 'NEW_REGIME',
                effectiveFrom: emp.hireDate
            };
            await put(`${API_BASE}/api/employees/${emp.id}/compensation`, compensationPayload, headers);
            console.log(`Configured CTC ${emp.ctc} for ${emp.fullName}`);
        } catch (err) {
            console.error(`Failed to configure compensation for ${emp.fullName}:`, err.message);
        }
    }

    console.log('--- Step 9: Seed Demo Attendance Records ---');
    const endDateStr = '2026-07-06';
    let attendanceCount = 0;

    for (const emp of createdEmployees) {
        console.log(`Logging attendance for ${emp.fullName} starting from hire date ${emp.hireDate}...`);
        const dates = getDatesInRange(emp.hireDate, endDateStr);
        
        for (const date of dates) {
            const day = date.getDay();
            if (day === 0 || day === 6) {
                continue; // Skip weekends
            }

            // Random distribution for demo data
            const rand = Math.random();
            let status = 'PRESENT';
            let reason = 'Regular checkin';
            let overtime = 0.0;

            if (rand < 0.04) {
                status = 'ABSENT';
                reason = 'Casual Leave';
            } else if (rand < 0.08) {
                status = 'LATE';
                reason = 'Delayed commute';
            } else if (rand < 0.11) {
                status = 'HALF_DAY';
                reason = 'Doctor appointment';
            } else {
                if (Math.random() < 0.15) {
                    overtime = 1.5;
                    reason = 'Production launch support';
                }
            }

            const attendancePayload = {
                employeeId: emp.id,
                attendanceDate: formatDate(date),
                status: status,
                reason: reason,
                overtimeHours: overtime
            };

            try {
                await post(`${API_BASE}/api/attendance/mark`, attendancePayload, headers);
                attendanceCount++;
            } catch (err) {
                // Skip on duplicate date or error
            }
        }
    }
    console.log(`Successfully back-seeded ${attendanceCount} demo attendance records.`);

    console.log('\n======================================================');
    console.log('   SEEDING OF TRIDENT TECH COMPLETED SUCCESSFULLY!');
    console.log('======================================================');
    console.log(`Tenant Admin Login Email: "${tenantPayload.adminEmail}"`);
    console.log(`Tenant Admin Login Password: "Admin@123"`);
    console.log('------------------------------------------------------');
}

run();
