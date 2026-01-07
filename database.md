# 📊 DATABASE MONITORING - HƯỚNG DẪN NHANH

> **Mục tiêu:** Hiểu cách hệ thống giám sát database real-time (metrics + Query Editor tracking)

---

## 1️⃣ KIẾN TRÚC TỔNG QUAN

```
┌─────────────────────────────────────────────────────────────────┐
│                         FRONTEND                                 │
│  DatabaseMetricsChart.tsx → EventSource SSE                     │
└─────────────────────┬───────────────────────────────────────────┘
                      │ SSE Stream (5s interval)
                      ↓
┌─────────────────────────────────────────────────────────────────┐
│                         BACKEND                                  │
│  DatabaseMonitoringController → SseEmitter                      │
│           ↓                                                      │
│  DatabaseMonitoringService.getDatabaseMetrics()                 │
│           ↓                                                      │
│  ┌─────────────────┬──────────────────┬────────────────────┐   │
│  │ Docker API      │ Database Queries │ Query Editor       │   │
│  │ (Container)     │ (SQL/NoSQL)      │ (Tracker)          │   │
│  └─────────────────┴──────────────────┴────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
         │                    │                      │
         ↓                    ↓                      ↓
    Container Stats    pg_stat_activity    ConcurrentHashMap
    (CPU, Memory)      (Connections, QPS)  (Sliding Window 60s)
```

---

## 2️⃣ LUỒNG HOẠT ĐỘNG CHÍNH

### **A. Metrics Streaming Flow**

```
1. User mở database detail page
   → Frontend tạo EventSource: /api/v1/databases/{id}/metrics/stream

2. Backend tạo SseEmitter + async thread (ExecutorService)
   → Loop mỗi 5 giây:
     a. DockerService.getContainerMetricByContainerId() → CPU, Memory, Network
     b. getDatabaseSpecificMetrics() → SQL queries để lấy connections, QPS, cache
     c. enhanceWithQueryEditorMetrics() → QPS, avg time từ sliding window
     d. Build DatabaseMetricsDTO
     e. emitter.send(event: "database-metrics", data: metrics)

3. Frontend nhận event "database-metrics"
   → Parse JSON → Update state → Update chart (giữ max 20 points)
```

**⏱️ Polling Intervals:**
- SSE stream: **5 giây/lần**
- Logs stream: **3 giây/lần**
- Sliding window: **60 giây** (calculate QPS/avg time)

---

### **B. Query Editor Tracking Flow**

```
1. User execute query trong Query Editor
   → DatabaseQueryService.executeQuery()

2. Record start time → Execute query → Calculate execution time

3. DatabaseQueryMetricsTracker.recordQuery(databaseId, executionTime, queryType)
   ├─ AtomicInteger.incrementAndGet() → totalQueries++
   ├─ AtomicLong.addAndGet() → totalExecutionTimeMs += time
   ├─ ConcurrentLinkedDeque.addLast() → Lưu timestamp vào sliding window
   └─ Cleanup old entries (>60s)

4. DatabaseMonitoringService.enhanceWithQueryEditorMetrics()
   → queryMetricsTracker.getMetrics(databaseId)
   → getQueriesPerSecond() → Count queries trong 60s window / 60
   → getAverageQueryTimeMs() → totalExecutionTimeMs / totalQueries

5. Override database engine metrics với Query Editor metrics
   → User thấy performance của queries họ VỪA MỚI chạy
```

---

## 3️⃣ METRICS THU THẬP (3 NGUỒN)

### **Nguồn 1: Docker Container Stats**

**File:** `DockerService.java` → `getContainerMetricByContainerId()`

**Metrics:**
- `cpuUsage` (%) - CPU usage percentage
- `memoryUsageBytes`, `memoryLimit` - Memory stats
- `networkRxBytes`, `networkTxBytes` - Network I/O
- `diskReadBytes`, `diskWriteBytes` - Disk I/O
- `uptimeSeconds` - Container uptime

**Cách lấy:**
```java
docker.statsCmd(containerId)
    .withStream(true)
    .exec(new ResultCallback<Statistics>() {
        // Parse statistics object
    });
```

---

