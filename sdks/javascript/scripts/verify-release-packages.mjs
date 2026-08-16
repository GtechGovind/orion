import {readFile, stat} from "node:fs/promises";

const root = new URL("..", import.meta.url);
const packageJson = await readJson(new URL("package.json", root));
const targets = new Map([
  ["aarch64-apple-darwin", "darwin-arm64"],
  ["x86_64-unknown-linux-gnu", "linux-x64-gnu"],
  ["x86_64-pc-windows-msvc", "win32-x64-msvc"],
]);
const expectedDependencies = new Set();

for (const target of packageJson.napi.targets) {

  const platform = targets.get(target);
  if (!platform) {
    throw new Error(`release verifier does not recognize target ${target}`);
  }

  const directory = new URL(`npm/${platform}/`, root);
  const platformPackage = await readJson(new URL("package.json", directory));
  const expectedName = `${packageJson.name}-${platform}`;
  const expectedBinary = `${packageJson.napi.binaryName}.${platform}.node`;

  if (platformPackage.name !== expectedName || platformPackage.version !== packageJson.version) {
    throw new Error(`${platform} package identity does not match the root package`);
  }

  if (platformPackage.main !== expectedBinary) {
    throw new Error(`${platform} package does not load ${expectedBinary}`);
  }

  await stat(new URL(expectedBinary, directory));
  expectedDependencies.add(expectedName);

}

const optionalDependencies = packageJson.optionalDependencies ?? {};
for (const dependency of expectedDependencies) {
  if (optionalDependencies[dependency] !== packageJson.version) {
    throw new Error(`${dependency} must be an exact optional dependency at ${packageJson.version}`);
  }
}

const unexpected = Object.keys(optionalDependencies)
  .filter(dependency => dependency.startsWith(`${packageJson.name}-`))
  .filter(dependency => !expectedDependencies.has(dependency));

if (unexpected.length) {
  throw new Error(`root package contains stale platform dependencies: ${unexpected.join(", ")}`);
}

console.log(`verified ${expectedDependencies.size} release platform packages and root metadata`);

async function readJson(url) {
  return JSON.parse(await readFile(url, "utf8"));
}
