"use client";

import {
  Category, ChartComponent, ColumnSeries, DataLabel, Inject,
  SeriesCollectionDirective, SeriesDirective, Tooltip
} from "@syncfusion/ej2-react-charts";
import { ColumnDirective, ColumnsDirective, GridComponent, Page, Sort } from "@syncfusion/ej2-react-grids";
import type { AdminDashboardSummary } from "@/lib/admin-dashboard-contract";

const labels: Record<string, string> = {
  CHEF_ACCEPTANCE_PENDING: "Awaiting chef", PREPARING: "Preparing", READY_FOR_PICKUP: "Ready",
  OUT_FOR_DELIVERY: "Out for delivery", REFUND_PENDING: "Refund pending", REFUND_FAILED: "Refund failed"
};

function readableStatus(value: string): string {
  return labels[value] ?? value.toLowerCase().replaceAll("_", " ").replace(/^./, character => character.toUpperCase());
}

export function AdminDashboardVisuals({ summary }: { summary: AdminDashboardSummary }) {
  const trend = summary.orderTrend.map(point => ({
    day: new Date(`${point.date}T00:00:00Z`).toLocaleDateString("en-IN", { weekday: "short", day: "numeric", timeZone: "UTC" }),
    orders: point.count
  }));
  const exceptions = summary.recentExceptions.map(item => ({
    ...item,
    shortOrderId: item.orderId.slice(0, 8).toUpperCase(),
    kitchen: item.kitchenName || "Kitchen name unavailable",
    displayStatus: readableStatus(item.status),
    updated: new Date(item.updatedAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" })
  }));

  return <div className="grid gap-6 xl:grid-cols-[1.35fr_1fr]">
    <section className="overflow-hidden rounded-[28px] border border-[#ebe5ef] bg-white p-5 shadow-[0_20px_60px_-45px_rgba(61,43,79,0.45)] sm:p-7">
      <div className="mb-5"><p className="text-xs font-bold uppercase tracking-[0.16em] text-[#8b7b97]">Order volume</p><h2 className="mt-1 text-xl font-bold">Last seven days</h2></div>
      <ChartComponent
        height="310px"
        background="transparent"
        chartArea={{ border: { width: 0 } }}
        primaryXAxis={{ valueType: "Category", majorGridLines: { width: 0 }, majorTickLines: { width: 0 }, labelStyle: { color: "#766981", fontFamily: "Poppins" } }}
        primaryYAxis={{ minimum: 0, majorTickLines: { width: 0 }, lineStyle: { width: 0 }, majorGridLines: { color: "#eee9f1", dashArray: "4 4" }, labelStyle: { color: "#766981", fontFamily: "Poppins" } }}
        tooltip={{ enable: true, format: "${point.x}: ${point.y} orders" }}
      >
        <Inject services={[ColumnSeries, Category, Tooltip, DataLabel]} />
        <SeriesCollectionDirective>
          <SeriesDirective dataSource={trend} xName="day" yName="orders" type="Column" fill="#6930ca" cornerRadius={{ topLeft: 8, topRight: 8 }} marker={{ dataLabel: { visible: true, position: "Top", font: { color: "#5b496a", fontWeight: "600" } } }} />
        </SeriesCollectionDirective>
      </ChartComponent>
    </section>
    <section className="overflow-hidden rounded-[28px] border border-[#ebe5ef] bg-white p-5 shadow-[0_20px_60px_-45px_rgba(61,43,79,0.45)] sm:p-7">
      <div className="mb-5"><p className="text-xs font-bold uppercase tracking-[0.16em] text-[#8b7b97]">Attention queue</p><h2 className="mt-1 text-xl font-bold">Recent exceptions</h2></div>
      {exceptions.length === 0
        ? <div className="grid min-h-[300px] place-items-center rounded-2xl bg-[#f7f5fb] text-sm font-semibold text-[#766981]">No current exceptions</div>
        : <GridComponent dataSource={exceptions} height="300" allowSorting allowPaging pageSettings={{ pageSize: 5, pageSizes: false }} gridLines="Horizontal">
          <ColumnsDirective>
            <ColumnDirective field="shortOrderId" headerText="Order" width="95" />
            <ColumnDirective field="kitchen" headerText="Kitchen" width="150" />
            <ColumnDirective field="displayStatus" headerText="Status" width="125" />
            <ColumnDirective field="updated" headerText="Updated" width="165" />
          </ColumnsDirective>
          <Inject services={[Page, Sort]} />
        </GridComponent>}
    </section>
  </div>;
}
