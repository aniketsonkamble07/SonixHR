const API_BASE = "http://localhost:8081";

async function runSchedulerTests() {
    console.log("=== STARTING SONIXHR SUBSCRIPTION SCHEDULER TEST RUNNER ===");

    // 1. Login as Platform Super Admin
    console.log("\n[PREPARATION] Logging in as Platform Super Admin...");
    const adminLoginRes = await fetch(`${API_BASE}/api/platform/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            email: "admin@sonixhr.com",
            password: "Admin@123"
        })
    });
    if (!adminLoginRes.ok) {
        throw new Error(`Failed to login as Platform Admin: ${adminLoginRes.status}`);
    }
    const adminLoginData = await adminLoginRes.json();
    const adminAccessToken = adminLoginData.accessToken;
    const adminHeaders = { "Authorization": `Bearer ${adminAccessToken}` };
    console.log("✔ Super Admin logged in successfully.");

    // 2. Register a new Tenant
    console.log("\n[PREPARATION] Registering a new Tenant...");
    const tenantEmail = `admin@schedulercorp_${Date.now()}.com`;
    const regRes = await fetch(`${API_BASE}/api/public/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            companyName: "Scheduler Corp " + Date.now(),
            companyEmail: `info@schedulercorp_${Date.now()}.com`,
            phone: "+919999999999",
            address: "123 Lifecycle St",
            city: "Pune",
            state: "Maharashtra",
            stateText: "Maharashtra",
            country: "India",
            postalCode: "411001",
            adminFirstName: "Lifecycle",
            adminLastName: "Test",
            adminEmail: tenantEmail,
            adminPhone: "+919999999999",
            planCode: "BASIC_MONTHLY"
        })
    });
    if (!regRes.ok) {
        const text = await regRes.text();
        throw new Error(`Failed to register tenant: ${regRes.status} - ${text}`);
    }
    const regData = await regRes.json();
    const tenantId = regData.tenantId;
    const activationToken = regData.activationToken;
    console.log(`✔ Tenant registered. Tenant ID: ${tenantId}`);

    // 3. Activate Tenant Admin
    console.log("\n[PREPARATION] Activating Tenant Admin...");
    const actRes = await fetch(`${API_BASE}/api/public/set-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            token: activationToken,
            newPassword: "TenantAdmin@123",
            confirmPassword: "TenantAdmin@123"
        })
    });
    if (!actRes.ok) {
        const text = await actRes.text();
        throw new Error(`Failed to activate: ${actRes.status} - ${text}`);
    }
    console.log("✔ Tenant Admin account activated.");

    // Helper: Trigger Scheduler
    async function triggerScheduler() {
        const res = await fetch(`${API_BASE}/api/platform/debug/trigger-scheduler`, { headers: adminHeaders });
        if (!res.ok) throw new Error("Failed to trigger scheduler");
        console.log("-> Scheduler lifecycle run triggered.");
    }

    // Helper: Adjust subscription dates
    async function adjustSub(daysOffset, autoRenew) {
        const res = await fetch(`${API_BASE}/api/platform/debug/adjust-subscription-dates?tenantId=${tenantId}&daysOffset=${daysOffset}&autoRenew=${autoRenew}`, {
            method: "POST",
            headers: adminHeaders
        });
        if (!res.ok) {
            const text = await res.text();
            throw new Error(`Failed to adjust subscription dates: ${res.status} - ${text}`);
        }
        const data = await res.json();
        console.log(`-> Adjusted subscription: billingPeriodEnd offset = ${daysOffset} days, autoRenew = ${autoRenew}`);
    }

    // Helper: Adjust tenant dates
    async function adjustTenant(expiredDaysAgo, dataStatus) {
        const res = await fetch(`${API_BASE}/api/platform/debug/adjust-tenant-dates?tenantId=${tenantId}&expiredDaysAgo=${expiredDaysAgo}&dataStatus=${dataStatus}`, {
            method: "POST",
            headers: adminHeaders
        });
        if (!res.ok) {
            const text = await res.text();
            throw new Error(`Failed to adjust tenant dates: ${res.status} - ${text}`);
        }
        const data = await res.json();
        console.log(`-> Adjusted tenant: expiredDaysAgo = ${expiredDaysAgo}, dataStatus = ${dataStatus}`);
    }

    // Helper: Get Tenant Info
    async function getTenantDetails() {
        const res = await fetch(`${API_BASE}/api/platform/tenants/${tenantId}`, { headers: adminHeaders });
        if (!res.ok) return null;
        return await res.json();
    }

    // ==========================================
    // TEST CASES (Steps 1 & 2)
    // ==========================================
    console.log("\n[TEST] Testing 7-day expiration reminder warning (autoRenew = false)...");
    await adjustSub(7, false);
    await triggerScheduler();

    console.log("\n[TEST] Testing 3-day upcoming renewal notification (autoRenew = true)...");
    await adjustSub(3, true);
    await triggerScheduler();

    console.log("\n[TEST] Testing 3-day expiration reminder warning (autoRenew = false)...");
    await adjustSub(3, false);
    await triggerScheduler();

    console.log("\n[TEST] Testing 1-day expiration reminder warning (autoRenew = false)...");
    await adjustSub(1, false);
    await triggerScheduler();

    // ==========================================
    // TEST CASE (Step 3: Auto-renew fails / succeeds)
    // ==========================================
    console.log("\n[TEST] Testing auto-renewal attempt expiring in 1 day (autoRenew = true)...");
    await adjustSub(1, true);
    await triggerScheduler();

    // ==========================================
    // TEST CASE (Step 4 & 5: Immediate Expiration)
    // ==========================================
    console.log("\n[TEST] Testing immediate subscription expiration (No Grace Period)...");
    await adjustSub(-1, false); // Expired 1 day ago
    await triggerScheduler();
    
    let tenantInfo = await getTenantDetails();
    console.log("Tenant active status (expected false):", tenantInfo.active);
    console.log("Tenant plan status (expected EXPIRED):", tenantInfo.planStatus);
    console.log("Tenant data status (expected RETAINED):", tenantInfo.dataStatus);
    console.log("Tenant status (expected SUSPENDED):", tenantInfo.status);
    
    if (tenantInfo.planStatus !== "EXPIRED" || tenantInfo.dataStatus !== "RETAINED" || tenantInfo.status !== "SUSPENDED") {
        throw new Error("Step 4 Immediate Expiration validation failed!");
    }
    console.log("✔ Step 4 Passed: Expired active subscription immediately suspension.");

    // ==========================================
    // TEST CASE (Step 6: Archiving)
    // ==========================================
    console.log("\n[TEST] Testing transition to ARCHIVED (31 days post-expiry)...");
    await adjustTenant(31, "RETAINED");
    await triggerScheduler();
    
    tenantInfo = await getTenantDetails();
    console.log("Tenant data status (expected ARCHIVED):", tenantInfo.dataStatus);
    if (tenantInfo.dataStatus !== "ARCHIVED") {
        throw new Error("Step 6 Archival transition validation failed!");
    }
    console.log("✔ Step 6 Passed: Tenant successfully transitioned to ARCHIVED.");

    // ==========================================
    // TEST CASE (Step 7: Soft Delete)
    // ==========================================
    console.log("\n[TEST] Testing transition to ELIGIBLE_FOR_DELETION (121 days post-expiry)...");
    await adjustTenant(121, "ARCHIVED");
    await triggerScheduler();
    
    tenantInfo = await getTenantDetails();
    console.log("Tenant data status (expected ELIGIBLE_FOR_DELETION):", tenantInfo.dataStatus);
    console.log("Tenant status (expected DELETED):", tenantInfo.status);
    if (tenantInfo.dataStatus !== "ELIGIBLE_FOR_DELETION" || tenantInfo.status !== "DELETED") {
        throw new Error("Step 7 Soft Delete transition validation failed!");
    }
    console.log("✔ Step 7 Passed: Tenant successfully soft-deleted.");

    // ==========================================
    // TEST CASE (Step 8: Hard Purge)
    // ==========================================
    console.log("\n[TEST] Testing Hard Purge of eligible tenants...");
    await adjustTenant(125, "ELIGIBLE_FOR_DELETION");
    await triggerScheduler();
    
    tenantInfo = await getTenantDetails();
    console.log("Tenant details retrieval post-purge (expected null/deleted):", tenantInfo);
    if (tenantInfo !== null) {
        throw new Error("Step 8 Purge validation failed - tenant still exists!");
    }
    console.log("✔ Step 8 Passed: Tenant successfully hard-purged.");

    console.log("\n==========================================================");
    console.log("✔ ALL LIFECYCLE SCHEDULER FLOW TESTS PASSED!");
    console.log("==========================================================");
}

runSchedulerTests().catch(err => {
    console.error("❌ SCHEDULER TESTS FAILED:", err);
    process.exit(1);
});