### **Nguồn 2: Database-Specific Queries**

**File:** `DatabaseMonitoringService.java`

#### **PostgreSQL** (via JDBC)
```sql
-- Active connections
SELECT count(*) FROM pg_stat_activity WHERE state = 'active';

-- Max connections
SELECT setting FROM pg_settings WHERE name = 'max_connections';

-- QPS (Queries Per Second)
SELECT (xact_commit + xact_rollback) / extract(epoch from now() - stats_reset) 
FROM pg_stat_database WHERE datname = 'mydb';

-- Cache hit ratio
SELECT round((sum(heap_blks_hit) / (sum(heap_blks_hit) + sum(heap_blks_read)) * 100), 2)
FROM pg_statio_user_tables;

-- Slow queries (cần pg_stat_statements extension)
SELECT count(*) FROM pg_stat_statements WHERE mean_exec_time > 1000;

-- Database size
SELECT pg_database_size('mydb');
```

#### **MySQL** (via JDBC)
```java
// SHOW GLOBAL STATUS
Threads_connected → activeConnections
Max_connections → maxConnections
Questions / Uptime → QPS
Slow_queries → slowQueries
Innodb_buffer_pool_read_requests vs Innodb_buffer_pool_reads → cacheHitRatio
```

#### **MongoDB** (via MongoDB Java Driver)
```java
// serverStatus command
db.runCommand({serverStatus: 1})
  → connections.current, connections.totalCreated
  → opcounters: {query, insert, update, delete} → totalQueries
  → uptime → QPS calculation

// dbStats command
db.runCommand({dbStats: 1})
  → dataSize, indexSize, collections

// system.profile collection
db.getCollection("system.profile").countDocuments({millis: {$gt: 100}})
  → slowQueries
```

#### **Redis** (via Jedis)
```java
jedis.info() → Parse key-value pairs
  → connected_clients, maxclients
  → total_commands_processed → totalQueries
  → keyspace_hits, keyspace_misses → cacheHitRatio

jedis.dbSize() → Number of keys
jedis.slowlogLen() → Slow queries count
```

---

### **Nguồn 3: Query Editor Metrics (Real-time)**

**File:** `DatabaseQueryMetricsTracker.java`

**Thread-safe tracking với:**
```java
// Per-database metrics storage
ConcurrentHashMap<UUID, DatabaseQueryMetrics> metricsMap

// Atomic counters
AtomicInteger totalQueries
AtomicInteger selectQueries
AtomicInteger modifyQueries
AtomicLong totalExecutionTimeMs

// Sliding window (60 seconds)
ConcurrentLinkedDeque<QueryTimestamp> queryTimestamps

// Cached calculations (volatile for visibility)
volatile long lastCalculatedQPS
volatile long lastCalculatedAvgTime
```

**Metrics được track:**
- `totalQueries` - Tổng queries từ Query Editor
- `selectQueries` - Số SELECT queries
- `modifyQueries` - Số INSERT/UPDATE/DELETE queries
- `QPS` - Queries trong 60s / 60
- `avgQueryTimeMs` - Avg execution time trong 60s

**Tại sao override database engine metrics?**
> Database engine metrics (như `pg_stat_database`) tính từ lúc database start, không real-time.
> Query Editor metrics (sliding window 60s) phản ánh **immediate performance** của queries user VỪA chạy.

---

## 4️⃣ CODE LOCATIONS QUAN TRỌNG

### **Backend**

| File | Function | Purpose |
|------|----------|---------|
| `DatabaseMonitoringController.java` | `streamDatabaseMetrics()` | SSE endpoint, loop 5s |
| `DatabaseMonitoringService.java` | `getDatabaseMetrics()` | Tổng hợp 3 nguồn metrics |
| `DatabaseMonitoringService.java` | `getPostgreSQLMetrics()` | Execute SQL queries |
| `DatabaseMonitoringService.java` | `enhanceWithQueryEditorMetrics()` | Override với tracker metrics |
| `DatabaseQueryMetricsTracker.java` | `recordQuery()` | Ghi query execution |
| `DatabaseQueryMetricsTracker.java` | `getQueriesPerSecond()` | Calculate QPS từ sliding window |
| `DatabaseQueryService.java` | `executeQuery()` | Gọi `recordQuery()` |
| `DockerService.java` | `getContainerMetricByContainerId()` | Docker Stats API |

