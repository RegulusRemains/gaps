const fs = require("fs");

const testPath = ".github/workflows/build-and-test.yaml";
const deployPath = ".github/workflows/build-and-deploy.yaml";
const testWorkflow = fs.readFileSync(testPath, "utf8");
const deployWorkflow = fs.readFileSync(deployPath, "utf8");
const combined = `${testWorkflow}\n${deployWorkflow}`;

const exactCounts = {
  "actions/checkout@v7": 2,
  "actions/setup-node@v7": 2,
  "actions/setup-java@v5": 2,
  "distribution: temurin": 2,
};

for (const [needle, expected] of Object.entries(exactCounts)) {
  const actual = combined.split(needle).length - 1;
  if (actual !== expected) {
    throw new Error(`${needle}: expected ${expected} references, found ${actual}`);
  }
}

for (const forbidden of [
  "actions/checkout@master",
  "actions/setup-node@v1",
  "actions/setup-java@v1",
]) {
  if (combined.includes(forbidden)) {
    throw new Error(`deprecated action reference remains: ${forbidden}`);
  }
}

if (!/^on:\r?\n  release$/m.test(deployWorkflow)) {
  throw new Error("release workflow trigger boundary drifted");
}
if (!/^\s{2}workflow_dispatch:\s*$/m.test(testWorkflow)) {
  throw new Error("manual non-publishing validation trigger is missing");
}
if (!deployWorkflow.includes("docker push housewrecker/gaps:latest")) {
  throw new Error("release publication step unexpectedly changed");
}
if (testWorkflow.includes("docker login") || testWorkflow.includes("docker push")) {
  throw new Error("branch validation workflow must not publish");
}

console.log("Validated Node 24 action pins and release publication boundary.");
