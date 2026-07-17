const http = require('http');

const API_BASE = 'https://sonixhr.onrender.com';

function post(url, data, headers = {}) {
    return request(url, 'POST', data, headers);
}

function put(url, data, headers = {}) {
    return request(url, 'PUT', data, headers);
}

function request(url, method, data, headers = {}) {
    return new Promise((resolve, reject) => {
        const urlObj = new URL(url);
        const bodyStr = JSON.stringify(data);
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
        req.write(bodyStr);
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
    console.log('Waiting for Render server to boot and initialize database schema...');
    let healthy = false;
    while (!healthy) {
        try {
            const check = await request(`${API_BASE}/api/public/check-email?email=health_check_${Date.now()}@test.com`, 'GET', null);
            if (check && check.available !== undefined) {
                healthy = true;
                console.log('Render server is online and database tables are ready!');
            }
        } catch (e) {
            console.log('Server is still initializing (waiting for tables to be recreated)... retrying in 5 seconds...');
            await new Promise(resolve => setTimeout(resolve, 5000));
        }
    }

    console.log('--- Step 1: Register Tenant ---');
    const tenantPayload = {
        companyName: 'Apex Nexus',
        adminEmail: 'admin.89174@apexnexus.com',
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

        console.log('--- Step 1.5: Set Admin Password & Activate Tenant (DB Override) ---');
        const execSync = require('child_process').execSync;
        const dbUrl = "jdbc:postgresql://dpg-d8lqhms8aovs73dtigbg-a.oregon-postgres.render.com:5432/sonixhr_db_lder?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory";
        const dbUser = "sonixhr_db_lder_user";
        const dbPass = "693FgNnsgnIF91M5tdz8Cvys1fG04Dx8";
        const cp = "E:/Viplora/sonixhr/scratch;C:/Users/win-10/.m2/repository/org/postgresql/postgresql/42.6.2/postgresql-42.6.2.jar;C:/Users/win-10/.m2/repository/org/springframework/security/spring-security-crypto/6.1.5/spring-security-crypto-6.1.5.jar;C:/Users/win-10/.m2/repository/commons-logging/commons-logging/1.2/commons-logging-1.2.jar";
        
        const cmd = `java -cp "${cp}" com.sonixhr.DbActivateAdmin "${dbUrl}" "${dbUser}" "${dbPass}" "${tenantPayload.adminEmail}"`;
        console.log("Running DB activation override...");
        execSync(cmd, { stdio: 'inherit' });
        console.log('Password set successfully via direct DB update, account activated.');

        console.log('--- Step 2: Login Tenant Admin ---');
        const loginPayload = {
            email: tenantPayload.adminEmail,
            password: 'Admin@123'
        };
        const loginInfo = await post(`${API_BASE}/api/tenant/auth/login`, loginPayload, {
            'X-Tenant-ID': tenantId.toString()
        });
        console.log('Logged in successfully, got access token.');
        const token = loginInfo.accessToken;
        const headers = { 'Authorization': `Bearer ${token}` };

        console.log('--- Step 3: Create Departments ---');
        const deptEng = await post(`${API_BASE}/api/tenant/departments`, {
            name: 'Engineering',
            code: 'ENG_' + Date.now(),
            description: 'Core software engineering and development'
        }, headers);
        console.log('Created department: Engineering (ID:', deptEng.id, ')');

        const deptHR = await post(`${API_BASE}/api/tenant/departments`, {
            name: 'Human Resources',
            code: 'HR_' + Date.now(),
            description: 'Recruitment, operations, and talent management'
        }, headers);
        console.log('Created department: Human Resources (ID:', deptHR.id, ')');

        const deptSales = await post(`${API_BASE}/api/tenant/departments`, {
            name: 'Sales',
            code: 'SLS_' + Date.now(),
            description: 'Customer acquisitions and marketing'
        }, headers);
        console.log('Created department: Sales (ID:', deptSales.id, ')');

        console.log('--- Step 4: Configure Global Payroll Config ---');
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

        const globalPayroll = await put(`${API_BASE}/api/payroll/config/tenants/${tenantId}/global`, payrollPayload, headers);
        console.log('Global Payroll Configured Successfully.');

        console.log('--- Step 4.5: Fetch Tenant Roles ---');
        const roles = await request(`${API_BASE}/api/tenant/roles`, 'GET', null, headers);
        console.log('Fetched tenant roles:', roles.map(r => `${r.name} (ID: ${r.id})`).join(', '));
        const employeeRole = roles.find(r => r.name === 'Employee');
        if (!employeeRole) {
            throw new Error('Employee role not found for tenant!');
        }

        console.log('--- Step 5: Register Employees ---');
        const employeeDefinitions = [
            { firstName: 'Amit', lastName: 'Patel', email: 'amit.patel.89174@apexnexus.com', dept: deptEng, pos: 'Engineering Director', hire: '2026-06-01', managerIdx: null, ctc: 100000.00 },
            { firstName: 'Sanjay', lastName: 'Sharma', email: 'sanjay.sharma.89174@apexnexus.com', dept: deptEng, pos: 'Support Lead', hire: '2026-06-01', managerIdx: null, ctc: 90000.00 },
            { firstName: 'Priya', lastName: 'Iyer', email: 'priya.iyer.89174@apexnexus.com', dept: deptHR, pos: 'HR Director', hire: '2026-06-01', managerIdx: null, ctc: 80000.00 },
            { firstName: 'Aarav', lastName: 'Mehta', email: 'aarav.mehta.89174@apexnexus.com', dept: deptEng, pos: 'Software Engineer', hire: '2026-06-01', managerIdx: 0, ctc: 60000.00 },
            { firstName: 'Divya', lastName: 'Nair', email: 'divya.nair.89174@apexnexus.com', dept: deptEng, pos: 'Software Engineer', hire: '2026-06-02', managerIdx: 0, ctc: 70000.00 },
            { firstName: 'Rohan', lastName: 'Joshi', email: 'rohan.joshi.89174@apexnexus.com', dept: deptEng, pos: 'Support Engineer', hire: '2026-06-03', managerIdx: 1, ctc: 50000.00 },
            { firstName: 'Neha', lastName: 'Gupta', email: 'neha.gupta.89174@apexnexus.com', dept: deptSales, pos: 'Sales Executive', hire: '2026-06-04', managerIdx: 2, ctc: 45000.00 },
            { firstName: 'Vikram', lastName: 'Singh', email: 'vikram.singh.89174@apexnexus.com', dept: deptSales, pos: 'Sales Manager', hire: '2026-06-05', managerIdx: 2, ctc: 55000.00 },
            { firstName: 'Ananya', lastName: 'Rao', email: 'ananya.rao.89174@apexnexus.com', dept: deptHR, pos: 'HR Specialist', hire: '2026-06-05', managerIdx: 2, ctc: 40000.00 },
            { firstName: 'Karan', lastName: 'Verma', email: 'karan.verma.89174@apexnexus.com', dept: deptHR, pos: 'HR Generalist', hire: '2026-06-08', managerIdx: 2, ctc: 42000.00 }
        ];
 
        const createdEmployees = [];
        for (let i = 0; i < employeeDefinitions.length; i++) {
            const def = employeeDefinitions[i];
            
            // Check if manager is already created
            let managerId = null;
            if (def.managerIdx !== null) {
                managerId = createdEmployees[def.managerIdx].id;
            }
 
            const empPayload = {
                firstName: def.firstName,
                lastName: def.lastName,
                email: def.email,
                departmentId: def.dept.id,
                position: def.pos,
                hireDate: def.hire,
                managerId: managerId,
                employmentType: 'FULL_TIME',
                roleIds: [employeeRole.id]
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
        }

        console.log('--- Step 6: Configure Employee Compensations (DB Override) ---');
        const empPairList = createdEmployees.map(emp => `${emp.email}:${emp.ctc}`).join(',');
        const cpComp = "E:/Viplora/sonixhr/scratch;C:/Users/win-10/.m2/repository/org/postgresql/postgresql/42.6.2/postgresql-42.6.2.jar";
        const dbUrlComp = "jdbc:postgresql://dpg-d8lqhms8aovs73dtigbg-a.oregon-postgres.render.com:5432/sonixhr_db_lder?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory";
        const cmdComp = `java -cp "${cpComp}" com.sonixhr.DbConfigureCompensations "${dbUrlComp}" "sonixhr_db_lder_user" "693FgNnsgnIF91M5tdz8Cvys1fG04Dx8" ${tenantId} "${empPairList}"`;
        execSync(cmdComp, { stdio: 'inherit' });
        console.log('Compensation profiles configured successfully via DB override.');

        console.log('--- Step 7: Back-seed past attendance logs ---');
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

                // Random distribution
                const rand = Math.random();
                let status = 'PRESENT';
                let reason = 'Regular checkin';
                let overtime = 0.0;

                if (rand < 0.05) {
                    status = 'ABSENT';
                    reason = 'Sick Leave';
                } else if (rand < 0.09) {
                    status = 'LATE';
                    reason = 'Traffic delay';
                } else if (rand < 0.12) {
                    status = 'HALF_DAY';
                    reason = 'Personal work';
                } else {
                    // PRESENT: 10% chance of overtime
                    if (Math.random() < 0.10) {
                        overtime = 2.0;
                        reason = 'Project delivery support';
                    }
                }

                const attendancePayload = {
                    employeeId: emp.id,
                    attendanceDate: formatDate(date),
                    status: status,
                    reason: reason,
                    overtimeHours: overtime
                };

                await post(`${API_BASE}/api/attendance/mark`, attendancePayload, headers);
                attendanceCount++;
            }
        }
        console.log(`Successfully back-seeded ${attendanceCount} attendance records.`);

        console.log('--- Step 8: Run June 2026 Payrun ---');
        const payrunPayload = {
            month: 6,
            year: 2026
        };

        const payrunInfo = await post(`${API_BASE}/api/payroll/payruns`, payrunPayload, headers);
        console.log('Payrun executed successfully! Payrun ID:', payrunInfo.id, 'Status:', payrunInfo.status);

        console.log('\n======================================================');
        console.log('   SEEDING OF APEX NEXUS WITH INDIAN NAMES COMPLETED!');
        console.log('======================================================');
        console.log(`Tenant Admin Login Email: "${tenantPayload.adminEmail}"`);
        console.log('Tenant Admin Login Password: "Admin@123"');
        console.log('------------------------------------------------------');
        
    } catch (e) {
        console.error('Seeding process failed:', e.message);
    }
}

run();
