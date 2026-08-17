import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { DeliveryTracking } from '@/components/delivery-tracking';
import { isUuid } from '@/lib/delivery-status';

export const metadata: Metadata = {
  title: 'Delivery tracking',
  robots: { index: false, follow: false },
};

export default async function DeliveryTrackingPage({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  if (!isUuid(orderId)) notFound();

  return (
    <main className="mx-auto min-h-screen max-w-4xl px-5 py-8 sm:px-8 sm:py-12">
      <header className="mb-7 flex items-center justify-between gap-4">
        <Link href="/" className="text-xl font-semibold text-white" aria-label="Craves home">Craves</Link>
        <p className="text-sm text-slate-400">food from home</p>
      </header>
      <DeliveryTracking orderId={orderId} />
    </main>
  );
}
