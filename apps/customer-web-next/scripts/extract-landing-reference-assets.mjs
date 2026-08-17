import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const appRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const targetDirectory = resolve(appRoot, "public/landing/reference");

const assets = [
  {
    name: "hero-reference.png",
    bytes: 3864660,
    sha256: "51d8f9f7e8fa852fcf0db35f1b52a6b8303e0ea869fb890855cb35156fa68655",
  },
  {
    name: "how-craves-works-reference.png",
    bytes: 2890033,
    sha256: "3e149fda7da24782a129bacdad7d652aae07358e90fd15182ccdf9730aff4796",
  },
  {
    name: "why-craves-reference.png",
    bytes: 2692511,
    sha256: "94592a5ac7a5ed5d4466e8ab6104bae0a062f6fb870eafb786ee36692d56f400",
  },
  {
    name: "home-chefs-app-reference.png",
    bytes: 3461478,
    sha256: "b3331ae6d53b85ba5face0383b82e25420527e208fcedee36c04a12eb19aa9bd",
  },
];

for (const asset of assets) {
  const path = resolve(targetDirectory, asset.name);
  let image;

  try {
    image = await readFile(path);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(
      `Approved landing reference ${asset.name} is missing from the repository: ${detail}`,
    );
  }

  if (image.length !== asset.bytes) {
    throw new Error(
      `Landing reference ${asset.name} has unexpected byte length ${image.length}; expected ${asset.bytes}`,
    );
  }

  const signature = image.subarray(0, 8).toString("hex");
  if (signature !== "89504e470d0a1a0a") {
    throw new Error(`Landing reference ${asset.name} is not the approved PNG`);
  }

  const actualSha256 = createHash("sha256").update(image).digest("hex");
  if (actualSha256 !== asset.sha256) {
    throw new Error(
      `Landing reference ${asset.name} has unexpected hash ${actualSha256}`,
    );
  }

  console.log(
    `Verified approved landing reference: ${asset.name}, ${image.length} bytes, ${actualSha256}`,
  );
}
