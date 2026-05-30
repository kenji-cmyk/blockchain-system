import { readFile } from "node:fs/promises";
import { relative } from "node:path";
import { cwd, exit } from "node:process";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const allowlistedFixtures = new Set([
  "backend/src/main/resources/application-example.properties",
  "backend/src/main/resources/application-docker.properties",
  "backend/src/main/resources/application-local.properties",
  "backend/src/main/resources/application-test.properties",
  "backend/src/test/java/com/kna/backend/SecurityPhase8Tests.java",
  "backend/README.md",
  "README.md"
]);
const suspiciousPatterns = [
  /-----BEGIN (RSA |EC |OPENSSH |)PRIVATE KEY-----/,
  /\bAKIA[0-9A-Z]{16}\b/,
  /\bghp_[A-Za-z0-9_]{36,}\b/,
  /\b(xox[baprs]-[A-Za-z0-9-]{20,})\b/,
  /\b(?:password|passwd|secret|api[_-]?key|token)\s*[:=]\s*["']?(?!\s*$|operator-token\b|read-only-token\b|test-token\b|demo\b|local\b|changeme\b)[A-Za-z0-9_./+=-]{12,}/i
];

const { stdout } = await execFileAsync("git", ["ls-files", "--cached", "--others", "--exclude-standard"], { cwd: cwd() });
const files = stdout
  .split(/\r?\n/)
  .filter(Boolean)
  .filter((file) => !file.startsWith("backend/src/main/resources/static/assets/"))
  .filter((file) => !file.endsWith("package-lock.json"));

const findings = [];
for (const file of files) {
  const normalized = file.replaceAll("\\", "/");
  if (allowlistedFixtures.has(normalized)) {
    continue;
  }
  const content = await readFile(file, "utf8").catch(() => null);
  if (content === null) {
    continue;
  }
  for (const pattern of suspiciousPatterns) {
    if (pattern.test(content)) {
      findings.push(relative(cwd(), file));
      break;
    }
  }
}

if (findings.length > 0) {
  console.error("Potential hardcoded secrets found:");
  for (const finding of findings) {
    console.error(`- ${finding}`);
  }
  exit(1);
}

console.log(`Secret scan passed for ${files.length} tracked files.`);
