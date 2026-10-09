const crypto = require('node:crypto');
const key = process.env.INTERNAL_API_KEY;
if (!key || key.length < 32)
  throw new Error('INTERNAL_API_KEY must contain at least 32 characters');
function internal(req, res, next) {
  const value = req.get('X-Service-Key') || '';
  const a = Buffer.from(value);
  const b = Buffer.from(key);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b))
    return res.status(401).json({ title: 'Unauthorized', status: 401 });
  next();
}
let verifier;
async function authenticated(req, res, next) {
  try {
    if (!verifier) {
      const { createRemoteJWKSet, jwtVerify } = await import('jose');
      verifier = { jwtVerify, jwks: createRemoteJWKSet(new URL(process.env.JWT_JWKS)) };
    }
    const header = req.get('Authorization') || '';
    if (!header.startsWith('Bearer ')) throw new Error('Missing bearer token');
    const { payload } = await verifier.jwtVerify(header.slice(7), verifier.jwks, {
      issuer: process.env.JWT_ISSUER,
      audience: 'ecommerce-api',
    });
    if (!payload.sub || !/^[0-9a-f-]{36}$/i.test(payload.sub)) throw new Error('Invalid subject');
    req.customerId = payload.sub;
    req.roles = payload.realm_access?.roles || [];
    next();
  } catch {
    res.status(401).json({ title: 'Unauthorized', status: 401 });
  }
}
module.exports = { internal, authenticated };
