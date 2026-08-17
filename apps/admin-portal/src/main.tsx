import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ChefHat, Users, Clock, IndianRupee, CheckCircle } from 'lucide-react';
import './styles.css';
const API = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
type Chef={id:string;kitchenName:string;cuisine:string;rating:number;status:string};
function App(){ const [summary,setSummary]=useState<any>({}); const [chefs,setChefs]=useState<Chef[]>([]); const load=()=>{fetch(`${API}/admin/summary`).then(r=>r.json()).then(setSummary); fetch(`${API}/admin/chefs`).then(r=>r.json()).then(setChefs)}; useEffect(load,[]);
 const approve=(id:string)=>fetch(`${API}/admin/chefs/${id}/approve`,{method:'POST'}).then(load);
 return <main className="admin"><aside><h1>Craves Admin</h1><nav><b>Dashboard</b><span>Chef Approvals</span><span>Orders</span><span>Users</span><span>Settings</span></nav></aside><section className="content"><header><h2>Operations Dashboard</h2><p>Manage chefs, menus, orders and platform health.</p></header><div className="stats"><Card icon={<Users/>} label="Users" value={summary.users}/><Card icon={<ChefHat/>} label="Chefs" value={summary.chefs}/><Card icon={<Clock/>} label="Pending Chefs" value={summary.pendingChefs}/><Card icon={<IndianRupee/>} label="Revenue Today" value={`₹${summary.revenueToday??0}`}/></div><h3>Chef Approval Queue</h3><div className="table">{chefs.map(c=><div className="tr" key={c.id}><div><b>{c.kitchenName}</b><p>{c.cuisine} • ⭐ {c.rating}</p></div><span className={c.status.toLowerCase()}>{c.status}</span>{c.status!=='APPROVED'?<button onClick={()=>approve(c.id)}><CheckCircle size={16}/> Approve</button>:<button disabled>Approved</button>}</div>)}</div></section></main>}
function Card({icon,label,value}:any){return <div className="card">{icon}<p>{label}</p><b>{value??0}</b></div>}
createRoot(document.getElementById('root')!).render(<App/>);
