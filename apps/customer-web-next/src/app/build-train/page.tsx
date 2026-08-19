import { BuildTrainWorkspace } from '@/components/build-train/build-train-workspace';

export const metadata = {
  title: 'Full build train request | Craves',
  description:
    'Production-readiness execution board for the canonical Craves Next.js web surface and its cross-domain dependencies.',
};

export default function BuildTrainPage() {
  return <BuildTrainWorkspace />;
}
