import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../models/auth.models';
import { AuthService } from '../services/auth.service';

/** Exige une session valide ; sinon redirige vers /login en mémorisant la page demandée. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Exige l'un des rôles listés dans `data: { roles: [...] }` de la route. */
export const roleGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  const roles = (route.data['roles'] as Role[] | undefined) ?? [];
  return auth.hasAnyRole(roles) ? true : router.createUrlTree(['/forbidden']);
};

/** Réservé aux visiteurs : un utilisateur déjà connecté est renvoyé vers l'accueil. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/dashboard']) : true;
};
