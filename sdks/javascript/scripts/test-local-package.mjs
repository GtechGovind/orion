import {execFileSync} from "node:child_process";
import {cp, mkdir, mkdtemp, readFile, writeFile} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join, resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {npmCliArguments} from "./npm-process.mjs";

const platform = platformSuffix();
const rustTarget = rustTargetFor(platform);
const root = new URL("..", import.meta.url);
const sourcePackage = JSON.parse(await readFile(new URL("package.json", root), "utf8"));
const binary = `${sourcePackage.napi.binaryName}.${platform}.node`;
const binaryUrl = new URL(`dist/${binary}`, root);
const workspace = await mkdtemp(join(tmpdir(), "orion-npm-test-"));
const rootStage = join(workspace, "root");
const requestedOutput = outputDirectoryArgument(process.argv.slice(2));
const tarballs = requestedOutput === undefined
  ? join(workspace, "tarballs")
  : resolve(fileURLToPath(root), requestedOutput);
const consumer = join(workspace, "consumer");
const platformName = `${sourcePackage.name}-${platform}`;
const napiCli = fileURLToPath(new URL("node_modules/@napi-rs/cli/dist/cli.js", root));

await mkdir(rootStage);
await mkdir(tarballs, {recursive: true});
await mkdir(consumer);
await cp(new URL("dist", root), join(rootStage, "dist"), {
  recursive: true,
  filter: source => !source.endsWith(".node"),
});
await cp(new URL("README.md", root), join(rootStage, "README.md"));
await cp(new URL("LICENSE-APACHE", root), join(rootStage, "LICENSE-APACHE"));
await cp(new URL("LICENSE-MIT", root), join(rootStage, "LICENSE-MIT"));

const rootPackage = {
  ...sourcePackage,
  scripts: {},
  napi: {
    ...sourcePackage.napi,
    targets: [rustTarget],
  },
};

await writeJson(join(rootStage, "package.json"), rootPackage);
run(process.execPath, [
  napiCli,
  "create-npm-dirs",
  "--cwd",
  rootStage,
  "--npm-dir",
  "npm",
], rootStage);

const platformStage = join(rootStage, "npm", platform);
await cp(binaryUrl, join(platformStage, binary));
run(process.execPath, [
  napiCli,
  "pre-publish",
  "--cwd",
  rootStage,
  "--npm-dir",
  "npm",
  "--tag-style",
  "npm",
  "--skip-optional-publish",
  "--no-gh-release",
], rootStage);

const preparedRootPackage = JSON.parse(await readFile(join(rootStage, "package.json"), "utf8"));
const optionalDependencies = preparedRootPackage.optionalDependencies ?? {};

if (optionalDependencies[platformName] !== sourcePackage.version) {
  throw new Error(`${platformName} was not generated as an exact optional dependency`);
}

pack(platformStage, tarballs);
pack(rootStage, tarballs);

const platformTarball = join(tarballs, `${packageFileStem(platformName)}-${sourcePackage.version}.tgz`);
const rootTarball = join(tarballs, `${packageFileStem(sourcePackage.name)}-${sourcePackage.version}.tgz`);
await writeJson(join(consumer, "package.json"), {name: "orion-external-smoke", private: true, type: "module"});
run("npm", ["install", "--ignore-scripts", platformTarball, rootTarball], consumer);
await writeFile(join(consumer, "smoke.mjs"), smokeSource(), "utf8");
run(process.execPath, ["smoke.mjs"], consumer);

console.log(
  `external package smoke test passed on ${process.version} for ${platformName} in ${consumer}`,
);

function pack(directory, destination) {

  run("npm", ["pack", "--ignore-scripts", "--pack-destination", destination], directory);

}

function run(command, arguments_, cwd) {

  const executable = command === "npm" ? process.execPath : command;
  const executableArguments = command === "npm" ? npmCliArguments(arguments_) : arguments_;

  execFileSync(executable, executableArguments, {
    cwd,
    stdio: "inherit",
  });

}

function packageFileStem(name) {

  return name.replace(/^@/, "").replace("/", "-");

}

function platformSuffix() {

  if (process.platform === "darwin" && process.arch === "arm64") return "darwin-arm64";
  if (process.platform === "linux" && process.arch === "x64") return "linux-x64-gnu";
  if (process.platform === "win32" && process.arch === "x64") return "win32-x64-msvc";

  throw new Error(`unsupported local package-test platform ${process.platform}-${process.arch}`);

}

function outputDirectoryArgument(arguments_) {

  const optionIndex = arguments_.indexOf("--output-dir");
  if (optionIndex === -1) return undefined;

  const value = arguments_[optionIndex + 1];
  if (!value || value.startsWith("--")) {
    throw new Error("--output-dir requires a directory path");
  }

  return value;

}

function rustTargetFor(platformName) {

  const targets = {
    "darwin-arm64": "aarch64-apple-darwin",
    "linux-x64-gnu": "x86_64-unknown-linux-gnu",
    "win32-x64-msvc": "x86_64-pc-windows-msvc",
  };

  return targets[platformName];

}

function smokeSource() {

  return `
import {Agent, OpenAI, z} from "@orion-runtime/sdk";

globalThis.fetch = async () => new Response(JSON.stringify({
  choices: [{message: {content: '{"answer":"ok"}'}, finish_reason: "stop"}],
  usage: {prompt_tokens: 1, completion_tokens: 1},
}), {status: 200, headers: {"content-type": "application/json"}});

const agent = new Agent({model: new OpenAI("smoke", {apiKey: "test"}), output: z.object({answer: z.string()})});
const result = await agent.run("smoke");
if (result.output.answer !== "ok") throw new Error("unexpected Orion result");
console.log("loaded Orion native package and completed a run");
`;

}

async function writeJson(path, value) {

  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, "utf8");

}
