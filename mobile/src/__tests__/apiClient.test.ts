import { apiRequest, ApiError } from '../services/apiClient';

describe('apiClient', () => {
  it('returns parsed JSON on success', async () => {
    global.fetch = jest.fn(async () => ({
      ok: true,
      text: async () => JSON.stringify({ ok: true }),
    })) as unknown as typeof fetch;

    const result = await apiRequest<{ ok: boolean }>('/health');
    expect(result.ok).toBe(true);
  });

  it('throws ApiError on failure', async () => {
    global.fetch = jest.fn(async () => ({
      ok: false,
      status: 401,
      text: async () => JSON.stringify({ code: 'UNAUTHORIZED' }),
    })) as unknown as typeof fetch;

    await expect(apiRequest('/secure')).rejects.toBeInstanceOf(ApiError);
  });
});
