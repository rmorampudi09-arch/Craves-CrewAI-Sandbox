'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { DeliveryStatusResponse } from '@/lib/delivery-status';
import { presentationFor, shouldAutoRefresh } from '@/lib/delivery-status';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; value: DeliveryStatusResponse }
  | { kind: 'error'; status: number; message: string };

type DeliveryTrackingProps = { orderId: string };

const PROGRESS_LABELS = [
  'Preparing',
  'Finding partner',
  'Partner assigned',
  'To pickup',
  'At pickup',
  'Picked up',
  'On the way',
  'Arriving',
  'Complete',
];

function formatTime(value: string | null): string {
  if (!value) return 'Not available yet';
  return new Intl.DateTimeFormat('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Kolkata',
  }).format(new Date(value));
}

async function readError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: unknown };
    return typeof body.message === 'string' ? body.message : `Request failed with HTTP ${response.status}`;
  } catch {
    return `Request failed with HTTP ${response.status}`;
  }
}

export function DeliveryTracking({ orderId }: DeliveryTrackingProps) {
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [refreshing, setRefreshing] = useState(false);
  const [lastRefreshAt, setLastRefreshAt] = useState<Date | null>(null);

  const load = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    else setState({ kind: 'loading' });

    try {
      const response = await fetch(`/api/orders/${orderId}/delivery-status`, {
        method: 'GET',
        headers: { Accept: 'application/json' },
        cache: 'no-store',
      });
      if (!response.ok) {
        setState({ kind: 'error', status: response.status, message: await readError(response) });
        return;
      }
      const value = (await response.json()) as DeliveryStatusResponse;
      setState({ kind: 'ready', value });
      setLastRefreshAt(new Date());
    } catch {
      setState({ kind: 'error', status: 0, message: 'Check your internet connection and try again.' });
    } finally {
      setRefreshing(false);
    }
  }, [orderId]);

  useEffect(() => { void load(false); }, [load]);

  useEffect(() => {
    if (state.kind !== 'ready' || !shouldAutoRefresh(state.value.status)) return;
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') void load(true);
    }, 30_000);
    return () => window.clearInterval(interval);
  }, [load, state]);

  const presentation = useMemo(
    () => state.kind === 'ready' ? presentationFor(state.value.status) : null,
    [state],
  );

  if (state.kind === 'loading') return <TrackingSkeleton />;

  if (state.kind === 'error') {
    return (
      <section className="rounded-[28px] border border-red-200/20 bg-white/95 p-6 shadow-2xl shadow-black/20" aria-live="polite">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-red-700">Tracking unavailable</p>
        <h1 className="mt-2 text-2xl font-bold text-slate-950">We could not load this delivery.</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{state.message}</p>
        {state.status === 401 ? (
          <a className="mt-6 inline-flex rounded-full bg-[#6930CA] px-5 py-3 text-sm font-semibold text-white" href="/sign-in">Sign in again</a>
        ) : (
          <button className="mt-6 rounded-full bg-[#6930CA] px-5 py-3 text-sm font-semibold text-white" type="button" onClick={() => void load(false)}>Try again</button>
        )}
      </section>
    );
  }

  const { value } = state;
  const current = presentation ?? presentationFor(value.status);

  return (
    <div className="space-y-5" aria-live="polite">
      <section className="overflow-hidden rounded-[30px] border border-white/10 bg-[#FFF8EC] shadow-2xl shadow-black/30">
        <div className="bg-[#0B1426] px-6 py-7 text-white sm:px-8">
          <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[#F6B545]">Chef-specific order</p>
              <h1 className="mt-2 text-3xl font-semibold tracking-tight">{current.label}</h1>
              <p className="mt-3 max-w-xl text-sm leading-6 text-slate-300">{current.description}</p>
            </div>
            <StatusBadge attention={current.attention} terminal={current.terminal} label={value.status ?? 'WAITING'} />
          </div>
        </div>

        <div className="space-y-7 px-6 py-7 sm:px-8">
          <Progress stage={current.stage} attention={current.attention} />
          <dl className="grid gap-4 sm:grid-cols-2">
            <Info label="Order ID" value={value.orderId} mono />
            <Info label="Last provider update" value={formatTime(value.observedAt)} />
            <Info label="Delivery partner" value={value.providerId ? 'Assigned through Craves delivery network' : 'Not assigned yet'} />
            <Info label="Refresh" value={lastRefreshAt ? `Checked ${formatTime(lastRefreshAt.toISOString())}` : 'Checking now'} />
          </dl>
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => void load(true)}
              disabled={refreshing}
              className="rounded-full bg-[#6930CA] px-5 py-3 text-sm font-semibold text-white transition hover:brightness-110 disabled:cursor-not-allowed disabled:bg-[#EAEAEF] disabled:text-[#A6A7B0]"
            >
              {refreshing ? 'Refreshing…' : 'Refresh status'}
            </button>
            {value.trackingUrl ? (
              <a
                className="rounded-full border border-[#6930CA] px-5 py-3 text-sm font-semibold text-[#6930CA] transition hover:bg-purple-50"
                href={value.trackingUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                Open partner tracking
              </a>
            ) : null}
          </div>
        </div>
      </section>
      <History history={value.history} />
    </div>
  );
}

