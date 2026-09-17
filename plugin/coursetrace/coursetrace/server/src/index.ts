import express, { type NextFunction, type Request, type Response } from "express";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { isInitializeRequest } from "@modelcontextprotocol/sdk/types.js";
import crypto from "node:crypto";
import { createMcpServer } from "./mcp.js";
import { RelayStore, type DeviceSnapshot } from "./store.js";

const dataDir = process.env.COURSETRACE_DATA_DIR || "./data";
const token = process.env.COURSETRACE_PAIRING_TOKEN;
const store = new RelayStore(dataDir);

function authorized(req: Request): boolean {
  if (!token) return process.argv.includes("--stdio");
  const supplied = req.header("authorization")?.replace(/^Bearer\s+/i, "") ?? req.header("x-coursetrace-token");
  return supplied === token;
}

if (process.argv.includes("--stdio")) {
  const server = createMcpServer(store);
  await server.connect(new StdioServerTransport());
} else {
  if (!token || token.length < 32) throw new Error("COURSETRACE_PAIRING_TOKEN must contain at least 32 characters");
  const app = express();
  app.disable("x-powered-by");
  app.use(express.json({ limit: "2mb" }));
  app.get("/health", (_req, res) => res.json({ ok: true, service: "coursetrace" }));
  app.use((req: Request, res: Response, next: NextFunction) => {
    if (req.path === "/health" || authorized(req)) return next();
    res.status(401).json({ error: "unauthorized" });
  });

  const transports = new Map<string, StreamableHTTPServerTransport>();
  app.post("/mcp", async (req, res) => {
    const sessionId = req.header("mcp-session-id");
    let transport = sessionId ? transports.get(sessionId) : undefined;
    if (!transport && isInitializeRequest(req.body)) {
      transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: () => crypto.randomUUID(),
        onsessioninitialized: (id) => { transports.set(id, transport!); },
      });
      transport.onclose = () => {
        if (transport?.sessionId) transports.delete(transport.sessionId);
      };
      await createMcpServer(store).connect(transport);
    } else if (!transport) {
      res.status(400).json({ error: "invalid or missing MCP session" });
      return;
    }
    await transport.handleRequest(req, res, req.body);
  });

  app.get("/mcp", async (req, res) => {
    const transport = transports.get(req.header("mcp-session-id") ?? "");
    if (!transport) { res.status(400).send("Invalid MCP session"); return; }
    await transport.handleRequest(req, res);
  });

  app.delete("/mcp", async (req, res) => {
    const transport = transports.get(req.header("mcp-session-id") ?? "");
    if (!transport) { res.status(400).send("Invalid MCP session"); return; }
    await transport.handleRequest(req, res);
  });

  app.put("/v1/device/snapshot", async (req, res) => {
    const snapshot = req.body as DeviceSnapshot;
    if (!snapshot.deviceId || !Array.isArray(snapshot.upcoming) || !Array.isArray(snapshot.materials) || !Array.isArray(snapshot.records)) {
      res.status(400).json({ error: "invalid snapshot" });
      return;
    }
    snapshot.updatedAt = new Date().toISOString();
    await store.replaceSnapshot(snapshot);
    res.json({ saved: true, updatedAt: snapshot.updatedAt });
  });

  app.get("/v1/device/changes", async (req, res) => {
    const after = Number(req.query.after ?? 0);
    res.json({ changes: await store.pending(Number.isFinite(after) ? after : 0) });
  });

  app.post("/v1/device/ack", async (req, res) => {
    const through = Number(req.body?.through);
    if (!Number.isInteger(through) || through < 0) {
      res.status(400).json({ error: "invalid sequence" });
      return;
    }
    await store.acknowledge(through);
    res.json({ acknowledged: through });
  });

  const port = Number(process.env.COURSETRACE_PORT || 8787);
  app.listen(port, "0.0.0.0", () => {
    process.stderr.write(`CourseTrace relay listening on port ${port}\n`);
  });
}
