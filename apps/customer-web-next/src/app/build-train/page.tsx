import { BuildTrainWorkspace } from "@/components/build-train/build-train-workspace";

export const metadata = {
  title: "Build train request | Craves",
  description:
    "Production-readiness execution board for the canonical Craves Next.js web surface.",
};

export default function BuildTrainPage() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-50">
      <BuildTrainWorkspace />
    </main>
  );
}
