import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { LoginRequest, LoginResponse, MessageResponse, Role, UserProfile } from '../models/auth.models';
import { ToastService } from './toast.service';

const STORAGE_KEY = 'soprahr.session';
const MAX_TIMEOUT_MS = 2_147_483_647;

interface Session {
  token: string;
  expiresAt: string;
  user: UserProfile;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly session = signal<Session | null>(this.restore());
  private expiryTimer?: ReturnType<typeof setTimeout>;

  readonly user = computed(() => this.session()?.user ?? null);

  constructor() {
    const current = this.session();
    if (current) {
      this.scheduleExpiry(current);
    }
  }

  /** Jeton à joindre aux requêtes, ou null si absent / expiré. */
  get token(): string | null {
    const s = this.session();
    return s && !this.isExpired(s) ? s.token : null;
  }

  isAuthenticated(): boolean {
    return this.token !== null;
  }

  hasAnyRole(roles: Role[]): boolean {
    const user = this.user();
    return this.isAuthenticated() && !!user && roles.includes(user.role);
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, credentials)
      .pipe(tap((res) => this.start({ token: res.token, expiresAt: res.expiresAt, user: res.user })));
  }

  /** Nettoie la session locale (le JWT est sans état côté serveur : il expire de lui-même). */
  logout(redirectToLogin = true, returnUrl?: string): void {
    clearTimeout(this.expiryTimer);
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // stockage indisponible : rien à nettoyer
    }
    if (redirectToLogin) {
      void this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : undefined);
    }
  }

  forgotPassword(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${environment.apiUrl}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${environment.apiUrl}/auth/reset-password`, {
      token,
      newPassword
    });
  }

  private start(session: Session): void {
    this.session.set(session);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // la session reste valable en mémoire
    }
    this.scheduleExpiry(session);
  }

  private scheduleExpiry(session: Session): void {
    clearTimeout(this.expiryTimer);
    const delay = Math.min(new Date(session.expiresAt).getTime() - Date.now(), MAX_TIMEOUT_MS);
    this.expiryTimer = setTimeout(() => {
      this.logout(true, this.router.url);
      this.toast.warning('Session expirée', 'Reconnectez-vous pour continuer.');
    }, Math.max(delay, 0));
  }

  private isExpired(session: Session): boolean {
    return new Date(session.expiresAt).getTime() <= Date.now();
  }

  private restore(): Session | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as Session;
      if (!session.token || !session.expiresAt || this.isExpired(session)) {
        sessionStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      return null;
    }
  }
}
