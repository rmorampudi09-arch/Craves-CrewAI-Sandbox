import { describe, expect, it } from 'vitest';

import { buildTrainConfig } from '@/lib/build-train-config';

describe('build train web alignment', () => {
  it('keeps web inside the active domain set', () => {
    expect(buildTrainConfig.activeDomains).toContain('web');
  });

  it('points to the canonical Next.js production module', () => {
    expect(buildTrainConfig.canonicalWebModule).toBe('apps/customer-web-next');
  });

  it('guards against extending a Node.js backend runtime', () => {
    expect(buildTrainConfig.forbiddenRuntimeDirection.toLowerCase()).toContain('node.js backend');
  });

  it('preserves the expected early milestone order', () => {
    expect(buildTrainConfig.sequence[0]?.title).toMatch(/Architecture lock/i);
    expect(buildTrainConfig.sequence[1]?.title).toMatch(/Infra deployability/i);
    expect(buildTrainConfig.sequence[2]?.title).toMatch(/RBAC/i);
    expect(buildTrainConfig.sequence[3]?.title).toMatch(/customer-web-next/i);
  });

  it('keeps the build train route bound to the shared work branch', () => {
    expect(buildTrainConfig.branchName).toBe('crewai/full-build-train-request');
  });
});