### **Frontend**

| File | Component | Purpose |
|------|-----------|---------|
| `DatabaseMetricsChart.tsx` | `useEffect()` | Tạo EventSource SSE |
| `DatabaseMetricsChart.tsx` | `addEventListener("database-metrics")` | Parse + update chart |
| `database-monitoring.service.ts` | `createMetricsStream()` | Tạo EventSource connection |
| `database-monitoring.service.ts` | `DatabaseMetrics` interface | Type definition |

---

## 5️⃣ KEY TECHNOLOGIES

### **Backend**
- **JDBC** - Execute SQL queries (PostgreSQL, MySQL)
- **MongoDB Java Driver** - `runCommand()` for stats
- **Jedis** - Redis client (`info()`, `dbSize()`)
- **docker-java** - Container statistics API
- **SseEmitter** - Server-Sent Events streaming
- **ExecutorService** - Async SSE threads (`newCachedThreadPool()`)
- **ConcurrentHashMap** - Thread-safe metrics storage
- **AtomicInteger/AtomicLong** - Lock-free counters
- **ConcurrentLinkedDeque** - Sliding window queue

### **Frontend**
- **EventSource** - SSE client (browser API)
- **Recharts** - Charts (LineChart, AreaChart)
- **React hooks** - `useState`, `useEffect`, `useRef`

---

## 6️⃣ CÁC METRIC QUAN TRỌNG

### **Connection Metrics**
- `activeConnections` / `maxConnections` → % pool usage
- `connectionPoolUsage` = (active / max) × 100
- `connectionErrors` → Failed connections

### **Performance Metrics**
- `queriesPerSecond` (QPS) → **Override bởi Query Editor metrics**
- `avgQueryTime` → **Override bởi Query Editor metrics**
- `slowQueries` → Queries > 1s execution time
- `cacheHitRatio` → % cache hits (higher = better)

### **Resource Metrics**
- `cpuUsage` → Container CPU %
- `memoryUsagePercent` → Container memory %
- `networkRxBytesPerSec`, `networkTxBytesPerSec` → I/O throughput

### **Storage Metrics**
- `databaseSizeBytes` → Total DB size
- `totalTableSize`, `totalIndexSize` → Table/Index sizes
- `storageUsagePercent` → % of allocated storage

### **Query Editor Specific**
- `queryEditorQueriesTotal` → Total từ Query Editor
- `queryEditorQPS` → Real-time QPS (60s window)
- `queryEditorAvgTimeMs` → Avg time (60s window)
- `queryEditorSelectQueries`, `queryEditorModifyQueries` → Breakdown

---

## 7️⃣ THREAD-SAFETY MECHANISMS

### **Problem:** Multiple users execute queries simultaneously
### **Solution:** Thread-safe data structures

```java
// ✅ Thread-safe storage
ConcurrentHashMap<UUID, DatabaseQueryMetrics> metricsMap;

// ✅ Atomic counters (lock-free)
AtomicInteger totalQueries;  // incrementAndGet()
AtomicLong totalExecutionTimeMs;  // addAndGet(time)

// ✅ Concurrent queue (sliding window)
ConcurrentLinkedDeque<QueryTimestamp> queryTimestamps;

// ✅ Volatile for visibility
volatile long lastCalculatedQPS;
volatile long lastCalculatedAvgTime;

// ✅ Synchronized cleanup method
private synchronized void cleanupOldEntries(Deque<QueryTimestamp> timestamps)
```

**Tại sao cần thread-safe?**
> Multiple users có thể execute queries đồng thời trên cùng database.
> Không có lock → race conditions → incorrect metrics.
> `Concurrent*` + `Atomic*` → Performance cao mà vẫn đảm bảo correctness.

---

## 8️⃣ Q&A NHANH

### **Q1: Tại sao dùng SSE thay vì polling?**
**A:** SSE giữ 1 connection mở, server push data khi có. Polling = N requests/giây → overhead cao, latency cao. SSE = real-time, efficient, auto-reconnect.

