import {spawnSync} from "node:child_process";
import {readdirSync} from "node:fs";
import {fileURLToPath} from "node:url";

const testDirectory = fileURLToPath(new URL("../test/", import.meta.url));
const testFiles = readdirSync(testDirectory, {recursive: true})
  .filter(path => typeof path === "string" && path.endsWith(".test.ts"))
  .sort()
  .map(path => fileURLToPath(new URL(path, new URL("../test/", import.meta.url))));

if (testFiles.length === 0) {
  throw new Error("no TypeScript test files were found");
}

const result = spawnSync(
  process.execPath,
  ["--import=tsx", "--test", ...testFiles],
  {stdio: "inherit"},
);

if (result.error) {
  throw result.error;
}

process.exit(result.status ?? 1);
