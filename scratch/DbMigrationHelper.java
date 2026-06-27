import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;

public class DbMigrationHelper {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/sonixhr_db";
        String user = "postgres";
        String password = "root";
        try {
            Class.forName("org.postgresql.Driver");
        } catch (Exception e) {
            System.out.println("PostgreSQL Driver not found in ClassPath: " + e.getMessage());
        }
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            System.out.println("Connected to database successfully!");

            // Check if subscription_plans table exists
            boolean plansTableExists = false;
            try {
                stmt.executeQuery("SELECT 1 FROM subscription_plans LIMIT 1");
                plansTableExists = true;
            } catch (Exception e) {
                System.out.println("subscription_plans table does not exist. It will be created by Hibernate.");
            }

            long planId = 1;
            if (plansTableExists) {
                // Ensure subscription_plans has at least one row
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM subscription_plans");
                rs.next();
                int plansCount = rs.getInt(1);
                System.out.println("Found " + plansCount + " subscription plans.");
                if (plansCount == 0) {
                    // Seed a default plan first
                    System.out.println("Inserting default plan...");
                    stmt.execute("INSERT INTO subscription_plans(code, name, monthly_price, max_employees, max_storage_mb, trial_days, is_trial, validity_months, is_active) " +
                                 "VALUES('trial', 'Trial Plan', 0.0, 10, 512, 14, true, 1, true)");
                }

                // Get first plan id
                rs = stmt.executeQuery("SELECT id FROM subscription_plans ORDER BY id LIMIT 1");
                if (rs.next()) {
                    planId = rs.getLong(1);
                }
            } else {
                // Create table subscription_plans if not exists so we can seed and link it
                System.out.println("Creating subscription_plans table...");
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
                
                System.out.println("Inserting default plan...");
                stmt.execute("INSERT INTO subscription_plans(code, name, monthly_price, max_employees, max_storage_mb, trial_days, is_trial, validity_months, is_active) " +
                             "VALUES('trial', 'Trial Plan', 0.0, 10, 512, 14, true, 1, true)");
                
                ResultSet rs = stmt.executeQuery("SELECT id FROM subscription_plans ORDER BY id LIMIT 1");
                if (rs.next()) {
                    planId = rs.getLong(1);
                }
            }
            
            System.out.println("Using plan ID: " + planId + " for default mapping.");

            // Check if tenant_subscriptions table exists
            boolean subsTableExists = false;
            try {
                stmt.executeQuery("SELECT 1 FROM tenant_subscriptions LIMIT 1");
                subsTableExists = true;
            } catch (Exception e) {
                System.out.println("tenant_subscriptions table does not exist yet.");
            }

            if (subsTableExists) {
                // Add column subscription_plan_id if not exists
                System.out.println("Ensuring subscription_plan_id column exists on tenant_subscriptions...");
                try {
                    stmt.execute("ALTER TABLE tenant_subscriptions ADD COLUMN IF NOT EXISTS subscription_plan_id bigint");
                } catch (Exception e) {
                    System.out.println("Could not add column (it might already exist or table is empty): " + e.getMessage());
                }

                // Update existing rows
                System.out.println("Updating existing tenant_subscriptions rows...");
                int updatedRows = stmt.executeUpdate("UPDATE tenant_subscriptions SET subscription_plan_id = " + planId + " WHERE subscription_plan_id IS NULL");
                System.out.println("Updated " + updatedRows + " rows.");
            }

            System.out.println("Database migration completed successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