### **Q2: Tại sao 5 giây/lần thay vì 1 giây?**
**A:** Balance giữa real-time và database load. Database queries (SQL) tốn tài nguyên hơn Docker API. 5s = reasonable refresh rate mà không overload database.

### **Q3: Tại sao sliding window 60 giây?**
**A:** 60s = đủ dài để smooth out spikes, đủ ngắn để reflect recent performance. QPS calculation: count queries trong 60s / 60 = avg QPS per second.

### **Q4: Làm sao handle khi user không execute query nào?**
**A:** Query Editor metrics sẽ là 0. Backend vẫn gửi metrics từ database engine (pg_stat_database). Nếu không có queries, QPS từ database engine cũng là 0 → consistent.

### **Q5: Tại sao mỗi database type có cách khác nhau?**
**A:** Mỗi database có native monitoring tools:
- PostgreSQL: `pg_stat_*` system views
- MySQL: `SHOW STATUS`, `INFORMATION_SCHEMA`
- MongoDB: `serverStatus`, `dbStats` commands
- Redis: `INFO` command, `SLOWLOG`

Không có cách chung, phải adapt theo từng database.

### **Q6: ExecutorService dùng thread pool nào?**
**A:** `Executors.newCachedThreadPool()` - Tạo threads động theo demand. Mỗi SSE connection = 1 thread. Auto cleanup idle threads sau 60s → Scale với số users.

### **Q7: Max 20 chart points để làm gì?**
**A:** Prevent memory leak. Mỗi 5s = 1 point → 20 points = 100s history. Đủ để user thấy trend, không làm browser chậm.

### **Q8: Tại sao override database engine metrics?**
**A:** Database engine metrics tính từ lúc start (cumulative). Query Editor metrics track queries user VỪA chạy (last 60s) → More relevant cho user monitoring.

---

## 9️⃣ CODE EXAMPLE: End-to-End

### **Step 1: User execute query trong Query Editor**

```java
// DatabaseQueryService.java
public QueryResult executeQuery(UUID databaseId, String query, String userId) {
    long startTime = System.currentTimeMillis();
    
    // Execute query
    ResultSet rs = statement.executeQuery(query);
    QueryResult result = buildResult(rs);
    
    long executionTime = System.currentTimeMillis() - startTime;
    String queryType = detectQueryType(query); // "SELECT", "INSERT", etc.
    
    // ⭐ Record metrics
    metricsTracker.recordQuery(databaseId, executionTime, queryType);
    
    return result;
}
```

### **Step 2: DatabaseQueryMetricsTracker.recordQuery()**

```java
public void recordQuery(UUID databaseId, long executionTimeMs, String queryType) {
    DatabaseQueryMetrics metrics = metricsMap.computeIfAbsent(
        databaseId, k -> new DatabaseQueryMetrics()
    );
    
    // ⭐ Atomic increment
    metrics.totalQueries.incrementAndGet();
    metrics.totalExecutionTimeMs.addAndGet(executionTimeMs);
    
    // ⭐ Track query type
    if ("SELECT".equals(queryType)) {
        metrics.selectQueries.incrementAndGet();
    } else {
        metrics.modifyQueries.incrementAndGet();
    }
    
    // ⭐ Add to sliding window
    metrics.queryTimestamps.addLast(
        new QueryTimestamp(System.currentTimeMillis(), executionTimeMs)
    );
    
    // ⭐ Cleanup old entries (>60s)
    cleanupOldEntries(metrics.queryTimestamps);
}
```

### **Step 3: Calculate QPS (called by monitoring service)**

```java
public double getQueriesPerSecond(UUID databaseId) {
    DatabaseQueryMetrics metrics = metricsMap.get(databaseId);
    if (metrics == null) return 0.0;
    
    cleanupOldEntries(metrics.queryTimestamps);
    
    // ⭐ Count queries trong 60s window
    int queriesInWindow = metrics.queryTimestamps.size();
    double qps = queriesInWindow / 60.0;
    
    metrics.lastCalculatedQPS = Math.round(qps * 100.0) / 100.0;
    return metrics.lastCalculatedQPS;
}
```

