import {spawnSync} from "node:child_process";
import {readFile} from "node:fs/promises";

const npm = process.platform === "win32" ? "npm.cmd" : "npm";
const packageJson = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
const packLifecycleScripts = ["prepack", "prepare", "postpack"];
const configuredPackLifecycleScripts = packLifecycleScripts.filter(name => packageJson.scripts?.[name]);

if (configuredPackLifecycleScripts.length) {
  throw new Error(
    `root package must not run lifecycle scripts during npm pack: ${configuredPackLifecycleScripts.join(", ")}`,
  );
}

const packed = spawnSync(
  npm,
  ["pack", "--dry-run", "--json", "--ignore-scripts"],
  {cwd: new URL("..", import.meta.url), encoding: "utf8"},
);

if (packed.status !== 0) {
  process.stderr.write(packed.stderr);
  process.exit(packed.status ?? 1);
}

const [manifest] = JSON.parse(packed.stdout);
const files = new Set(manifest.files.map(file => file.path));
const required = [
  "LICENSE-APACHE",
  "LICENSE-MIT",
  "README.md",
  "dist/index.d.ts",
  "dist/index.js",
  "dist/native.cjs",
];
const missing = required.filter(file => !files.has(file));
const nativeBinaries = [...files].filter(file => file.endsWith(".node"));

if (missing.length) {
  throw new Error(`root package is missing required files: ${missing.join(", ")}`);
}

if (nativeBinaries.length) {
  throw new Error(`root package must not embed platform binaries: ${nativeBinaries.join(", ")}`);
}

console.log(`root package dry-run verified ${files.size} files without pack lifecycle scripts`);
