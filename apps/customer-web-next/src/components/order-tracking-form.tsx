'use client';

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { isUuid } from '@/lib/delivery-status';

export function OrderTrackingForm() {
  const router = useRouter();
  const [orderId, setOrderId] = useState('');
  const [error, setError] = useState('');

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = orderId.trim();
    if (!isUuid(normalized)) {
      setError('Enter a valid chef-specific order ID.');
      return;
    }
    setError('');
    router.push(`/orders/${normalized}/tracking`);
  }

  return (
    <form className="mt-8 space-y-4" onSubmit={submit} noValidate>
      <label className="block text-sm font-semibold text-slate-700" htmlFor="order-id">Chef-specific order ID</label>
      <input
        id="order-id"
        value={orderId}
        onChange={(event) => setOrderId(event.target.value)}
        placeholder="11111111-2222-4333-8444-555555555555"
        autoComplete="off"
        className="w-full rounded-2xl border border-slate-300 bg-white px-4 py-3 font-mono text-sm text-slate-950 shadow-inner"
      />
      {error ? <p className="text-sm font-medium text-red-700" role="alert">{error}</p> : null}
      <button className="w-full rounded-full bg-[#6930CA] px-5 py-3 text-sm font-semibold text-white transition hover:brightness-110" type="submit">Track delivery</button>
    </form>
  );
}
