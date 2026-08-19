import { BuildTrainWorkspace } from "@/components/build-train/build-train-workspace";

export const metadata = {
  title: "Build train request | Craves",
  description: "Production-readiness execution plan for the Craves Next.js web platform."
};

export default function BuildTrainPage() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-50">
      <BuildTrainWorkspace />
    </main>
  );
}
