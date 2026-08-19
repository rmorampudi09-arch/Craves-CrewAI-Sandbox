describe('mobileEnvironment defaults', () => {
  const originalEnv = process.env;

  afterEach(() => {
    process.env = originalEnv;
    jest.resetModules();
  });

  it('falls back to safe defaults when env is missing', async () => {
    process.env = {} as NodeJS.ProcessEnv;
    const {mobileEnvironment} = await import('../src/config/env');

    expect(mobileEnvironment.apiBaseUrl).toBe('http://localhost:8080');
    expect(mobileEnvironment.environmentName).toBe('local');
    expect(mobileEnvironment.firebaseProjectId).toBeUndefined();
  });
});
