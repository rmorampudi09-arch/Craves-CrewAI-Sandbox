import {mobileEnvironment} from '../../config/env';

export type ApiHealthResponse = {
  ok: boolean;
  baseUrl: string;
};

export const getApiHealth = async (): Promise<ApiHealthResponse> => {
  return {
    ok: true,
    baseUrl: mobileEnvironment.apiBaseUrl,
  };
};
