#!/usr/bin/env python3
import argparse
import asyncio
import shlex
import urllib.error
import urllib.parse
import urllib.request
from contextlib import AsyncExitStack, asynccontextmanager
from pathlib import Path


HOST = "127.0.0.1"
PORT = 18080
LUA = Path(__file__).with_name("entity.lua").resolve()


def request(port, key, body=None, version=None):
    url = f"http://{HOST}:{port}/v0/entity?id={urllib.parse.quote(key, safe='')}"
    headers = {"Content-Type": "application/octet-stream"}
    if version is not None:
        headers["X-Entity-Version"] = str(version)
    req = urllib.request.Request(url, data=body, headers=headers, method="GET" if body is None else "PUT")
    try:
        with urllib.request.urlopen(req, timeout=3) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        with error:
            return error.code, error.read()


@asynccontextmanager
async def server(jar, port, profile, log, *properties):
    with log.open("w") as output:
        process = await asyncio.create_subprocess_exec(
            "java", "-Xms128m", "-Xmx512m", "-XX:ActiveProcessorCount=4",
            "-XX:+EnableDynamicAgentLoading", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints",
            "-jar", str(jar), f"--spring.profiles.active={profile}", f"--server.port={port}",
            f"--server.address={HOST}", "--logging.level.root=WARN", *properties,
            stdout=output, stderr=asyncio.subprocess.STDOUT,
        )
        try:
            for _ in range(100):
                try:
                    status, _ = await asyncio.to_thread(request, port, "startup")
                    if status == 404:
                        break
                except (OSError, urllib.error.URLError):
                    pass
                if process.returncode is not None:
                    raise RuntimeError(f"Java exited. See {log}")
                await asyncio.sleep(0.1)
            else:
                raise RuntimeError(f"Server did not start. See {log}")
            yield process.pid
        finally:
            if process.returncode is None:
                process.terminate()
                try:
                    await asyncio.wait_for(process.wait(), timeout=10)
                except TimeoutError:
                    process.kill()
                    await process.wait()


async def delayed_replica(reader, writer):
    async def relay(source, destination):
        while data := await source.read(65536):
            destination.write(data)
            await destination.drain()

    async def relay_with_delay(source, destination):
        loop = asyncio.get_running_loop()
        chunks = asyncio.Queue()

        async def receive():
            while True:
                data = await source.read(65536)
                await chunks.put((loop.time() + 0.025, data))
                if not data:
                    return

        async with asyncio.TaskGroup() as tasks:
            tasks.create_task(receive())
            while True:
                deadline, data = await chunks.get()
                await asyncio.sleep(max(0, deadline - loop.time()))
                if not data:
                    return
                destination.write(data)
                await destination.drain()

    try:
        peer_reader, peer_writer = await asyncio.open_connection(HOST, PORT + 1)
        try:
            pipes = [asyncio.create_task(relay_with_delay(reader, peer_writer)),
                     asyncio.create_task(relay(peer_reader, writer))]
            _, pending = await asyncio.wait(pipes, return_when=asyncio.FIRST_COMPLETED)
            for task in pending:
                task.cancel()
            await asyncio.gather(*pipes, return_exceptions=True)
        finally:
            peer_writer.close()
    finally:
        writer.close()


async def run(command, log):
    print(log.name, flush=True)
    with log.open("w") as output:
        output.write(f"$ {shlex.join(map(str, command))}\n\n")
        output.flush()
        process = await asyncio.create_subprocess_exec(*map(str, command), stdout=output,
                                                       stderr=asyncio.subprocess.STDOUT)
        try:
            if await process.wait():
                raise RuntimeError(f"Command failed. See {log}")
        finally:
            if process.returncode is None:
                process.terminate()
                await process.wait()


async def load(args, method, rate, output, duration):
    await run([args.wrk, "-t4", "-c64", f"-d{duration}s", "-R", rate, "--latency", "--timeout", "3s",
               "-s", LUA, f"http://{HOST}:{PORT}", "--", method], output)


async def profile(args, mode, pid):
    recording = args.output / f"{mode}.jfr"
    log = args.output / f"{mode}-profile"
    await run([args.asprof, "start", "-e", "cpu", "--alloc", "64k", "--lock", "1us", "-f", recording, pid],
              log.with_suffix(".start.log"))
    try:
        await load(args, "MIXED", 1200, log.with_suffix(".wrk.txt"), args.seconds)
    finally:
        await run([args.asprof, "stop", "-f", recording, pid], log.with_suffix(".stop.log"))
    jfrconv = args.asprof.with_name("jfrconv")
    for event in ("cpu", "alloc", "lock"):
        html = args.output / f"{mode}-{event}.html"
        await run([jfrconv, f"--{event}", "--total", "--title", f"{mode} {event}", recording, html],
                  html.with_suffix(".log"))


async def benchmark(args):
    args.output.mkdir(parents=True, exist_ok=True)
    async with AsyncExitStack() as replicas:
        for port in range(PORT + 1, PORT + 4):
            await replicas.enter_async_context(server(args.async_jar, port, "replica",
                                                       args.output / f"replica-{port}.log"))
            for key in range(128):
                status, _ = await asyncio.to_thread(request, port, f"key-{key}", b"x" * 256, 1)
                if status != 201:
                    raise RuntimeError(f"Could not seed replica {port}: {status}")
        proxy = await asyncio.start_server(delayed_replica, HOST, PORT + 4)
        async with proxy:
            for condition in args.conditions:
                first = PORT + (1 if condition == "healthy" else 4)
                endpoints = ",".join(f"http://{HOST}:{port}" for port in (first, PORT + 2, PORT + 3))
                modes = [("sync", args.sync_jar), ("async", args.async_jar)]
                if condition == "healthy":
                    modes.reverse()
                for mode, jar in modes:
                    name = f"{condition}-{mode}"
                    async with server(jar, PORT, "coordinator", args.output / f"{name}.log",
                                      f"--replication.replicas={endpoints}") as pid:
                        key = f"{name} я +/&"
                        put = await asyncio.to_thread(request, PORT, key, b"check\x00")
                        get = await asyncio.to_thread(request, PORT, key)
                        if put != (201, b"") or get != (200, b"check\x00"):
                            raise RuntimeError(f"Smoke check failed for {name}: {put}, {get}")
                        rates = (1200,) if condition == "healthy" else (1200, 3500)
                        for rate in rates:
                            for method in ("GET", "PUT"):
                                output = args.output / f"{name}-{method.lower()}-{rate}.txt"
                                await load(args, method, rate, output, args.seconds)
                        if condition == "slow" and args.asprof is not None:
                            await profile(args, mode, pid)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("wrk", "async-jar", "sync-jar", "output"):
        parser.add_argument(f"--{name}", required=True, type=lambda value: Path(value).resolve())
    parser.add_argument("--asprof", type=lambda value: Path(value).resolve())
    parser.add_argument("--seconds", type=int, default=25)
    parser.add_argument("--conditions", nargs="+", choices=("healthy", "slow"), default=("healthy", "slow"))
    asyncio.run(benchmark(parser.parse_args()))
