#!/usr/bin/env python3
"""Run a reproducible local MySQL-vs-Redis cache comparison.

The script uses only the Python standard library. It builds and starts the same
Spring Boot artifact twice: once with Spring caching disabled (direct MySQL
path), and once with Redis caching enabled. Each mode is warmed up and verified
before timed load begins. Raw request timings and a JSON summary are written to
performance/results/.
"""

import argparse
import csv
import datetime as dt
import http.client
import json
import math
import os
import platform
import signal
import socket
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from statistics import mean


ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
COMPOSE = ["docker", "compose", "--env-file", str(ROOT / ".env"), "-f", str(ROOT / "compose.local.yml")]


def command(args, *, cwd=ROOT, input_text=None, check=True):
    return subprocess.run(
        args,
        cwd=str(cwd),
        input=input_text,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=check,
    )


def load_dotenv(path):
    values = {}
    if not path.exists():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.removeprefix("export ").strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        values[key] = value
    return values


def compose_exec(service, executable, input_text=None):
    result = command(COMPOSE + ["exec", "-T", service, "sh", "-lc", executable], input_text=input_text)
    return result.stdout.strip()


def mysql(sql):
    return compose_exec(
        "mysql",
        'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D maimai_dx --batch --skip-column-names',
        input_text=sql,
    )


def redis(*args):
    quoted = " ".join("'" + str(arg).replace("'", "'\\''") + "'" for arg in args)
    return compose_exec("redis", "redis-cli --raw " + quoted)


def redis_stat(name):
    output = redis("INFO", "stats")
    for line in output.splitlines():
        if line.startswith(name + ":"):
            return int(line.split(":", 1)[1].strip())
    raise RuntimeError("Redis statistic not found: " + name)


def percentile(values, percent):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(percent / 100.0 * len(ordered)) - 1))
    return round(ordered[index], 2)


def request_once(host, port, path, connection=None, timeout=10):
    own_connection = connection is None
    conn = connection or http.client.HTTPConnection(host, port, timeout=timeout)
    started = time.perf_counter()
    status = 0
    error = ""
    body = b""
    try:
        conn.request("GET", path, headers={"Connection": "keep-alive"})
        response = conn.getresponse()
        status = response.status
        body = response.read()
        ok = 200 <= status < 300
    except Exception as exc:  # request failures are benchmark output, not script crashes
        ok = False
        error = type(exc).__name__ + ": " + str(exc)
        try:
            conn.close()
        except Exception:
            pass
        conn = None
    elapsed_ms = (time.perf_counter() - started) * 1000.0
    if own_connection and conn is not None:
        conn.close()
        conn = None
    return ok, status, round(elapsed_ms, 3), error, body, conn


def warm_up(host, port, path, count):
    conn = http.client.HTTPConnection(host, port, timeout=10)
    try:
        for index in range(count):
            ok, status, _, error, body, conn = request_once(host, port, path, conn)
            if not ok:
                raise RuntimeError("Warm-up request {} failed: HTTP {} {}".format(index + 1, status, error))
            if index == 0:
                payload = json.loads(body.decode("utf-8"))
                if not payload.get("data"):
                    raise RuntimeError("Target test song returned no chart data")
            if conn is None:
                conn = http.client.HTTPConnection(host, port, timeout=10)
    finally:
        if conn is not None:
            conn.close()


def run_load(host, port, path, scenario, concurrency, duration, csv_path):
    gate = threading.Event()
    shared_start = [0.0]

    def worker(worker_id):
        records = []
        conn = http.client.HTTPConnection(host, port, timeout=10)
        gate.wait()
        deadline = shared_start[0] + duration
        sequence = 0
        try:
            while time.perf_counter() < deadline:
                ok, status, elapsed_ms, error, _, conn = request_once(host, port, path, conn)
                sequence += 1
                records.append((worker_id, sequence, ok, status, elapsed_ms, error))
                if conn is None:
                    conn = http.client.HTTPConnection(host, port, timeout=10)
        finally:
            if conn is not None:
                conn.close()
        return records

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(worker, worker_id) for worker_id in range(concurrency)]
        shared_start[0] = time.perf_counter()
        started_utc = dt.datetime.now(dt.timezone.utc)
        gate.set()
        worker_records = [future.result() for future in futures]
        elapsed_seconds = time.perf_counter() - shared_start[0]

    records = [record for group in worker_records for record in group]
    latencies = [record[4] for record in records]
    success = sum(1 for record in records if record[2])
    failed = len(records) - success

    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["scenario", "concurrency", "worker", "sequence", "success", "status", "latency_ms", "error"])
        for worker_id, sequence, ok, status, elapsed_ms, error in records:
            writer.writerow([scenario, concurrency, worker_id, sequence, ok, status, elapsed_ms, error])

    return {
        "scenario": scenario,
        "concurrency": concurrency,
        "configuredDurationSeconds": duration,
        "measuredDurationSeconds": round(elapsed_seconds, 3),
        "startedAtUtc": started_utc.isoformat(),
        "totalRequests": len(records),
        "successfulRequests": success,
        "failedRequests": failed,
        "errorRatePercent": round((failed / len(records) * 100.0) if records else 0.0, 4),
        "avgMs": round(mean(latencies), 2) if latencies else 0.0,
        "p50Ms": percentile(latencies, 50),
        "p95Ms": percentile(latencies, 95),
        "p99Ms": percentile(latencies, 99),
        "successfulQps": round(success / elapsed_seconds, 2),
        "rawCsv": str(csv_path.relative_to(ROOT)),
    }


