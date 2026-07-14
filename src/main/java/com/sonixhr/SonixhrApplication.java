package com.sonixhr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SonixhrApplication {

	public static void main(String[] args) {
		runPreStartupMigration();
		SpringApplication.run(SonixhrApplication.class, args);
	}

	private static void runPreStartupMigration() {
		System.out.println("[Pre-Startup] Running database migration check...");
		// 1. Load .env manually
		Map<String, String> env = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				int equalsIdx = line.indexOf('=');
				if (equalsIdx > 0) {
					String key = line.substring(0, equalsIdx).trim();
					String val = line.substring(equalsIdx + 1).trim();
					env.put(key, val);
				}
			}
		} catch (Exception e) {
			System.out.println("[Pre-Startup] Warning: Could not read .env file: " + e.getMessage());
		}

		String url = System.getenv("DB_URL");
		if (url == null || url.trim().isEmpty()) {
			url = env.getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/sonixhr_db?ssl=false");
		}
		String user = System.getenv("DB_USERNAME");
		if (user == null || user.trim().isEmpty()) {
			user = env.getOrDefault("DB_USERNAME", "postgres");
		}
		String password = System.getenv("DB_PASSWORD");
		if (password == null || password.trim().isEmpty()) {
			password = env.getOrDefault("DB_PASSWORD", "root");
		}

		// 2. Run JDBC update
		try (Connection conn = DriverManager.getConnection(url, user, password);
				Statement stmt = conn.createStatement()) {
			System.out.println("[Pre-Startup] Connected to database successfully.");

			// Check if subscription_plans table exists
			boolean plansTableExists = false;
			try {
				stmt.executeQuery("SELECT 1 FROM subscription_plans LIMIT 1");
				plansTableExists = true;
			} catch (Exception e) {
				System.out.println("[Pre-Startup] subscription_plans table does not exist yet.");
			}

			long planId = -1;
			if (plansTableExists) {
				ResultSet rs = stmt.executeQuery("SELECT id FROM subscription_plans ORDER BY id LIMIT 1");
				if (rs.next()) {
					planId = rs.getLong(1);
				}
				// Ensure all columns exist in the existing table to match entity definition
				try {
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS code varchar(50)");
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS currency varchar(3) DEFAULT 'USD'");
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS max_users integer");
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS max_employees integer");
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS features jsonb");
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS is_custom boolean DEFAULT false NOT NULL");
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS is_public boolean DEFAULT true NOT NULL");
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS display_order integer DEFAULT 0");
					stmt.execute("ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS deleted_at timestamp");
				} catch (Exception e) {
					System.out.println("[Pre-Startup] Could not alter subscription_plans table: " + e.getMessage());
				}
			} else {
				System.out.println("[Pre-Startup] Creating subscription_plans table...");
				stmt.execute("CREATE TABLE IF NOT EXISTS subscription_plans (" +
						"id bigserial primary key, " +
						"name varchar(100) not null, " +
						"code varchar(50), " +
						"price numeric(10, 2) default 0.00 not null, " +
						"validity_months integer default 1 not null, " +
						"currency varchar(3) default 'USD', " +
						"max_users integer, " +
						"max_employees integer, " +
						"features jsonb, " +
						"is_custom boolean default false not null, " +
						"is_active boolean default true not null, " +
						"is_public boolean default true not null, " +
						"display_order integer default 0, " +
						"description varchar(500), " +
						"created_at timestamp, " +
						"updated_at timestamp, " +
						"deleted_at timestamp)");
			}

			if (planId != -1) {
				System.out.println("[Pre-Startup] Default plan ID is: " + planId);
			} else {
				System.out.println("[Pre-Startup] No default plan found in subscription_plans.");
			}

			// Check if tenant_subscriptions table exists
			boolean subsTableExists = false;
			try {
				stmt.executeQuery("SELECT 1 FROM tenant_subscriptions LIMIT 1");
				subsTableExists = true;
			} catch (Exception e) {
				System.out.println("[Pre-Startup] tenant_subscriptions table does not exist yet.");
			}

			if (subsTableExists) {
				System.out.println(
						"[Pre-Startup] Ensuring subscription_plan_id, is_current, is_active, auto_renew, cancelled_at_end_of_period, billing_period_start and billing_period_end columns exist in tenant_subscriptions...");
				try {
					stmt.execute(
							"ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS subscription_plan_id bigint");
					stmt.execute(
							"ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS is_current boolean DEFAULT true NOT NULL");
					stmt.execute(
							"ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true NOT NULL");
					stmt.execute(
							"ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS auto_renew boolean DEFAULT true NOT NULL");
					stmt.execute(
							"ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS cancelled_at_end_of_period boolean DEFAULT false NOT NULL");
					stmt.execute(
							"ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS billing_period_start date DEFAULT CURRENT_DATE NOT NULL");
					stmt.execute(
							"ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS billing_period_end date DEFAULT CURRENT_DATE NOT NULL");
				} catch (Exception e) {
					System.out.println("[Pre-Startup] Could not add column: " + e.getMessage());
				}

				try {
					stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_tenant_current_subscription " +
							"ON tenant_subscriptions (tenant_id) " +
							"WHERE (is_current = true)");
					System.out.println("[Pre-Startup] Ensuring unique index uk_tenant_current_subscription exists on tenant_subscriptions...");
				} catch (Exception e) {
					System.out.println("[Pre-Startup] Could not create unique index on tenant_subscriptions: " + e.getMessage());
				}

				if (planId != -1) {
					System.out.println("[Pre-Startup] Populating null subscription_plan_id with default plan ID...");
					int updated = stmt.executeUpdate("UPDATE tenant_subscriptions SET subscription_plan_id = " + planId
							+ " WHERE subscription_plan_id IS NULL");
					System.out.println("[Pre-Startup] Updated " + updated + " records in tenant_subscriptions.");
				} else {
					System.out.println("[Pre-Startup] Skipping populating null subscription_plan_id since no plans exist.");
				}
			}

			// Check if platform_statutory_rate_configs table exists and ensure is_deleted has no nulls
			boolean statutoryTableExists = false;
			try {
				stmt.executeQuery("SELECT 1 FROM platform_statutory_rate_configs LIMIT 1");
				statutoryTableExists = true;
			} catch (Exception e) {
				System.out.println("[Pre-Startup] platform_statutory_rate_configs table does not exist yet.");
			}

			if (statutoryTableExists) {
				System.out.println("[Pre-Startup] Ensuring is_deleted column exists in platform_statutory_rate_configs...");
				try {
					stmt.execute("ALTER TABLE platform_statutory_rate_configs ADD COLUMN IF NOT EXISTS is_deleted boolean DEFAULT false");
					stmt.execute("UPDATE platform_statutory_rate_configs SET is_deleted = false WHERE is_deleted IS NULL");
					stmt.execute("ALTER TABLE platform_statutory_rate_configs ALTER COLUMN is_deleted SET NOT NULL");
				} catch (Exception e) {
					System.out.println("[Pre-Startup] Could not adjust is_deleted column in platform_statutory_rate_configs: " + e.getMessage());
				}
			}

			// Check if tenants table exists and add fields
			boolean tenantsTableExists = false;
			try {
				stmt.executeQuery("SELECT 1 FROM tenants LIMIT 1");
				tenantsTableExists = true;
			} catch (Exception e) {
				System.out.println("[Pre-Startup] tenants table does not exist yet.");
			}

			if (tenantsTableExists) {
				System.out.println("[Pre-Startup] Ensuring lifecycle columns exist in tenants table...");
				try {
					stmt.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS data_status varchar(50) DEFAULT 'RETAINED' NOT NULL");
					stmt.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS expired_at timestamp");
					stmt.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS archived_at timestamp");
					stmt.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS archive_warning_notified_at timestamp");
					stmt.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS final_reminder_sent_at timestamp");
					stmt.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS expiration_notified_at timestamp");
					stmt.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS deleted_at timestamp");
					stmt.execute("ALTER TABLE tenants ADD COLUMN IF NOT EXISTS deleted_by_admin_id bigint");
				} catch (Exception e) {
					System.out.println("[Pre-Startup] Could not alter tenants table: " + e.getMessage());
				}
			}

			System.out.println("[Pre-Startup] Pre-startup database migration checks completed successfully.");
		} catch (Exception e) {
			System.out.println("[Pre-Startup] Error during migration check: " + e.getMessage());
		}
	}
}
