/**
 * Fetch-based API client with OAuth2 bearer token management.
 *
 * Wraps the native fetch API to:
 * - Add Bearer token header to all requests
 * - Handle 401 by refreshing the token and retrying once
 * - Support typed GET, POST, PUT, DELETE methods
 *
 * Validates: Requirements 10.3
 */

import { tokenManager } from './tokenManager';

// ─── Configuration ───────────────────────────────────────────────────────────

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

// ─── Types ───────────────────────────────────────────────────────────────────

export interface ApiErrorResponse {
  code: string;
  message: string;
  details?: unknown;
  correlationId?: string;
  timestamp?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly response: ApiErrorResponse | null;

  constructor(status: number, response: ApiErrorResponse | null, message?: string) {
    super(message ?? response?.message ?? `API request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.response = response;
  }
}

export interface RequestOptions {
  /** Additional headers to merge with defaults. */
  headers?: Record<string, string>;
  /** AbortSignal for cancellation. */
  signal?: AbortSignal;
  /** Skip authentication header (e.g., for public endpoints). */
  skipAuth?: boolean;
}

// ─── ApiClient ───────────────────────────────────────────────────────────────

/**
 * Authenticated HTTP client for the DopaShift REST API.
 *
 * Usage:
 *   const response = await apiClient.get<Goal[]>('/goals');
 *   const created = await apiClient.post<Goal>('/goals', { name: 'Learn Rust' });
 */
class ApiClient {
  private readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  /**
   * Perform a GET request.
   */
  async get<T>(path: string, options?: RequestOptions): Promise<T> {
    return this.request<T>('GET', path, undefined, options);
  }

  /**
   * Perform a POST request with a JSON body.
   */
  async post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>('POST', path, body, options);
  }

  /**
   * Perform a PUT request with a JSON body.
   */
  async put<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>('PUT', path, body, options);
  }

  /**
   * Perform a DELETE request.
   */
  async delete<T = void>(path: string, options?: RequestOptions): Promise<T> {
    return this.request<T>('DELETE', path, undefined, options);
  }

  // ─── Core Request Logic ──────────────────────────────────────────────────

  /**
   * Execute a fetch request with auth headers and 401 retry logic.
   *
   * On receiving a 401:
   * 1. Refreshes the access token via TokenManager
   * 2. Retries the original request once with the new token
   * 3. If the retry also fails, throws an ApiError
   */
  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
    options?: RequestOptions,
  ): Promise<T> {
    const response = await this.executeRequest(method, path, body, options);

    // If 401 and we have tokens, try refreshing and retry once
    if (response.status === 401 && !options?.skipAuth && tokenManager.hasTokens()) {
      try {
        // Force a fresh token by refreshing
        await tokenManager.getAccessToken();
      } catch {
        // Refresh failed, propagate the original 401
        throw await this.buildApiError(response);
      }

      // Retry with the new token
      const retryResponse = await this.executeRequest(method, path, body, options);

      if (!retryResponse.ok) {
        throw await this.buildApiError(retryResponse);
      }

      return this.parseResponse<T>(retryResponse);
    }

    if (!response.ok) {
      throw await this.buildApiError(response);
    }

    return this.parseResponse<T>(response);
  }

  /**
   * Execute a single fetch call with appropriate headers.
   */
  private async executeRequest(
    method: string,
    path: string,
    body?: unknown,
    options?: RequestOptions,
  ): Promise<Response> {
    const headers: Record<string, string> = {
      ...options?.headers,
    };

    // Add auth header unless explicitly skipped
    if (!options?.skipAuth && tokenManager.hasTokens()) {
      const token = await tokenManager.getAccessToken();
      headers['Authorization'] = `Bearer ${token}`;
    }

    // Add content-type for requests with a body
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    const url = `${this.baseUrl}${path}`;

    return fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: options?.signal,
    });
  }

  // ─── Response Handling ─────────────────────────────────────────────────────

  private async parseResponse<T>(response: Response): Promise<T> {
    // Handle 204 No Content
    if (response.status === 204) {
      return undefined as T;
    }

    const contentType = response.headers.get('content-type');
    if (contentType?.includes('application/json')) {
      return (await response.json()) as T;
    }

    // Non-JSON responses returned as text cast to T
    return (await response.text()) as unknown as T;
  }

  private async buildApiError(response: Response): Promise<ApiError> {
    let errorBody: ApiErrorResponse | null = null;

    try {
      const contentType = response.headers.get('content-type');
      if (contentType?.includes('application/json')) {
        errorBody = (await response.json()) as ApiErrorResponse;
      }
    } catch {
      // Failed to parse error body — leave as null
    }

    return new ApiError(response.status, errorBody);
  }
}

// ─── Singleton Instance ──────────────────────────────────────────────────────

/** Default API client instance for the application. */
export const apiClient = new ApiClient(API_BASE_URL);