def wait_for_port(process, host, port, timeout_seconds=60):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Backend exited before becoming ready (exit code {})".format(process.returncode))
        try:
            with socket.create_connection((host, port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.25)
    raise RuntimeError("Backend did not open port {} within {} seconds".format(port, timeout_seconds))


def start_backend(jar_path, cache_type, port, output_dir, dotenv):
    env = os.environ.copy()
    env.update(dotenv)
    env.update({
        "SPRING_CACHE_TYPE": cache_type,
        "SERVER_PORT": str(port),
        "ASSISTANT_AI_ENABLED": "false",
        "LOGGING_LEVEL_COM_MAIMAI_MAIDX": "INFO",
        "LOGGING_LEVEL_COM_MAIMAI_MAIDX_REPOSITORY": "INFO",
    })
    log_path = output_dir / ("backend-{}.log".format("mysql" if cache_type == "none" else "redis"))
    log_handle = log_path.open("w", encoding="utf-8")
    process = subprocess.Popen(
        ["java", "-jar", str(jar_path), "--server.port={}".format(port)],
        cwd=str(BACKEND),
        env=env,
        stdout=log_handle,
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    try:
        wait_for_port(process, "127.0.0.1", port)
    except Exception:
        log_handle.close()
        raise
    return process, log_handle, log_path


def stop_backend(process, log_handle):
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=5)
    log_handle.close()


def prepare_general_log():
    mysql("SET GLOBAL general_log='OFF';\nSET GLOBAL log_output='TABLE';\nTRUNCATE TABLE mysql.general_log;\nSET GLOBAL general_log='ON';")


def finish_general_log_and_count():
    mysql("SET GLOBAL general_log='OFF';")
    value = mysql(
        "SELECT COUNT(*) FROM mysql.general_log "
        "WHERE command_type='Query' "
        "AND argument LIKE 'SELECT%' "
        "AND (argument LIKE '%FROM song %' OR argument LIKE '%FROM song_difficulty %');"
    )
    return int(value.strip())


def verify_mode(mode, host, port, path, cache_key):
    if mode == "redis_hit":
        before_hits = redis_stat("keyspace_hits")
    else:
        before_hits = None

    prepare_general_log()
    try:
        for _ in range(3):
            ok, status, _, error, _, _ = request_once(host, port, path)
            if not ok:
                raise RuntimeError("Verification request failed: HTTP {} {}".format(status, error))
    finally:
        song_query_count = finish_general_log_and_count()

    redis_hit_delta = redis_stat("keyspace_hits") - before_hits if before_hits is not None else None
    key_exists = redis("EXISTS", cache_key) == "1"
    ttl_seconds = int(redis("TTL", cache_key)) if key_exists else -2

    if mode == "mysql" and song_query_count < 6:
        raise RuntimeError("MySQL verification expected at least 6 song queries for 3 requests, got {}".format(song_query_count))
    if mode == "redis_hit" and song_query_count != 0:
        raise RuntimeError("Redis hit verification observed {} song queries".format(song_query_count))
    if mode == "redis_hit" and (not key_exists or redis_hit_delta < 3):
        raise RuntimeError("Redis hit verification failed: keyExists={}, hitDelta={}".format(key_exists, redis_hit_delta))

    return {
        "verificationRequests": 3,
        "songSqlQueries": song_query_count,
        "redisKey": cache_key,
        "redisKeyExists": key_exists,
        "redisTtlSeconds": ttl_seconds,
        "redisKeyspaceHitDelta": redis_hit_delta,
    }


def find_jar():
    jars = [path for path in (BACKEND / "target").glob("*.jar") if not path.name.endswith(".original")]
    if not jars:
        raise RuntimeError("No executable backend JAR found after build")
    return max(jars, key=lambda path: path.stat().st_mtime)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--song-id", default="s001")
    parser.add_argument("--concurrency", type=int, nargs="+", default=[10, 50])
    parser.add_argument("--duration", type=int, default=30)
    parser.add_argument("--warmup-requests", type=int, default=100)
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--skip-dependency-start", action="store_true")
    parser.add_argument("--output-dir", type=Path)
    args = parser.parse_args()

    if args.duration <= 0 or args.warmup_requests <= 0 or any(value <= 0 for value in args.concurrency):
        parser.error("duration, warmup requests and concurrency values must be positive")

    timestamp = dt.datetime.now().astimezone().strftime("%Y%m%d-%H%M%S")
    output_dir = args.output_dir or (ROOT / "performance" / "results" / timestamp)
    if not output_dir.is_absolute():
        output_dir = ROOT / output_dir
    output_dir.mkdir(parents=True, exist_ok=False)

    dotenv = load_dotenv(ROOT / ".env")
    if not args.skip_dependency_start:
        dependency_start = command(COMPOSE + ["up", "-d", "--wait"], check=False)
        if dependency_start.returncode != 0:
            sys.stderr.write(dependency_start.stdout + dependency_start.stderr)
            raise RuntimeError("Docker Compose dependencies did not become healthy")
    command(COMPOSE + ["ps", "--status", "running"])

    chart_count_text = mysql(
        "SELECT COUNT(*) FROM song;\n"
        "SELECT COUNT(*) FROM song_difficulty;\n"
        "SELECT COUNT(*) FROM song s JOIN song_difficulty d ON d.song_id=s.id WHERE s.song_id='{}';".format(
            args.song_id.replace("'", "''")
        )
    ).splitlines()
    if len(chart_count_text) < 3 or int(chart_count_text[2]) == 0:
        raise RuntimeError("Song {} does not exist or has no charts".format(args.song_id))

    if not args.skip_build:
        build = command(["mvn", "-q", "-DskipTests", "package"], cwd=BACKEND, check=False)
        if build.returncode != 0:
            sys.stderr.write(build.stdout + build.stderr)
            raise RuntimeError("Backend build failed")
    jar_path = find_jar()

    java_version = command(["java", "-version"], check=False)
    mysql_version = mysql("SELECT VERSION();")
    redis_version = ""
    for line in redis("INFO", "server").splitlines():
        if line.startswith("redis_version:"):
            redis_version = line.split(":", 1)[1].strip()
            break

    summary = {
        "generatedAt": dt.datetime.now().astimezone().isoformat(),
        "environment": {
            "operatingSystem": platform.platform(),
            "machine": platform.machine(),
            "python": platform.python_version(),
            "java": (java_version.stderr or java_version.stdout).splitlines()[0],
            "mysql": mysql_version,
            "redis": redis_version,
            "springBootStart": "java -jar backend/target/maidx-1.0.0-SNAPSHOT.jar",
            "mysqlLocation": "local Docker Compose container",
            "redisLocation": "local Docker Compose container",
        },
        "method": {
            "endpoint": "GET /api/charts/{}".format(args.song_id),
            "songId": args.song_id,
            "songCount": int(chart_count_text[0]),
            "chartCount": int(chart_count_text[1]),
            "targetChartCount": int(chart_count_text[2]),
            "warmupRequestsPerMode": args.warmup_requests,
            "concurrency": args.concurrency,
            "durationSecondsPerRun": args.duration,
            "qpsDefinition": "successful requests / measured run seconds",
            "latencyDefinition": "all completed HTTP request latencies, nearest-rank percentiles",
        },
        "verification": {},
        "results": [],
    }

    host = "127.0.0.1"
    path = "/api/charts/{}".format(args.song_id)
    cache_key = "maidx:songCharts::{}".format(args.song_id)

    try:
        for mode, cache_type in (("mysql", "none"), ("redis_hit", "redis")):
            redis("DEL", cache_key)
            process = log_handle = None
            try:
                process, log_handle, log_path = start_backend(jar_path, cache_type, args.port, output_dir, dotenv)
                if mode == "redis_hit":
                    ok, status, _, error, _, _ = request_once(host, args.port, path)
                    if not ok:
                        raise RuntimeError("Redis priming request failed: HTTP {} {}".format(status, error))
                    if redis("EXISTS", cache_key) != "1":
                        raise RuntimeError("Redis priming request did not create " + cache_key)

                warm_up(host, args.port, path, args.warmup_requests)
                summary["verification"][mode] = verify_mode(mode, host, args.port, path, cache_key)

                for concurrency in args.concurrency:
                    csv_path = output_dir / "{}-c{}.csv".format(mode, concurrency)
                    result = run_load(host, args.port, path, mode, concurrency, args.duration, csv_path)
                    summary["results"].append(result)
                    print(json.dumps(result, ensure_ascii=False), flush=True)
            finally:
                if process is not None:
                    stop_backend(process, log_handle)
    finally:
        try:
            mysql("SET GLOBAL general_log='OFF';")
        except Exception:
            pass

    summary_path = output_dir / "summary.json"
    summary_json = json.dumps(summary, ensure_ascii=False, indent=2) + "\n"
    summary_path.write_text(summary_json, encoding="utf-8")
    (ROOT / "performance" / "cache-comparison-latest.json").write_text(summary_json, encoding="utf-8")
    print("Summary: {}".format(summary_path))


if __name__ == "__main__":
    main()
