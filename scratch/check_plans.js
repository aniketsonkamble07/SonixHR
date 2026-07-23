const API_BASE = "http://localhost:8081";

async function checkPlans() {
    try {
        console.log("Logging in as Platform Admin...");
        const adminLoginRes = await fetch(`${API_BASE}/api/platform/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                email: "admin@sonixhr.com",
                password: "Admin@123"
            })
        });

        const adminLoginData = await adminLoginRes.json();
        const token = adminLoginData.accessToken;
        console.log("Admin token:", token ? "Acquired" : "Failed");

        console.log("\nFetching active plans...");
        const activePlansRes = await fetch(`${API_BASE}/api/platform/subscription-plans/active`, {
            method: "GET",
            headers: { "Authorization": `Bearer ${token}` }
        });
        const activePlans = await activePlansRes.json();
        console.log("Active Plans:", JSON.stringify(activePlans, null, 2));

    } catch (e) {
        console.error(e);
    }
}

checkPlans();
