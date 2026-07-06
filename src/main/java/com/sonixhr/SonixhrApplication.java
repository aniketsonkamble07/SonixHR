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

			long planId = 1;
			if (plansTableExists) {
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM subscription_plans");
				rs.next();
				int plansCount = rs.getInt(1);
				if (plansCount == 0) {
					System.out.println("[Pre-Startup] Inserting default trial plan into subscription_plans...");
					stmt.execute(
							"INSERT INTO subscription_plans(code, name, monthly_price, max_employees, max_storage_mb, trial_days, is_trial, validity_months, is_active) "
									+
									"VALUES('trial', 'Trial Plan', 0.0, 10, 512, 14, true, 1, true)");
				}

				rs = stmt.executeQuery("SELECT id FROM subscription_plans ORDER BY id LIMIT 1");
				if (rs.next()) {
					planId = rs.getLong(1);
				}
			} else {
				System.out.println("[Pre-Startup] Creating subscription_plans table...");
				stmt.execute("CREATE TABLE IF NOT EXISTS subscription_plans (" +
						"id bigserial primary key, " +
						"code varchar(50) unique not null, " +
						"name varchar(100) not null, " +
						"monthly_price double precision not null, " +
						"max_employees integer not null, " +
						"max_storage_mb integer not null, " +
						"trial_days integer not null, " +
						"is_trial boolean not null, " +
						"validity_months integer default 1 not null, " +
						"is_active boolean default true not null," +
						"description varchar(500)," +
						"created_at timestamp," +
						"updated_at timestamp)");

				System.out.println("[Pre-Startup] Inserting default trial plan...");
				stmt.execute(
						"INSERT INTO subscription_plans(code, name, monthly_price, max_employees, max_storage_mb, trial_days, is_trial, validity_months, is_active) "
								+
								"VALUES('trial', 'Trial Plan', 0.0, 10, 512, 14, true, 1, true)");

				ResultSet rs = stmt.executeQuery("SELECT id FROM subscription_plans ORDER BY id LIMIT 1");
				if (rs.next()) {
					planId = rs.getLong(1);
				}
			}

			System.out.println("[Pre-Startup] Default plan ID is: " + planId);

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
						"[Pre-Startup] Ensuring subscription_plan_id column exists in tenant_subscriptions...");
				try {
					stmt.execute(
							"ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS subscription_plan_id bigint");
				} catch (Exception e) {
					System.out.println("[Pre-Startup] Could not add column: " + e.getMessage());
				}

				System.out.println("[Pre-Startup] Populating null subscription_plan_id with default plan ID...");
				int updated = stmt.executeUpdate("UPDATE tenant_subscriptions SET subscription_plan_id = " + planId
						+ " WHERE subscription_plan_id IS NULL");
				System.out.println("[Pre-Startup] Updated " + updated + " records in tenant_subscriptions.");
			}

			System.out.println("[Pre-Startup] Pre-startup database migration checks completed successfully.");
		} catch (Exception e) {
			System.out.println("[Pre-Startup] Error during migration check: " + e.getMessage());
		}
	}
}
