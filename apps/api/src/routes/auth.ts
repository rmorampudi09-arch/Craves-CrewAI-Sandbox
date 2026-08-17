import { FastifyInstance } from 'fastify';
import jwt from 'jsonwebtoken';
import { z } from 'zod';
import { config } from '../config.js';
import { users } from '../infrastructure/mockStore.js';

const startSchema = z.object({ phone: z.string().min(8), mode: z.enum(['CUSTOMER', 'CHEF']).default('CUSTOMER') });
const verifySchema = z.object({ phone: z.string().min(8), otp: z.string().length(6), mode: z.enum(['CUSTOMER', 'CHEF']).default('CUSTOMER') });

export async function authRoutes(app: FastifyInstance) {
  app.post('/api/v1/auth/otp/start', async (req, reply) => {
    const body = startSchema.parse(req.body);
    // TODO: Replace mock OTP with Firebase Phone Auth verification/token exchange.
    return reply.send({ success: true, phone: body.phone, message: 'OTP initiated. Use 123456 in local dev.' });
  });

  app.post('/api/v1/auth/otp/verify', async (req, reply) => {
    const body = verifySchema.parse(req.body);
    if (body.otp !== '123456') return reply.code(401).send({ error: 'Invalid OTP' });
    let user = users.find(u => u.phone === body.phone);
    if (!user) {
      user = { id: `u_${Date.now()}`, phone: body.phone, name: body.mode === 'CHEF' ? 'New Chef' : 'New Customer', role: body.mode };
      users.push(user);
    }
    const token = jwt.sign({ sub: user.id, role: user.role, phone: user.phone }, config.jwtSecret, { expiresIn: '7d' });
    return { token, user };
  });
}
