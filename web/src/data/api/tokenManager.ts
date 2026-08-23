/**
 * In-memory OAuth2 token manager.
 *
 * Stores access and refresh tokens in memory (not localStorage) for security.
 * Provides automatic token refresh when the access token is expired.
 * Calls POST /v1/auth/refresh to obtain new tokens.
 *
 * Validates: Requirements 10.3
 */

import { API_BASE_URL } from './apiClient';

// ─── Types ───────────────────────────────────────────────────────────────────

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  /** Expiry time in milliseconds since epoch. */
  expiresAt: number;
}

export interface TokenRefreshResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number; // seconds
  token_type: string;
}

// ─── TokenManager ────────────────────────────────────────────────────────────

/**
 * Manages OAuth2 access/refresh tokens in memory.
 *
 * Usage:
 *   const tokenManager = new TokenManager();
 *   tokenManager.setTokens({ accessToken, refreshToken, expiresAt });
 *   const token = await tokenManager.getAccessToken(); // auto-refreshes if expired
 */
export class TokenManager {
  private tokens: TokenPair | null = null;
  private refreshPromise: Promise<string> | null = null;

  /** Buffer in ms before actual expiry to trigger refresh early (30 seconds). */
  private readonly expiryBufferMs = 30_000;

  /**
   * Store a new token pair (e.g., after login or token exchange).
   */
  setTokens(tokens: TokenPair): void {
    this.tokens = tokens;
  }

  /**
   * Store tokens from a raw refresh/login response.
   */
  setTokensFromResponse(response: TokenRefreshResponse): void {
    this.tokens = {
      accessToken: response.access_token,
      refreshToken: response.refresh_token,
      expiresAt: Date.now() + response.expires_in * 1000,
    };
  }

  /**
   * Clear stored tokens (e.g., on logout).
   */
  clearTokens(): void {
    this.tokens = null;
    this.refreshPromise = null;
  }

  /**
   * Returns true if the user has tokens stored (may be expired).
   */
  hasTokens(): boolean {
    return this.tokens !== null;
  }

  /**
   * Get a valid access token.
   * If the current token is expired (or about to expire), refreshes it first.
   * Multiple concurrent callers will share the same refresh request.
   *
   * @throws Error if no tokens are stored or refresh fails.
   */
  async getAccessToken(): Promise<string> {
    if (!this.tokens) {
      throw new Error('No tokens available. User must authenticate first.');
    }

    if (!this.isTokenExpired()) {
      return this.tokens.accessToken;
    }

    // Deduplicate concurrent refresh calls
    if (!this.refreshPromise) {
      this.refreshPromise = this.refreshAccessToken();
    }

    try {
      const token = await this.refreshPromise;
      return token;
    } finally {
      this.refreshPromise = null;
    }
  }

  // ─── Private ─────────────────────────────────────────────────────────────

  private isTokenExpired(): boolean {
    if (!this.tokens) return true;
    return Date.now() >= this.tokens.expiresAt - this.expiryBufferMs;
  }

  /**
   * Refresh the access token by calling POST /v1/auth/refresh.
   */
  private async refreshAccessToken(): Promise<string> {
    if (!this.tokens?.refreshToken) {
      throw new Error('No refresh token available.');
    }

    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refresh_token: this.tokens.refreshToken }),
    });

    if (!response.ok) {
      // Refresh failed — clear tokens so the user is prompted to re-authenticate
      this.clearTokens();
      throw new Error(`Token refresh failed: ${response.status} ${response.statusText}`);
    }

    const data = (await response.json()) as TokenRefreshResponse;
    this.setTokensFromResponse(data);

    return data.access_token;
  }
}

// ─── Singleton Instance ──────────────────────────────────────────────────────

/** Default singleton token manager instance for the application. */
export const tokenManager = new TokenManager();