### **Step 4: DatabaseMonitoringService enhances metrics**

```java
private void enhanceWithQueryEditorMetrics(UUID databaseId, Map<String, Object> metrics) {
    DatabaseQueryMetrics queryMetrics = queryMetricsTracker.getMetrics(databaseId);
    
    double editorQPS = queryMetrics.getQueriesPerSecond();
    double editorAvgTime = queryMetrics.getAverageQueryTimeMs();
    
    // ⭐ Override database engine metrics
    if (editorQPS > 0) {
        metrics.put("queriesPerSecond", editorQPS);
        metrics.put("avgQueryTime", editorAvgTime);
    }
    
    // ⭐ Add Query Editor specific fields
    metrics.put("queryEditorQueriesTotal", queryMetrics.totalQueries.get());
    metrics.put("queryEditorQPS", editorQPS);
    metrics.put("queryEditorAvgTimeMs", editorAvgTime);
}
```

### **Step 5: SSE streams to frontend**

```java
// DatabaseMonitoringController.java
executorService.execute(() -> {
    while (true) {
        // ⭐ Get all metrics (Docker + DB + Query Editor)
        DatabaseMetricsDTO metrics = monitoringService.getDatabaseMetrics(id, userId);
        
        // ⭐ Send via SSE
        emitter.send(SseEmitter.event()
            .name("database-metrics")
            .data(metrics));
        
        Thread.sleep(5000);
    }
});
```

### **Step 6: Frontend updates chart**

```typescript
// DatabaseMetricsChart.tsx
eventSource.addEventListener("database-metrics", (event: MessageEvent) => {
    const data: DatabaseMetrics = JSON.parse(event.data);
    
    // ⭐ Update chart data
    setChartData(prev => {
        const newData = [...prev, {
            time: new Date(data.timestamp).toLocaleTimeString(),
            qps: data.queriesPerSecond, // ← Query Editor QPS nếu có
            avgTime: data.avgQueryTime,
            connections: data.activeConnections,
        }];
        return newData.slice(-20); // Keep last 20 points
    });
});
```

---

## 🔟 CHECKLIST BẢO VỆ

### **✅ Phải biết:**
1. **3 nguồn metrics:** Docker API, Database queries, Query Editor tracker
2. **SSE polling:** 5s interval, SseEmitter, EventSource
3. **Thread-safe tracking:** ConcurrentHashMap, AtomicInteger, sliding window 60s
4. **Database-specific queries:** pg_stat_activity, SHOW STATUS, serverStatus, INFO
5. **Override mechanism:** Query Editor metrics override database engine metrics
6. **Thread pool:** newCachedThreadPool() cho SSE connections

### **✅ Có thể giải thích:**
- Tại sao 5s thay vì 1s? → Balance performance vs real-time
- Tại sao sliding window 60s? → Smooth QPS calculation
- Tại sao override metrics? → Real-time user query performance
- Tại sao thread-safe? → Multiple concurrent users
- Tại sao SSE thay vì WebSocket? → Unidirectional, simpler

### **✅ Demo flow:**
1. User execute query → recordQuery() → Atomic increment
2. Sliding window cleanup → Count queries trong 60s
3. SSE loop 5s → getDatabaseMetrics() → 3 nguồn
4. enhanceWithQueryEditorMetrics() → Override QPS
5. Frontend receive event → Update chart → Max 20 points

---

## 🎯 TÓM TẮT SIÊU NHANH (30 GIÂY)

**Database Monitoring = 3 metrics nguồn:**
1. **Docker API** → Container stats (CPU, memory, I/O)
2. **Database queries** → SQL/NoSQL commands (connections, QPS, cache)
3. **Query Editor tracker** → Thread-safe sliding window 60s (real-time QPS)

**Streaming:** SSE 5s → Frontend EventSource → Charts (max 20 points)

**Thread-safe:** ConcurrentHashMap + AtomicInteger + ConcurrentLinkedDeque

**Override:** Query Editor metrics > Database engine metrics (more real-time)

**Tech stack:** JDBC/MongoDB Driver/Jedis + docker-java + SseEmitter + Recharts

---

**✨ Chúc bạn bảo vệ thành công!** 🚀
