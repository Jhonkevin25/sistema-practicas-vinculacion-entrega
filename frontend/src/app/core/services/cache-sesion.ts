export function claveCacheSesion(): string {
  if (typeof localStorage === 'undefined') return 'SIN_SESION';
  return localStorage.getItem('token') || 'SIN_SESION';
}
