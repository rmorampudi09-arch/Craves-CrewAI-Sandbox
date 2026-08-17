import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ChefHat, Heart, Home, Search, ShoppingCart, User, Bell, Star, CreditCard, ShieldCheck } from 'lucide-react';
import './styles.css';
import './payment-test.css';

const API = (import.meta.env.VITE_API_BASE_URL ?? 'https://api.craves.in/api/v1').replace(/\/$/, '');

type Chef = { id:string; kitchenName:string; cuisine:string; rating:number; status:string };
type MenuItem = { id:string; name:string; description:string; price:number; category:string; isVeg:boolean; imageUrl:string };
type PaymentResponse = {
 paymentOrderId:string;
 checkoutId:string;
 cravesPaymentOrderRef:string;
 cashfreeOrderId:string;
 cfOrderId:string;
 paymentSessionId:string;
 amount:number;
 currency:string;
 status:string;
 providerStatus?:string;
};

declare global {
 interface Window {
  Cashfree?: (options: { mode: 'sandbox' | 'production' }) => {
   checkout: (options: { paymentSessionId: string; redirectTarget?: '_modal' | '_self' | '_blank' }) => Promise<unknown>;
  };
 }
}

function loadCashfreeSdk(): Promise<void> {
 return new Promise((resolve, reject) => {
  if (window.Cashfree) {
   resolve();
   return;
  }
  const existing = document.querySelector<HTMLScriptElement>('script[data-cashfree-sdk="true"]');
  if (existing) {
   existing.addEventListener('load', () => resolve());
   existing.addEventListener('error', () => reject(new Error('Cashfree SDK failed to load')));
   return;
  }
  const script = document.createElement('script');
  script.src = 'https://sdk.cashfree.com/js/v3/cashfree.js';
  script.async = true;
  script.dataset.cashfreeSdk = 'true';
  script.onload = () => resolve();
  script.onerror = () => reject(new Error('Cashfree SDK failed to load'));
  document.head.appendChild(script);
 });
}

function PaymentTestPage() {
 const [apiBase, setApiBase] = useState(API);
 const [accessToken, setAccessToken] = useState('');
 const [checkoutId, setCheckoutId] = useState('');
 const [customerName, setCustomerName] = useState('Ravi Teja');
 const [customerEmail, setCustomerEmail] = useState('sandbox@craves.in');
 const [customerPhone, setCustomerPhone] = useState('8019166645');
 const [payment, setPayment] = useState<PaymentResponse | null>(null);
 const [manualSessionId, setManualSessionId] = useState('');
 const [busy, setBusy] = useState(false);
 const [message, setMessage] = useState('Ready for Cashfree sandbox test.');
 const sessionId = useMemo(() => payment?.paymentSessionId || manualSessionId.trim(), [payment, manualSessionId]);

 async function createPaymentOrder() {
  setBusy(true);
  setMessage('Creating Cashfree sandbox payment order...');
  setPayment(null);
  try {
   const response = await fetch(`${apiBase}/payments/orders`, {
    method: 'POST',
    headers: {
     'Authorization': `Bearer ${accessToken.trim()}`,
     'Content-Type': 'application/json'
    },
    body: JSON.stringify({ checkoutId: checkoutId.trim(), customerName, customerEmail, customerPhone, returnUrl: window.location.href })
   });
   const data = await response.json();
   if (!response.ok) {
    throw new Error(data?.message || data?.error || `HTTP ${response.status}`);
   }
   setPayment(data);
   setManualSessionId(data.paymentSessionId || '');
   setMessage(`Payment order created: ${data.status} for ₹${data.amount}`);
  } catch (error) {
   setMessage(error instanceof Error ? error.message : 'Payment order creation failed');
  } finally {
   setBusy(false);
  }
 }

 async function openCheckout() {
  if (!sessionId) {
   setMessage('Payment session id is required.');
   return;
  }
  setBusy(true);
  setMessage('Opening Cashfree sandbox checkout...');
  try {
   await loadCashfreeSdk();
   if (!window.Cashfree) throw new Error('Cashfree SDK is not available');
   const cashfree = window.Cashfree({ mode: 'sandbox' });
   await cashfree.checkout({ paymentSessionId: sessionId, redirectTarget: '_modal' });
   setMessage('Cashfree checkout closed. Click Verify Payment after completing sandbox payment.');
  } catch (error) {
   setMessage(error instanceof Error ? error.message : 'Unable to open Cashfree checkout');
  } finally {
   setBusy(false);
  }
 }

 async function verifyPayment() {
  const paymentOrderId = payment?.paymentOrderId;
  if (!paymentOrderId) {
   setMessage('Create payment order first, then verify.');
   return;
  }
  setBusy(true);
  setMessage('Verifying payment with backend...');
  try {
   const response = await fetch(`${apiBase}/payments/orders/${paymentOrderId}/verify`, { method: 'POST' });
   const data = await response.json();
   if (!response.ok) throw new Error(data?.message || data?.error || `HTTP ${response.status}`);
   setPayment(prev => prev ? { ...prev, status: data.status, providerStatus: data.providerStatus } : prev);
   setMessage(`Backend verification result: ${data.status} / ${data.providerStatus ?? 'provider status not returned'}`);
  } catch (error) {
   setMessage(error instanceof Error ? error.message : 'Payment verification failed');
  } finally {
   setBusy(false);
  }
 }

 return <main className="payment-page">
  <section className="payment-card hero-card">
   <div className="payment-icon"><CreditCard size={30}/></div>
   <div>
    <p className="eyebrow">Craves sandbox utility</p>
    <h1>Cashfree Payment Test</h1>
    <p className="muted">Use this temporary page only for backend sandbox testing. Do not use production customer data here.</p>
   </div>
  </section>

  <section className="payment-card">
   <label>API base URL</label>
   <input value={apiBase} onChange={e => setApiBase(e.target.value)} />
   <label>Craves access token</label>
   <textarea value={accessToken} onChange={e => setAccessToken(e.target.value)} placeholder="Paste fresh Craves access token" rows={4}/>
   <label>Checkout ID</label>
   <input value={checkoutId} onChange={e => setCheckoutId(e.target.value)} placeholder="8d7063d7-b780-42eb-a01f-1ac377efc71e" />
   <div className="grid-3">
    <div><label>Name</label><input value={customerName} onChange={e => setCustomerName(e.target.value)} /></div>
    <div><label>Email</label><input value={customerEmail} onChange={e => setCustomerEmail(e.target.value)} /></div>
    <div><label>Phone</label><input value={customerPhone} onChange={e => setCustomerPhone(e.target.value)} /></div>
   </div>
   <button className="primary-button" onClick={createPaymentOrder} disabled={busy || !accessToken.trim() || !checkoutId.trim()}>Create Payment Order</button>
  </section>

  <section className="payment-card">
   <div className="secure-row"><ShieldCheck size={20}/><b>Cashfree sandbox checkout</b></div>
   <label>Payment session ID</label>
   <textarea value={manualSessionId} onChange={e => setManualSessionId(e.target.value)} placeholder="Auto-filled after Create Payment Order, or paste manually" rows={4}/>
   <div className="button-row">
    <button className="secondary-button" onClick={openCheckout} disabled={busy || !sessionId}>Open Cashfree Checkout</button>
    <button className="secondary-button" onClick={verifyPayment} disabled={busy || !payment?.paymentOrderId}>Verify Payment</button>
   </div>
  </section>

  {payment && <section className="payment-card result-card">
   <h2>Payment order result</h2>
   <div className="result-grid">
    <span>Status</span><b>{payment.status}</b>
    <span>Provider</span><b>{payment.providerStatus ?? 'ACTIVE'}</b>
    <span>Amount</span><b>₹{payment.amount} {payment.currency}</b>
    <span>Payment Order ID</span><code>{payment.paymentOrderId}</code>
    <span>Cashfree Order ID</span><code>{payment.cashfreeOrderId}</code>
   </div>
  </section>}

  <section className="payment-card status-card">{message}</section>
 </main>;
}

