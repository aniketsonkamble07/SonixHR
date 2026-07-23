package com.sonixhr.service.common;

import com.sonixhr.entity.common.ApiHitLog;
import com.sonixhr.repository.common.ApiHitLogRepository;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for logging API hits asynchronously with connection pool protection.
 * Prevents connection pool exhaustion by using batching and circuit breaker patterns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiHitLogService {

    private final ApiHitLogRepository apiHitLogRepository;
    private final DataSource dataSource;

    @Value("${sonixhr.async.logging.enabled:true}")
    private boolean loggingEnabled;

    @Value("${sonixhr.async.logging.batch-size:50}")
    private int batchSize;

    @Value("${sonixhr.async.logging.flush-interval:5000}")
    private long flushInterval;

    @Value("${sonixhr.async.logging.queue-capacity:1000}")
    private int queueCapacity;

    @Value("${sonixhr.async.logging.fallback-to-file:true}")
    private boolean fallbackToFile;

    @Value("${sonixhr.async.logging.max-retries:3}")
    private int maxRetries;

    // Thread-safe queue for buffering logs
    private final BlockingQueue<ApiHitLog> logBuffer = new LinkedBlockingQueue<>();

    // Batch for bulk insert
    private final List<ApiHitLog> batch = new ArrayList<>();

    // Circuit breaker state
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitOpenedAt = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final long CIRCUIT_RESET_TIMEOUT = 60000; // 1 minute

    // Tenant logging settings
    private final Map<Long, Boolean> tenantLoggingSettings = new ConcurrentHashMap<>();

    // File logging fallback
    private static final Path LOG_FILE_PATH = Paths.get("logs", "api_hits_fallback.log");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Executor for async operations with limited thread pool
    private ExecutorService executorService;

    // Scheduled executor for periodic flush
    private ScheduledExecutorService scheduledExecutor;

    private volatile boolean running = true;

    @PostConstruct
    public void init() {
        // Create log directory if it doesn't exist
        try {
            Path logDir = LOG_FILE_PATH.getParent();
            if (logDir != null && !Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
        } catch (IOException e) {
            log.warn("Could not create log directory: {}", e.getMessage());
        }

        // Initialize executor with limited threads to prevent connection pool exhaustion
        executorService = new ThreadPoolExecutor(
                1,  // core pool size
                2,  // max pool size
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.DiscardPolicy() // Discard if queue is full
        );

        // Start scheduled flush
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutor.scheduleAtFixedRate(
                this::flushLogs,
                flushInterval,
                flushInterval,
                TimeUnit.MILLISECONDS
        );

        // Start health monitoring
        scheduledExecutor.scheduleAtFixedRate(
                this::monitorPoolHealth,
                30000, // 30 seconds initial delay
                30000, // 30 seconds interval
                TimeUnit.MILLISECONDS
        );

        log.info("ApiHitLogService initialized with batch size: {}, queue capacity: {}, flush interval: {}ms",
                batchSize, queueCapacity, flushInterval);
    }

    /**
     * Save API hit log asynchronously with connection pool protection
     */
    @Async("logExecutor")
    public void saveLog(ApiHitLog apiLog) {
        if (!loggingEnabled) {
            return;
        }

        if (apiLog == null) {
            log.warn("Attempted to save null API log");
            return;
        }

        // Check if logging is enabled for this tenant
        Long tenantId = apiLog.getTenantId();
        if (tenantId != null && !isApiLoggingEnabled(tenantId)) {
            log.debug("API logging disabled for tenant: {}", tenantId);
            return;
        }

        // Check circuit breaker
        if (circuitOpen.get()) {
            log.debug("Circuit breaker is open, logging to file fallback");
            if (fallbackToFile) {
                writeToFileFallback(apiLog);
            }
            return;
        }

        // Check connection pool health
        if (isConnectionPoolExhausted()) {
            log.warn("Connection pool exhausted, using fallback for API log: {}", apiLog.getRequestUri());
            if (fallbackToFile) {
                writeToFileFallback(apiLog);
            }
            return;
        }

        // Add to buffer with timeout
        try {
            boolean added = logBuffer.offer(apiLog, 100, TimeUnit.MILLISECONDS);
            if (!added) {
                log.warn("API log buffer full, using fallback for: {}", apiLog.getRequestUri());
                if (fallbackToFile) {
                    writeToFileFallback(apiLog);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while adding to log buffer", e);
            if (fallbackToFile) {
                writeToFileFallback(apiLog);
            }
        }
    }

    /**
     * Flush buffered logs to database in batches
     */
    @Scheduled(fixedDelayString = "${sonixhr.async.logging.flush-interval:5000}")
    public void flushLogs() {
        if (!running || !loggingEnabled) {
            return;
        }

        // Check if circuit breaker should be reset
        if (circuitOpen.get()) {
            if (System.currentTimeMillis() - circuitOpenedAt > CIRCUIT_RESET_TIMEOUT) {
                circuitOpen.set(false);
                consecutiveFailures.set(0);
                circuitOpenedAt = 0;
                log.info("Circuit breaker reset");
            } else {
                return;
            }
        }

        // Drain queue to batch
        int drained = logBuffer.drainTo(batch, batchSize);

        if (drained == 0 && batch.isEmpty()) {
            return;
        }

        // Add remaining from buffer if batch not full
        if (batch.size() < batchSize) {
            logBuffer.drainTo(batch, batchSize - batch.size());
        }

        if (batch.isEmpty()) {
            return;
        }

        // Create a copy for processing
        List<ApiHitLog> toSave = new ArrayList<>(batch);
        batch.clear();

        // Save with retry
        boolean success = saveBatchWithRetry(toSave);

        if (!success) {
            // If failed, put back in buffer for retry (if not exceeding capacity)
            if (logBuffer.size() < queueCapacity) {
                logBuffer.addAll(toSave);
                log.warn("Batch save failed, requeued {} logs for retry", toSave.size());
            } else {
                log.error("Batch save failed and buffer is full, {} logs lost", toSave.size());
                if (fallbackToFile) {
                    for (ApiHitLog logEntry : toSave) {
                        writeToFileFallback(logEntry);
                    }
                }
            }
        }
    }

    /**
     * Save batch with retry logic
     */
    private boolean saveBatchWithRetry(List<ApiHitLog> logs) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            try {
                attempts++;
                apiHitLogRepository.saveAll(logs);
                if (log.isDebugEnabled()) {
                    log.debug("Successfully saved {} API logs", logs.size());
                }
                consecutiveFailures.set(0);
                return true;
            } catch (Exception e) {
                lastException = e;
                log.warn("Failed to save API logs (attempt {}/{}): {}", attempts, maxRetries, e.getMessage());
                consecutiveFailures.incrementAndGet();

                // Check if circuit breaker should open
                if (consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
                    circuitOpen.set(true);
                    circuitOpenedAt = System.currentTimeMillis();
                    log.error("Circuit breaker opened due to {} consecutive failures", consecutiveFailures.get());
                    return false;
                }

                // Wait before retry
                try {
                    Thread.sleep(100 * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("Failed to save API logs after {} attempts", maxRetries, lastException);
        return false;
    }

    /**
     * Write log to file as fallback
     */
    private void writeToFileFallback(ApiHitLog apiLog) {
        if (!fallbackToFile) {
            return;
        }

        try {
            String logEntry = String.format("[%s] %s | %s | %s | %s | %s | %s%n",
                    LocalDateTime.now().format(DATE_FORMATTER),
                    apiLog.getEmployeeEmail() != null ? apiLog.getEmployeeEmail() : "anonymous",
                    apiLog.getRequestUri() != null ? apiLog.getRequestUri() : "unknown",
                    apiLog.getHttpMethod() != null ? apiLog.getHttpMethod() : "unknown",
                    apiLog.getIpAddress() != null ? apiLog.getIpAddress() : "unknown",
                    apiLog.getDeviceName() != null ? apiLog.getDeviceName() : "unknown",
                    apiLog.getTenantId() != null ? apiLog.getTenantId() : "no-tenant"
            );

            Files.write(LOG_FILE_PATH, logEntry.getBytes(), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write API log to file: {}", e.getMessage());
        }
    }

    // =====================================================
    // TENANT LOGGING MANAGEMENT - ADD THESE METHODS
    // =====================================================

    /**
     * Check if API logging is enabled for a tenant
     */
    public boolean isApiLoggingEnabled(Long tenantId) {
        if (tenantId == null) {
            return true; // Default to enabled for null tenant (platform logs)
        }
        return tenantLoggingSettings.getOrDefault(tenantId, true);
    }

    /**
     * Toggle API logging for a tenant
     */
    @Transactional
    public void toggleApiLogging(Long tenantId, boolean enabled) {
        log.info("Toggling API logging for tenant {} to {}", tenantId, enabled);
        tenantLoggingSettings.put(tenantId, enabled);
        // In a real implementation, you would persist this to a database
        // For now, we'll store it in memory
        log.info("API logging for tenant {} is now {}", tenantId, enabled ? "enabled" : "disabled");
    }

    /**
     * Get tenant logs with pagination
     */
    @Transactional(readOnly = true, timeout = 5)
    public Page<ApiHitLog> getTenantLogs(Long tenantId, Pageable pageable) {
        log.debug("Fetching API hit logs for tenant: {}", tenantId);
        try {
            return apiHitLogRepository.findByTenantId(tenantId, pageable);
        } catch (Exception e) {
            log.error("Failed to fetch API hit logs for tenant: {}", tenantId, e);
            throw new RuntimeException("Failed to fetch API hit logs", e);
        }
    }

    // =====================================================
    // CRUD OPERATIONS FOR CONTROLLER
    // =====================================================

    /**
     * Get all API hit logs with pagination
     */
    @Transactional(readOnly = true, timeout = 5)
    public Page<ApiHitLog> getAllLogs(Pageable pageable) {
        log.debug("Fetching all API hit logs with pagination: page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        try {
            return apiHitLogRepository.findAll(pageable);
        } catch (Exception e) {
            log.error("Failed to fetch API hit logs", e);
            throw new RuntimeException("Failed to fetch API hit logs", e);
        }
    }

    /**
     * Get API hit logs by employee ID with pagination
     */
    @Transactional(readOnly = true, timeout = 5)
    public Page<ApiHitLog> getLogsByEmployee(Long employeeId, Pageable pageable) {
        log.debug("Fetching API hit logs for employee: {}", employeeId);
        try {
            return apiHitLogRepository.findByEmployeeId(employeeId, pageable);
        } catch (Exception e) {
            log.error("Failed to fetch API hit logs for employee: {}", employeeId, e);
            throw new RuntimeException("Failed to fetch API hit logs", e);
        }
    }

    /**
     * Get API hit logs by request URI with pagination
     */
    @Transactional(readOnly = true, timeout = 5)
    public Page<ApiHitLog> getLogsByRequestUri(String requestUri, Pageable pageable) {
        log.debug("Fetching API hit logs for URI: {}", requestUri);
        try {
            return apiHitLogRepository.findByRequestUri(requestUri, pageable);
        } catch (Exception e) {
            log.error("Failed to fetch API hit logs for URI: {}", requestUri, e);
            throw new RuntimeException("Failed to fetch API hit logs", e);
        }
    }

    /**
     * Get API hit logs by IP address with pagination
     */
    @Transactional(readOnly = true, timeout = 5)
    public Page<ApiHitLog> getLogsByIpAddress(String ipAddress, Pageable pageable) {
        log.debug("Fetching API hit logs for IP: {}", ipAddress);
        try {
            return apiHitLogRepository.findByIpAddress(ipAddress, pageable);
        } catch (Exception e) {
            log.error("Failed to fetch API hit logs for IP: {}", ipAddress, e);
            throw new RuntimeException("Failed to fetch API hit logs", e);
        }
    }

    /**
     * Get API hit logs within a date range with pagination
     */
    @Transactional(readOnly = true, timeout = 5)
    public Page<ApiHitLog> getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        log.debug("Fetching API hit logs from {} to {}", startDate, endDate);
        try {
            return apiHitLogRepository.findByHitTimeBetween(startDate, endDate, pageable);
        } catch (Exception e) {
            log.error("Failed to fetch API hit logs by date range", e);
            throw new RuntimeException("Failed to fetch API hit logs", e);
        }
    }

    /**
     * Get API hit logs by HTTP method with pagination
     */
    @Transactional(readOnly = true, timeout = 5)
    public Page<ApiHitLog> getLogsByHttpMethod(String httpMethod, Pageable pageable) {
        log.debug("Fetching API hit logs for method: {}", httpMethod);
        try {
            return apiHitLogRepository.findByHttpMethod(httpMethod, pageable);
        } catch (Exception e) {
            log.error("Failed to fetch API hit logs for method: {}", httpMethod, e);
            throw new RuntimeException("Failed to fetch API hit logs", e);
        }
    }

    /**
     * Get API hit log by ID
     */
    @Transactional(readOnly = true, timeout = 3)
    public ApiHitLog getLogById(Long id) {
        log.debug("Fetching API hit log by ID: {}", id);
        try {
            return apiHitLogRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("API hit log not found with id: " + id));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch API hit log by ID: {}", id, e);
            throw new RuntimeException("Failed to fetch API hit log", e);
        }
    }

    /**
     * Get API hit log statistics
     */
    @Transactional(readOnly = true, timeout = 5)
    public Map<String, Object> getLogStatistics() {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("totalLogs", apiHitLogRepository.count());
            stats.put("bufferSize", logBuffer.size());
            stats.put("batchSize", batch.size());
            stats.put("circuitOpen", circuitOpen.get());
            stats.put("consecutiveFailures", consecutiveFailures.get());
            stats.put("loggingEnabled", loggingEnabled);
            stats.put("tenantSettings", tenantLoggingSettings);

            if (dataSource instanceof HikariDataSource) {
                try {
                    HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
                    stats.put("poolActive", pool.getActiveConnections());
                    stats.put("poolIdle", pool.getIdleConnections());
                    stats.put("poolTotal", pool.getTotalConnections());
                    stats.put("poolWaiting", pool.getThreadsAwaitingConnection());
                } catch (Exception e) {
                    log.debug("Could not get pool stats: {}", e.getMessage());
                }
            }

            // Add date range stats
            LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
            long last24Hours = apiHitLogRepository.countByHitTimeAfter(twentyFourHoursAgo);
            stats.put("last24Hours", last24Hours);

        } catch (Exception e) {
            log.error("Failed to get log statistics", e);
            stats.put("error", e.getMessage());
        }
        return stats;
    }

    /**
     * Delete old logs (scheduled cleanup)
     */
    @Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM every day
    @Transactional
    public void cleanupOldLogs() {
        log.info("Starting cleanup of old API hit logs");
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(1); // Keep last 1 month
            int deleted = apiHitLogRepository.deleteByHitTimeBefore(cutoffDate);
            log.info("Deleted {} old API hit logs", deleted);
        } catch (Exception e) {
            log.error("Failed to cleanup old API hit logs", e);
        }
    }

    /**
     * Check if connection pool is exhausted
     */
    private boolean isConnectionPoolExhausted() {
        if (dataSource instanceof HikariDataSource) {
            try {
                HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
                int active = pool.getActiveConnections();
                int total = pool.getTotalConnections();
                int waiting = pool.getThreadsAwaitingConnection();

                // If more than 80% of connections are active or there are threads waiting
                if (active > total * 0.8 || waiting > 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Connection pool stress: active={}, total={}, waiting={}", active, total, waiting);
                    }
                    return true;
                }
            } catch (Exception e) {
                log.debug("Could not check connection pool status: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Monitor connection pool health
     */
    private void monitorPoolHealth() {
        if (dataSource instanceof HikariDataSource) {
            try {
                HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
                int active = pool.getActiveConnections();
                int idle = pool.getIdleConnections();
                int total = pool.getTotalConnections();
                int waiting = pool.getThreadsAwaitingConnection();

                if (active > total * 0.7) {
                    log.warn("⚠️ Connection pool under high stress: active={}/{}, idle={}, waiting={}",
                            active, total, idle, waiting);
                }

                if (active > total * 0.9) {
                    log.error("🚨 Connection pool critical: active={}/{}, waiting={}",
                            active, total, waiting);
                    // Force circuit breaker to open
                    if (waiting > 0) {
                        circuitOpen.set(true);
                        circuitOpenedAt = System.currentTimeMillis();
                        log.warn("Circuit breaker forced open due to pool exhaustion");
                    }
                }

                // Update buffer size warning
                if (logBuffer.size() > queueCapacity * 0.8) {
                    log.warn("API log buffer filling up: {}/{}", logBuffer.size(), queueCapacity);
                }
            } catch (Exception e) {
                log.debug("Could not monitor connection pool: {}", e.getMessage());
            }
        }
    }

    /**
     * Get buffer status
     */
    public BufferStatus getBufferStatus() {
        return new BufferStatus(
                logBuffer.size(),
                batch.size(),
                circuitOpen.get(),
                consecutiveFailures.get(),
                isConnectionPoolExhausted()
        );
    }

    /**
     * Force flush all logs immediately
     */
    public void forceFlush() {
        log.info("Forcing flush of all API logs");
        flushLogs();
    }

    /**
     * Clear all pending logs
     */
    public void clearBuffer() {
        int cleared = logBuffer.size();
        logBuffer.clear();
        batch.clear();
        log.info("Cleared {} pending API logs", cleared);
    }

    /**
     * Enable/disable logging globally
     */
    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        log.info("API logging {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Reset circuit breaker
     */
    public void resetCircuitBreaker() {
        circuitOpen.set(false);
        consecutiveFailures.set(0);
        circuitOpenedAt = 0;
        log.info("Circuit breaker manually reset");
    }

    /**
     * Buffer status DTO
     */
    public record BufferStatus(
            int queueSize,
            int batchSize,
            boolean circuitOpen,
            int consecutiveFailures,
            boolean poolExhausted
    ) {}

    @PreDestroy
    public void destroy() {
        running = false;

        // Flush remaining logs
        flushLogs();

        // Shutdown executors
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("ApiHitLogService shut down");
    }
}