function TrackingSkeleton() {
  return (
    <section className="animate-pulse rounded-[30px] bg-[#FFF8EC] p-7 shadow-2xl shadow-black/20" aria-label="Loading delivery tracking">
      <div className="h-4 w-36 rounded bg-slate-300" />
      <div className="mt-5 h-9 w-3/4 rounded bg-slate-300" />
      <div className="mt-4 h-4 w-full rounded bg-slate-200" />
      <div className="mt-10 h-3 w-full rounded bg-slate-200" />
      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        <div className="h-24 rounded-2xl bg-slate-200" />
        <div className="h-24 rounded-2xl bg-slate-200" />
      </div>
    </section>
  );
}

function StatusBadge({ attention, terminal, label }: { attention: boolean; terminal: boolean; label: string }) {
  const classes = attention ? 'bg-amber-100 text-amber-900' : terminal ? 'bg-emerald-100 text-emerald-900' : 'bg-white/10 text-white';
  return <span className={`inline-flex w-fit rounded-full px-4 py-2 text-xs font-bold tracking-wide ${classes}`}>{label.replaceAll('_', ' ')}</span>;
}

function Progress({ stage, attention }: { stage: number; attention: boolean }) {
  return (
    <div>
      <div className="flex justify-between gap-1" aria-hidden="true">
        {PROGRESS_LABELS.map((label, index) => (
          <span key={label} className={`h-2 flex-1 rounded-full ${index <= stage ? (attention ? 'bg-[#F6B545]' : 'bg-[#6930CA]') : 'bg-slate-200'}`} />
        ))}
      </div>
      <p className="mt-3 text-xs font-medium text-slate-500">Stage {Math.min(stage + 1, PROGRESS_LABELS.length)} of {PROGRESS_LABELS.length}: {PROGRESS_LABELS[Math.min(stage, PROGRESS_LABELS.length - 1)]}</p>
    </div>
  );
}

function Info({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <dt className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">{label}</dt>
      <dd className={`mt-2 break-words text-sm font-semibold text-slate-900 ${mono ? 'font-mono' : ''}`}>{value}</dd>
    </div>
  );
}

function History({ history }: { history: DeliveryStatusResponse['history'] }) {
  return (
    <section className="rounded-[28px] border border-white/10 bg-white/95 p-6 shadow-xl shadow-black/10 sm:p-8">
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#6930CA]">Timeline</p>
          <h2 className="mt-2 text-2xl font-bold text-slate-950">Delivery updates</h2>
        </div>
        <span className="text-xs text-slate-500">Newest status is shown above</span>
      </div>
      {history.length === 0 ? (
        <p className="mt-6 rounded-2xl bg-slate-50 p-5 text-sm leading-6 text-slate-600">No delivery events have been recorded yet. This page refreshes automatically while the order is active.</p>
      ) : (
        <ol className="mt-7 space-y-5">
          {history.map((item, index) => {
            const copy = presentationFor(item.newStatus);
            return (
              <li key={`${item.recordedAt}-${item.newStatus}-${index}`} className="relative border-l-2 border-slate-200 pl-6">
                <span className="absolute -left-[7px] top-1 h-3 w-3 rounded-full bg-[#6930CA] ring-4 ring-purple-100" aria-hidden="true" />
                <p className="text-sm font-bold text-slate-950">{copy.label}</p>
                <p className="mt-1 text-sm text-slate-600">{copy.description}</p>
                <time className="mt-2 block text-xs font-medium text-slate-500" dateTime={item.observedAt}>{formatTime(item.observedAt)}</time>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