function App(){
 const route = `${window.location.pathname}${window.location.search}${window.location.hash}`;
 if (route.includes('payment-test')) return <PaymentTestPage/>;
 const [chefs,setChefs]=useState<Chef[]>([]); const [menu,setMenu]=useState<MenuItem[]>([]); const [cart,setCart]=useState<MenuItem[]>([]);
 useEffect(()=>{ fetch(`${API}/chefs`).then(r=>r.json()).then(setChefs).catch(()=>setChefs([])); fetch(`${API}/menu`).then(r=>r.json()).then(setMenu).catch(()=>setMenu([])); },[]);
 return <div className="phone-shell">
  <header className="topbar"><div><span className="pin">⌖</span> Hyderabad <b>▼</b><p>Good morning, Rohan 👋</p><h1>What would you like to eat today?</h1></div><Bell size={22}/></header>
  <section className="search"><Search size={18}/><input placeholder="Search for dishes, cuisines..."/><button>⚙</button></section>
  <section className="hero"><div><b>Homemade meals made with love</b><p>Fresh food from trusted home chefs.</p><button>Explore Now</button></div></section>
  <section><div className="row"><h2>Categories</h2><a>View all</a></div><div className="chips">{['Breakfast','Lunch','Dinner','Snacks','Desserts'].map(x=><div className="chip" key={x}>🍲<span>{x}</span></div>)}</div></section>
  <section><div className="row"><h2>Top Picks For You</h2><a>View all</a></div><div className="cards">{menu.map(m=><article className="food" key={m.id}><img src={m.imageUrl}/><button className="heart"><Heart size={16}/></button><h3>{m.name}</h3><p>{m.description}</p><b>₹{m.price}</b><button onClick={()=>setCart([...cart,m])}>Add</button></article>)}</div></section>
  <section><div className="row"><h2>All Chefs</h2><a>Near me</a></div>{chefs.map(c=><div className="chef" key={c.id}><div className="avatar"><ChefHat/></div><div><b>{c.kitchenName}</b><p>{c.cuisine}</p><span><Star size={14}/> {c.rating} • 25–30 min</span></div><Heart size={18}/></div>)}</section>
  <footer className="nav"><Home/><ChefHat/><ShoppingCart/><Heart/><User/><span className="badge">{cart.length}</span></footer>
 </div>
}
createRoot(document.getElementById('root')!).render(<App/>);
