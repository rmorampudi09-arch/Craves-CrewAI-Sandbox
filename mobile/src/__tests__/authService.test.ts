import { exchangeFirebaseIdToken } from '../services/authService';

jest.mock('../services/apiClient', () => ({
  apiRequest: jest.fn(async () => ({
    accessToken: 'craves-access-token',
    refreshToken: 'craves-refresh-token',
    roles: ['CUSTOMER'],
  })),
}));

describe('authService', () => {
  it('exchanges a Firebase token for Craves backend tokens', async () => {
    const result = await exchangeFirebaseIdToken('firebase-id-token');

    expect(result.accessToken).toBe('craves-access-token');
    expect(result.refreshToken).toBe('craves-refresh-token');
    expect(result.roles).toEqual(['CUSTOMER']);
  });
});
