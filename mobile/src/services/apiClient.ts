import { mobileEnv } from '../config/env';

export type ApiRequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  token?: string;
  body?: unknown;
  headers?: Record<string, string>;
};

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly payload?: unknown,
  ) {
    super(message);
  }
}

export const apiRequest = async <T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> => {
  const response = await fetch(`${mobileEnv.apiBaseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      ...(options.headers ?? {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  const text = await response.text();
  const payload = text ? safeJsonParse(text) : null;

  if (!response.ok) {
    throw new ApiError(`API request failed for ${path}`, response.status, payload);
  }

  return payload as T;
};

const safeJsonParse = (value: string) => {
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
};
